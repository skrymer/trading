package com.skrymer.midgaard.integration.eodhd.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.skrymer.midgaard.model.CompanyInfo
import com.skrymer.midgaard.model.Earning
import com.skrymer.midgaard.model.Fundamental
import com.skrymer.midgaard.model.RawBar
import java.math.BigDecimal
import java.time.LocalDate

// ── Common API response contract ──
//
// EODHD returns plain arrays for OHLCV and technical-indicator endpoints — no
// envelope. The wrapper types in this file exist to keep the per-endpoint
// hasError/isValid checks symmetric with the AlphaVantage DTO pattern, so
// EodhdProvider can use the same validateAndTransform helper.

interface EodhdApiResponse {
    fun hasError(): Boolean

    fun getErrorDescription(): String

    fun isValid(): Boolean
}

// ── End-of-Day OHLCV bars ──

/**
 * Wraps the array of bars returned by `GET /api/eod/{symbol}.US?fmt=json`.
 *
 * EODHD itself returns a top-level array; we wrap it so the same `hasError /
 * isValid` shape works for our error-handling helper. The mapper applies the
 * same split/dividend adjustment factor AlphaVantage's `toRawBars` does, so
 * downstream `RawBar.close` semantics are unchanged.
 */
data class EodhdEodResponse(
    val bars: List<EodhdBarDto>,
    val errorMessage: String? = null,
) : EodhdApiResponse {
    override fun hasError(): Boolean = errorMessage != null

    override fun getErrorDescription(): String = errorMessage ?: "Unknown error"

    // An empty bars list is a legitimate "no data in range" response, not a
    // malformed payload — only `hasError()` distinguishes structural failure.
    override fun isValid(): Boolean = true

    fun toRawBars(
        symbol: String,
        minDate: LocalDate = LocalDate.of(2000, 1, 1),
    ): List<RawBar> =
        bars
            .mapNotNull { it.toRawBar(symbol, minDate) }
            .sortedBy { it.date }
}

// ── Technical indicators ──

/**
 * Wraps the array of indicator values returned by `GET /api/technical/{symbol}.US?function=...`.
 *
 * EODHD returns the same shape (date + value) for every function; the value
 * field name is the function name (e.g. `atr`, `adx`). Two response classes
 * exist instead of one polymorphic DTO so each can map to a specific field
 * name without runtime field-name reflection.
 */
data class EodhdAtrResponse(
    val rows: List<EodhdAtrRowDto>,
    val errorMessage: String? = null,
) : EodhdApiResponse {
    override fun hasError(): Boolean = errorMessage != null

    override fun getErrorDescription(): String = errorMessage ?: "Unknown error"

    override fun isValid(): Boolean = true

    fun toAtrMap(minDate: LocalDate = LocalDate.of(2000, 1, 1)): Map<LocalDate, Double> =
        rows
            .mapNotNull { it.toEntry(minDate) }
            .toMap()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdAtrRowDto(
    @param:JsonProperty("date") val date: String,
    @param:JsonProperty("atr") val atr: Double?,
) {
    fun toEntry(minDate: LocalDate): Pair<LocalDate, Double>? {
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val value = atr ?: return null
        return if (parsedDate.isBefore(minDate)) null else parsedDate to value
    }
}

data class EodhdAdxResponse(
    val rows: List<EodhdAdxRowDto>,
    val errorMessage: String? = null,
) : EodhdApiResponse {
    override fun hasError(): Boolean = errorMessage != null

    override fun getErrorDescription(): String = errorMessage ?: "Unknown error"

    override fun isValid(): Boolean = true

    fun toAdxMap(minDate: LocalDate = LocalDate.of(2000, 1, 1)): Map<LocalDate, Double> =
        rows
            .mapNotNull { it.toEntry(minDate) }
            .toMap()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdAdxRowDto(
    @param:JsonProperty("date") val date: String,
    @param:JsonProperty("adx") val adx: Double?,
) {
    fun toEntry(minDate: LocalDate): Pair<LocalDate, Double>? {
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val value = adx ?: return null
        return if (parsedDate.isBefore(minDate)) null else parsedDate to value
    }
}

// ── Lenient section deserializers ──
//
// EODHD returns the literal string `"NA"` (not null, not an empty object)
// for entire fundamentals sections when a symbol has no data — typically
// leveraged / international ETFs. Without this, Jackson throws
// MismatchedInputException trying to deserialize a string into an object,
// killing the whole fundamentals fetch for that symbol.

private inline fun <reified T> readSectionOrNull(p: JsonParser): T? =
    when (p.currentToken) {
        JsonToken.START_OBJECT -> {
            val node: JsonNode = p.codec.readTree(p)
            p.codec.treeToValue(node, T::class.java)
        }
        else -> {
            p.skipChildren()
            null
        }
    }

class LenientHighlightsDeserializer : JsonDeserializer<EodhdHighlightsSection?>() {
    override fun deserialize(
        p: JsonParser,
        ctx: DeserializationContext,
    ): EodhdHighlightsSection? = readSectionOrNull(p)
}

class LenientGeneralDeserializer : JsonDeserializer<EodhdGeneralSection?>() {
    override fun deserialize(
        p: JsonParser,
        ctx: DeserializationContext,
    ): EodhdGeneralSection? = readSectionOrNull(p)
}

class LenientEarningsDeserializer : JsonDeserializer<EodhdEarningsSection?>() {
    override fun deserialize(
        p: JsonParser,
        ctx: DeserializationContext,
    ): EodhdEarningsSection? = readSectionOrNull(p)
}

class LenientFinancialsDeserializer : JsonDeserializer<EodhdFinancialsSection?>() {
    override fun deserialize(
        p: JsonParser,
        ctx: DeserializationContext,
    ): EodhdFinancialsSection? = readSectionOrNull(p)
}

// ── Fundamentals (company info + earnings + quarterly financials) ──

/**
 * Wraps the filtered fundamentals payload from `GET /api/fundamentals/{symbol}.US` — the request asks
 * for `General,Highlights,Earnings,Financials::Income_Statement::quarterly,Financials::Balance_Sheet::quarterly`
 * only, which still excludes the bulk of the unfiltered ~1+ MB payload (yearly statements, cash-flow,
 * outstanding shares, analyst data). EODHD nests data by section; we extract only what `CompanyInfo`,
 * the `Earning` model, and the quality metric (`Fundamental`) need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdFundamentalsResponse(
    @param:JsonProperty("General")
    @param:JsonDeserialize(using = LenientGeneralDeserializer::class)
    val general: EodhdGeneralSection? = null,
    @param:JsonProperty("Highlights")
    @param:JsonDeserialize(using = LenientHighlightsDeserializer::class)
    val highlights: EodhdHighlightsSection? = null,
    @param:JsonProperty("Earnings")
    @param:JsonDeserialize(using = LenientEarningsDeserializer::class)
    val earnings: EodhdEarningsSection? = null,
    @param:JsonProperty("Financials")
    @param:JsonDeserialize(using = LenientFinancialsDeserializer::class)
    val financials: EodhdFinancialsSection? = null,
    @param:JsonProperty("error") val errorMessage: String? = null,
) : EodhdApiResponse {
    override fun hasError(): Boolean = errorMessage != null

    override fun getErrorDescription(): String = errorMessage ?: "Unknown error"

    override fun isValid(): Boolean = general != null || highlights != null || earnings != null || financials != null

    fun toCompanyInfo(): CompanyInfo =
        CompanyInfo(
            sector = general?.sector,
            marketCap = highlights?.marketCapitalization?.toLongOrNull(),
        )

    fun toEarnings(symbol: String): List<Earning> =
        earnings
            ?.history
            ?.values
            ?.mapNotNull { it.toEarning(symbol) }
            ?.sortedByDescending { it.fiscalDateEnding }
            ?: emptyList()

    /**
     * Merges the income-statement and balance-sheet quarters into one [Fundamental] per fiscal period.
     * The two statements share the fiscal-date key and the same `filing_date` (one 10-Q); a quarter
     * present in only one statement still yields a record with the other statement's fields null.
     * Entries without a parseable fiscal date are dropped — `fiscalDateEnding` is the record's identity.
     */
    fun toFundamentals(symbol: String): List<Fundamental> {
        val income = financials?.incomeStatement?.quarterly.orEmpty()
        val balance = financials?.balanceSheet?.quarterly.orEmpty()
        return (income.keys + balance.keys)
            .mapNotNull { key ->
                val inc = income[key]
                val bal = balance[key]
                val fiscalEnd = parseEodhdDate(inc?.date ?: bal?.date ?: key) ?: return@mapNotNull null
                Fundamental(
                    symbol = symbol,
                    fiscalDateEnding = fiscalEnd,
                    filingDate = parseEodhdDate(inc?.filingDate ?: bal?.filingDate),
                    grossProfit = inc?.grossProfit,
                    costOfRevenue = inc?.costOfRevenue,
                    totalRevenue = inc?.totalRevenue,
                    operatingIncome = inc?.operatingIncome,
                    netIncome = inc?.netIncome,
                    totalAssets = bal?.totalAssets,
                    totalStockholderEquity = bal?.totalStockholderEquity,
                    totalCurrentAssets = bal?.totalCurrentAssets,
                    totalCurrentLiabilities = bal?.totalCurrentLiabilities,
                )
            }.sortedByDescending { it.fiscalDateEnding }
    }
}

private fun parseEodhdDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdGeneralSection(
    @param:JsonProperty("Sector") val sector: String? = null,
    @param:JsonProperty("Industry") val industry: String? = null,
    // EODHD returns CIK with variable padding ("0000320193" or "320193"); we
    // store the raw string and let `EdgarClient` re-pad to the 10-digit form.
    @param:JsonProperty("CIK") val cik: String? = null,
    // `IsDelisted` arrives as the integer 0/1 (rendered as a JSON number)
    // for most symbols, but EODHD has historically returned strings on a
    // small slice of the universe. Use String? so Jackson tolerates both
    // shapes, and resolve via `isDelisted` below.
    @param:JsonProperty("IsDelisted") val isDelistedRaw: String? = null,
    @param:JsonProperty("DelistedDate") val delistedDate: String? = null,
) {
    val isDelisted: Boolean get() = isDelistedRaw?.let { it == "1" || it.equals("true", ignoreCase = true) } ?: false

    val parsedDelistedDate: LocalDate? get() = delistedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdHighlightsSection(
    // EODHD occasionally returns market cap as a string for ADRs / dual-listed
    // names; keeping it as String? + toLongOrNull() in the mapper avoids a
    // Jackson failure that would null out the entire CompanyInfo for a symbol.
    @param:JsonProperty("MarketCapitalization") val marketCapitalization: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdEarningsSection(
    @param:JsonProperty("History") val history: Map<String, EodhdEarningEntry>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdEarningEntry(
    @param:JsonProperty("date") val date: String? = null,
    @param:JsonProperty("reportDate") val reportDate: String? = null,
    @param:JsonProperty("epsActual") val epsActual: BigDecimal? = null,
    @param:JsonProperty("epsEstimate") val epsEstimate: BigDecimal? = null,
    @param:JsonProperty("epsDifference") val epsDifference: BigDecimal? = null,
    @param:JsonProperty("surprisePercent") val surprisePercent: BigDecimal? = null,
    @param:JsonProperty("beforeAfterMarket") val beforeAfterMarket: String? = null,
) {
    fun toEarning(symbol: String): Earning? {
        val fiscalEnd = parseDate(date) ?: return null
        return Earning(
            symbol = symbol,
            fiscalDateEnding = fiscalEnd,
            reportedDate = parseDate(reportDate),
            reportedEps = epsActual,
            estimatedEps = epsEstimate,
            surprise = epsDifference,
            surprisePercentage = surprisePercent,
            reportTime = beforeAfterMarket,
        )
    }

    private fun parseDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

// ── Financials (quarterly income statement + balance sheet) ──
//
// EODHD nests Financials by statement → period-class → fiscal-date. We request only the `quarterly`
// maps of Income_Statement and Balance_Sheet (filter `Financials::Income_Statement::quarterly,
// Financials::Balance_Sheet::quarterly`) and curate the line items the quality metric needs. Each
// entry carries both `date` (fiscal-period end) and `filing_date` (when it became public) — the
// point-in-time visibility key (ADR 0019). Line items arrive as JSON strings or numbers; Jackson
// coerces both into BigDecimal.

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdFinancialsSection(
    @param:JsonProperty("Balance_Sheet") val balanceSheet: EodhdBalanceSheet? = null,
    @param:JsonProperty("Income_Statement") val incomeStatement: EodhdIncomeStatement? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdIncomeStatement(
    @param:JsonProperty("quarterly") val quarterly: Map<String, EodhdIncomeStatementEntry>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdBalanceSheet(
    @param:JsonProperty("quarterly") val quarterly: Map<String, EodhdBalanceSheetEntry>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdIncomeStatementEntry(
    @param:JsonProperty("date") val date: String? = null,
    @param:JsonProperty("filing_date") val filingDate: String? = null,
    @param:JsonProperty("grossProfit") val grossProfit: BigDecimal? = null,
    @param:JsonProperty("costOfRevenue") val costOfRevenue: BigDecimal? = null,
    @param:JsonProperty("totalRevenue") val totalRevenue: BigDecimal? = null,
    @param:JsonProperty("operatingIncome") val operatingIncome: BigDecimal? = null,
    @param:JsonProperty("netIncome") val netIncome: BigDecimal? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdBalanceSheetEntry(
    @param:JsonProperty("date") val date: String? = null,
    @param:JsonProperty("filing_date") val filingDate: String? = null,
    @param:JsonProperty("totalAssets") val totalAssets: BigDecimal? = null,
    @param:JsonProperty("totalStockholderEquity") val totalStockholderEquity: BigDecimal? = null,
    @param:JsonProperty("totalCurrentAssets") val totalCurrentAssets: BigDecimal? = null,
    @param:JsonProperty("totalCurrentLiabilities") val totalCurrentLiabilities: BigDecimal? = null,
)

// ── End-of-Day OHLCV row ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdBarDto(
    @param:JsonProperty("date") val date: String,
    @param:JsonProperty("open") val open: Double?,
    @param:JsonProperty("high") val high: Double?,
    @param:JsonProperty("low") val low: Double?,
    @param:JsonProperty("close") val close: Double?,
    @param:JsonProperty("adjusted_close") val adjustedClose: Double?,
    @param:JsonProperty("volume") val volume: Long?,
) {
    fun toRawBar(
        symbol: String,
        minDate: LocalDate,
    ): RawBar? {
        val validated = validate(minDate) ?: return null
        val factor = validated.adjClose / validated.rawClose
        return RawBar(
            symbol = symbol,
            date = validated.date,
            open = (open ?: 0.0) * factor,
            high = (high ?: 0.0) * factor,
            low = (low ?: 0.0) * factor,
            close = validated.adjClose,
            volume = volume ?: 0L,
        )
    }

    private fun validate(minDate: LocalDate): ValidatedBar? {
        val parsedDate =
            runCatching { LocalDate.parse(date) }
                .getOrNull()
                ?.takeIf { !it.isBefore(minDate) }
        val rawClose = close?.takeIf { it > 0.0 }
        val adjClose = adjustedClose
        if (parsedDate == null || rawClose == null || adjClose == null) return null
        return ValidatedBar(parsedDate, rawClose, adjClose)
    }

    private data class ValidatedBar(
        val date: LocalDate,
        val rawClose: Double,
        val adjClose: Double,
    )
}
