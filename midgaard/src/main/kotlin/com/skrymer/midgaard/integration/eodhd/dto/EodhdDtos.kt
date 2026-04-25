package com.skrymer.midgaard.integration.eodhd.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.midgaard.model.CompanyInfo
import com.skrymer.midgaard.model.Earning
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

// ── Fundamentals (company info + earnings) ──

/**
 * Wraps the fundamentals payload returned by `GET /api/fundamentals/{symbol}.US?filter=General,Highlights,Earnings`.
 *
 * EODHD nests data by section; we extract only what `CompanyInfo` and the
 * `Earning` model need. Filtered request keeps the response under ~50 KB even
 * for heavy symbols like AAPL, vs the unfiltered ~1+ MB payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdFundamentalsResponse(
    @param:JsonProperty("General") val general: EodhdGeneralSection? = null,
    @param:JsonProperty("Highlights") val highlights: EodhdHighlightsSection? = null,
    @param:JsonProperty("Earnings") val earnings: EodhdEarningsSection? = null,
    @param:JsonProperty("error") val errorMessage: String? = null,
) : EodhdApiResponse {
    override fun hasError(): Boolean = errorMessage != null

    override fun getErrorDescription(): String = errorMessage ?: "Unknown error"

    override fun isValid(): Boolean = general != null || highlights != null || earnings != null

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
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdGeneralSection(
    @param:JsonProperty("Sector") val sector: String? = null,
    @param:JsonProperty("Industry") val industry: String? = null,
)

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
