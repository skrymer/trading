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
    @JsonProperty("Meta Data")
    val metaData: AdjustedMetaData? = null,
    @JsonProperty("Time Series (Daily)")
    val timeSeriesDaily: Map<String, DailyAdjustedData>? = null,
    @JsonProperty("Error Message")
    val errorMessage: String? = null,
    @JsonProperty("Note")
    val note: String? = null,
    @JsonProperty("Information")
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
    @JsonProperty("1. Information") val information: String,
    @JsonProperty("2. Symbol") val symbol: String,
    @JsonProperty("3. Last Refreshed") val lastRefreshed: String,
    @JsonProperty("4. Output Size") val outputSize: String,
    @JsonProperty("5. Time Zone") val timeZone: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailyAdjustedData(
    @JsonProperty("1. open") val open: String,
    @JsonProperty("2. high") val high: String,
    @JsonProperty("3. low") val low: String,
    @JsonProperty("4. close") val close: String,
    @JsonProperty("5. adjusted close") val adjustedClose: String,
    @JsonProperty("6. volume") val volume: String,
    @JsonProperty("7. dividend amount") val dividendAmount: String,
    @JsonProperty("8. split coefficient") val splitCoefficient: String,
) {
    fun isEmptyQuote(): Boolean {
        val vol = volume.toLongOrNull() ?: 0L
        return vol == 0L && open == close && high == close && low == close
    }
}

// ── ATR ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageATR(
    @JsonProperty("Meta Data") val metaData: IndicatorMetaData? = null,
    @JsonProperty("Technical Analysis: ATR") val technicalAnalysis: Map<String, ATRData>? = null,
    @JsonProperty("Error Message") val errorMessage: String? = null,
    @JsonProperty("Note") val note: String? = null,
    @JsonProperty("Information") val information: String? = null,
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
    @JsonProperty("ATR") val atr: String,
)

// ── ADX ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageADX(
    @JsonProperty("Meta Data") val metaData: IndicatorMetaData? = null,
    @JsonProperty("Technical Analysis: ADX") val technicalAnalysis: Map<String, ADXData>? = null,
    @JsonProperty("Error Message") val errorMessage: String? = null,
    @JsonProperty("Note") val note: String? = null,
    @JsonProperty("Information") val information: String? = null,
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
    @JsonProperty("ADX") val adx: String,
)

// ── Shared Indicator Metadata ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class IndicatorMetaData(
    @JsonProperty("1: Symbol") val symbol: String,
    @JsonProperty("2: Indicator") val indicator: String,
    @JsonProperty("3: Last Refreshed") val lastRefreshed: String,
    @JsonProperty("4: Interval") val interval: String,
    @JsonProperty("5: Time Period") val timePeriod: Int,
    @JsonProperty("6: Time Zone") val timeZone: String,
)

// ── Earnings ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageEarnings(
    @JsonProperty("symbol") val symbol: String? = null,
    @JsonProperty("annualEarnings") val annualEarnings: List<AnnualEarning>? = null,
    @JsonProperty("quarterlyEarnings") val quarterlyEarnings: List<QuarterlyEarning>? = null,
    @JsonProperty("Error Message") val errorMessage: String? = null,
    @JsonProperty("Note") val note: String? = null,
    @JsonProperty("Information") val information: String? = null,
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
    @JsonProperty("fiscalDateEnding") val fiscalDateEnding: String,
    @JsonProperty("reportedEPS") val reportedEPS: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class QuarterlyEarning(
    @JsonProperty("fiscalDateEnding") val fiscalDateEnding: String,
    @JsonProperty("reportedDate") val reportedDate: String? = null,
    @JsonProperty("reportedEPS") val reportedEPS: String? = null,
    @JsonProperty("estimatedEPS") val estimatedEPS: String? = null,
    @JsonProperty("surprise") val surprise: String? = null,
    @JsonProperty("surprisePercentage") val surprisePercentage: String? = null,
    @JsonProperty("reportTime") val reportTime: String? = null,
)

// ── Company Overview ──

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageCompanyOverview(
    @JsonProperty("Symbol") val symbol: String? = null,
    @JsonProperty("Name") val name: String? = null,
    @JsonProperty("Sector") val sector: String? = null,
    @JsonProperty("Industry") val industry: String? = null,
    @JsonProperty("MarketCapitalization") val marketCapitalization: String? = null,
    @JsonProperty("Error Message") val errorMessage: String? = null,
    @JsonProperty("Note") val note: String? = null,
    @JsonProperty("Information") val information: String? = null,
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
