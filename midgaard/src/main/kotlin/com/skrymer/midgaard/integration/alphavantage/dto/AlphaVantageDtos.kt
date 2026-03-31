package com.skrymer.midgaard.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.midgaard.model.Earning
import com.skrymer.midgaard.model.RawBar
import org.slf4j.LoggerFactory
import java.time.LocalDate

// ── Common API response interface ──

interface AlphaVantageApiResponse {
    fun hasError(): Boolean

    fun getErrorDescription(): String

    fun isValid(): Boolean
}

// ── Time Series Daily Adjusted ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageTimeSeriesDailyAdjusted(
    @param:JsonProperty("Meta Data")
    val metaData: AdjustedMetaData? = null,
    @param:JsonProperty("Time Series (Daily)")
    val timeSeriesDaily: Map<String, DailyAdjustedData>? = null,
    @param:JsonProperty("Error Message")
    val errorMessage: String? = null,
    @param:JsonProperty("Note")
    val note: String? = null,
    @param:JsonProperty("Information")
    val information: String? = null,
) : AlphaVantageApiResponse {
    override fun hasError(): Boolean = errorMessage != null || note != null || information != null

    override fun getErrorDescription(): String =
        when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }

    override fun isValid(): Boolean = metaData != null && timeSeriesDaily != null

    fun toRawBars(minDate: LocalDate = LocalDate.of(2000, 1, 1)): List<RawBar> {
        val symbol = metaData?.symbol ?: ""
        return timeSeriesDaily
            ?.mapNotNull { (dateString, data) -> parseDailyBar(symbol, dateString, data, minDate) }
            ?.sortedBy { it.date } ?: emptyList()
    }

    private fun parseDailyBar(
        symbol: String,
        dateString: String,
        data: DailyAdjustedData,
        minDate: LocalDate,
    ): RawBar? {
        val date = LocalDate.parse(dateString)
        if (date.isBefore(minDate)) return null
        if (data.isEmptyQuote()) return null
        val rawClose = data.close.toDoubleOrNull() ?: 0.0
        val adjustedClose = data.adjustedClose.toDoubleOrNull() ?: 0.0
        val adjustmentFactor = if (rawClose > 0.0) adjustedClose / rawClose else 1.0
        return RawBar(
            symbol = symbol,
            date = date,
            open = (data.open.toDoubleOrNull() ?: 0.0) * adjustmentFactor,
            high = (data.high.toDoubleOrNull() ?: 0.0) * adjustmentFactor,
            low = (data.low.toDoubleOrNull() ?: 0.0) * adjustmentFactor,
            close = adjustedClose,
            volume = data.volume.toLongOrNull() ?: 0L,
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdjustedMetaData(
    @param:JsonProperty("1. Information") val information: String,
    @param:JsonProperty("2. Symbol") val symbol: String,
    @param:JsonProperty("3. Last Refreshed") val lastRefreshed: String,
    @param:JsonProperty("4. Output Size") val outputSize: String,
    @param:JsonProperty("5. Time Zone") val timeZone: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailyAdjustedData(
    @param:JsonProperty("1. open") val open: String,
    @param:JsonProperty("2. high") val high: String,
    @param:JsonProperty("3. low") val low: String,
    @param:JsonProperty("4. close") val close: String,
    @param:JsonProperty("5. adjusted close") val adjustedClose: String,
    @param:JsonProperty("6. volume") val volume: String,
    @param:JsonProperty("7. dividend amount") val dividendAmount: String,
    @param:JsonProperty("8. split coefficient") val splitCoefficient: String,
) {
    fun isEmptyQuote(): Boolean {
        val vol = volume.toLongOrNull() ?: 0L
        return vol == 0L && open == close && high == close && low == close
    }
}

// ── ATR ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageATR(
    @param:JsonProperty("Meta Data") val metaData: IndicatorMetaData? = null,
    @param:JsonProperty("Technical Analysis: ATR") val technicalAnalysis: Map<String, ATRData>? = null,
    @param:JsonProperty("Error Message") val errorMessage: String? = null,
    @param:JsonProperty("Note") val note: String? = null,
    @param:JsonProperty("Information") val information: String? = null,
) : AlphaVantageApiResponse {
    override fun hasError(): Boolean = errorMessage != null || note != null || information != null

    override fun getErrorDescription(): String =
        when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }

    override fun isValid(): Boolean = metaData != null && technicalAnalysis != null

    fun toATRMap(minDate: LocalDate = LocalDate.of(2000, 1, 1)): Map<LocalDate, Double> {
        return technicalAnalysis
            ?.mapNotNull { (dateString, data) ->
                runCatching {
                    val date = LocalDate.parse(dateString)
                    if (date.isBefore(minDate)) return@runCatching null
                    val atr = data.atr.toDoubleOrNull()
                    if (atr != null) date to atr else null
                }.getOrNull()
            }?.toMap() ?: emptyMap()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ATRData(
    @param:JsonProperty("ATR") val atr: String,
)

// ── ADX ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageADX(
    @param:JsonProperty("Meta Data") val metaData: IndicatorMetaData? = null,
    @param:JsonProperty("Technical Analysis: ADX") val technicalAnalysis: Map<String, ADXData>? = null,
    @param:JsonProperty("Error Message") val errorMessage: String? = null,
    @param:JsonProperty("Note") val note: String? = null,
    @param:JsonProperty("Information") val information: String? = null,
) : AlphaVantageApiResponse {
    override fun hasError(): Boolean = errorMessage != null || note != null || information != null

    override fun getErrorDescription(): String =
        when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }

    override fun isValid(): Boolean = metaData != null && technicalAnalysis != null

    fun toADXMap(minDate: LocalDate = LocalDate.of(2000, 1, 1)): Map<LocalDate, Double> {
        return technicalAnalysis
            ?.mapNotNull { (dateString, data) ->
                runCatching {
                    val date = LocalDate.parse(dateString)
                    if (date.isBefore(minDate)) return@runCatching null
                    val adx = data.adx.toDoubleOrNull()
                    if (adx != null) date to adx else null
                }.getOrNull()
            }?.toMap() ?: emptyMap()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ADXData(
    @param:JsonProperty("ADX") val adx: String,
)

// ── Shared Indicator Metadata ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class IndicatorMetaData(
    @param:JsonProperty("1: Symbol") val symbol: String,
    @param:JsonProperty("2: Indicator") val indicator: String,
    @param:JsonProperty("3: Last Refreshed") val lastRefreshed: String,
    @param:JsonProperty("4: Interval") val interval: String,
    @param:JsonProperty("5: Time Period") val timePeriod: Int,
    @param:JsonProperty("6: Time Zone") val timeZone: String,
)

// ── Earnings ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageEarnings(
    @param:JsonProperty("symbol") val symbol: String? = null,
    @param:JsonProperty("annualEarnings") val annualEarnings: List<AnnualEarning>? = null,
    @param:JsonProperty("quarterlyEarnings") val quarterlyEarnings: List<QuarterlyEarning>? = null,
    @param:JsonProperty("Error Message") val errorMessage: String? = null,
    @param:JsonProperty("Note") val note: String? = null,
    @param:JsonProperty("Information") val information: String? = null,
) : AlphaVantageApiResponse {
    override fun hasError(): Boolean = errorMessage != null || note != null || information != null

    override fun getErrorDescription(): String =
        when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }

    override fun isValid(): Boolean = symbol != null && quarterlyEarnings != null

    fun toEarnings(): List<Earning> {
        val symbolValue = symbol ?: ""
        return quarterlyEarnings
            ?.mapNotNull { quarterly ->
                try {
                    Earning(
                        symbol = symbolValue,
                        fiscalDateEnding = LocalDate.parse(quarterly.fiscalDateEnding),
                        reportedDate = quarterly.reportedDate?.let { LocalDate.parse(it) },
                        reportedEps = quarterly.reportedEPS?.toBigDecimalOrNull(),
                        estimatedEps = quarterly.estimatedEPS?.toBigDecimalOrNull(),
                        surprise = quarterly.surprise?.toBigDecimalOrNull(),
                        surprisePercentage = quarterly.surprisePercentage?.toBigDecimalOrNull(),
                        reportTime = quarterly.reportTime,
                    )
                } catch (e: Exception) {
                    logger.warn("Skipping invalid earnings entry for $symbolValue: ${e.message}")
                    null
                }
            }?.sortedBy { it.fiscalDateEnding } ?: emptyList()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AlphaVantageEarnings::class.java)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AnnualEarning(
    @param:JsonProperty("fiscalDateEnding") val fiscalDateEnding: String,
    @param:JsonProperty("reportedEPS") val reportedEPS: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuarterlyEarning(
    @param:JsonProperty("fiscalDateEnding") val fiscalDateEnding: String,
    @param:JsonProperty("reportedDate") val reportedDate: String? = null,
    @param:JsonProperty("reportedEPS") val reportedEPS: String? = null,
    @param:JsonProperty("estimatedEPS") val estimatedEPS: String? = null,
    @param:JsonProperty("surprise") val surprise: String? = null,
    @param:JsonProperty("surprisePercentage") val surprisePercentage: String? = null,
    @param:JsonProperty("reportTime") val reportTime: String? = null,
)

// ── Company Overview ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageCompanyOverview(
    @param:JsonProperty("Symbol") val symbol: String? = null,
    @param:JsonProperty("Name") val name: String? = null,
    @param:JsonProperty("Sector") val sector: String? = null,
    @param:JsonProperty("Industry") val industry: String? = null,
    @param:JsonProperty("MarketCapitalization") val marketCapitalization: String? = null,
    @param:JsonProperty("Error Message") val errorMessage: String? = null,
    @param:JsonProperty("Note") val note: String? = null,
    @param:JsonProperty("Information") val information: String? = null,
) : AlphaVantageApiResponse {
    override fun hasError(): Boolean = errorMessage != null || note != null || information != null

    override fun getErrorDescription(): String =
        when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }

    override fun isValid(): Boolean = symbol != null && sector != null

    fun toSector(): String? = sector?.uppercase()?.trim()

    fun toMarketCap(): Long? = marketCapitalization?.toLongOrNull()
}

// ── Currency Exchange Rate (real-time) ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageCurrencyExchangeRate(
    @param:JsonProperty("Realtime Currency Exchange Rate") val exchangeRate: CurrencyExchangeRateData? = null,
    @param:JsonProperty("Error Message") val errorMessage: String? = null,
    @param:JsonProperty("Note") val note: String? = null,
    @param:JsonProperty("Information") val information: String? = null,
) : AlphaVantageApiResponse {
    override fun hasError(): Boolean = errorMessage != null || note != null || information != null

    override fun getErrorDescription(): String =
        when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }

    override fun isValid(): Boolean = exchangeRate?.rate != null

    fun toRate(): Double? = exchangeRate?.rate?.toDoubleOrNull()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CurrencyExchangeRateData(
    @param:JsonProperty("1. From_Currency Code") val fromCurrency: String? = null,
    @param:JsonProperty("2. From_Currency Name") val fromCurrencyName: String? = null,
    @param:JsonProperty("3. To_Currency Code") val toCurrency: String? = null,
    @param:JsonProperty("4. To_Currency Name") val toCurrencyName: String? = null,
    @param:JsonProperty("5. Exchange Rate") val rate: String? = null,
    @param:JsonProperty("6. Last Refreshed") val lastRefreshed: String? = null,
    @param:JsonProperty("8. Bid Price") val bidPrice: String? = null,
    @param:JsonProperty("9. Ask Price") val askPrice: String? = null,
)

// ── FX Daily (historical) ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageFxDaily(
    @param:JsonProperty("Meta Data") val metaData: FxDailyMetaData? = null,
    @param:JsonProperty("Time Series FX (Daily)") val timeSeries: Map<String, FxDailyData>? = null,
    @param:JsonProperty("Error Message") val errorMessage: String? = null,
    @param:JsonProperty("Note") val note: String? = null,
    @param:JsonProperty("Information") val information: String? = null,
) : AlphaVantageApiResponse {
    override fun hasError(): Boolean = errorMessage != null || note != null || information != null

    override fun getErrorDescription(): String =
        when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }

    override fun isValid(): Boolean = metaData != null && timeSeries != null

    fun rateForDate(date: LocalDate): Double? = timeSeries?.get(date.toString())?.close?.toDoubleOrNull()

    fun closestRateForDate(date: LocalDate): Double? {
        val series = timeSeries ?: return null
        // Try exact date first, then walk backwards up to 5 days (weekends/holidays)
        for (offset in 0L..5L) {
            val d = date.minusDays(offset)
            series[d.toString()]?.close?.toDoubleOrNull()?.let { return it }
        }
        return null
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class FxDailyMetaData(
    @param:JsonProperty("1. Information") val information: String? = null,
    @param:JsonProperty("2. From Symbol") val fromSymbol: String? = null,
    @param:JsonProperty("3. To Symbol") val toSymbol: String? = null,
    @param:JsonProperty("4. Output Size") val outputSize: String? = null,
    @param:JsonProperty("5. Last Refreshed") val lastRefreshed: String? = null,
    @param:JsonProperty("6. Time Zone") val timeZone: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FxDailyData(
    @param:JsonProperty("1. open") val open: String? = null,
    @param:JsonProperty("2. high") val high: String? = null,
    @param:JsonProperty("3. low") val low: String? = null,
    @param:JsonProperty("4. close") val close: String? = null,
)
