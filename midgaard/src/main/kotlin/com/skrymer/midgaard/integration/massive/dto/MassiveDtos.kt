package com.skrymer.midgaard.integration.massive.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.midgaard.model.RawBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@JsonIgnoreProperties(ignoreUnknown = true)
data class MassiveAggregatesResponse(
    @param:JsonProperty("ticker") val ticker: String? = null,
    @param:JsonProperty("adjusted") val adjusted: Boolean? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("resultsCount") val resultsCount: Int? = null,
    @param:JsonProperty("results") val results: List<AggregateBar>? = null,
    @param:JsonProperty("next_url") val nextUrl: String? = null,
    @param:JsonProperty("error") val error: String? = null,
    @param:JsonProperty("message") val message: String? = null,
) {
    fun hasError(): Boolean = status == "ERROR" || error != null || message != null

    fun getErrorDescription(): String =
        when {
            error != null -> error
            message != null -> message
            else -> "Unknown error"
        }

    fun isValid(): Boolean = results != null && ticker != null

    fun toRawBars(
        symbol: String,
        minDate: LocalDate = LocalDate.of(2000, 1, 1),
    ): List<RawBar> {
        return results
            ?.mapNotNull { bar ->
                val date = Instant.ofEpochMilli(bar.timestamp).atZone(EASTERN_TIME).toLocalDate()
                if (date.isBefore(minDate)) return@mapNotNull null

                RawBar(
                    symbol = symbol,
                    date = date,
                    open = bar.open,
                    high = bar.high,
                    low = bar.low,
                    close = bar.close,
                    volume = bar.volume,
                )
            }?.sortedBy { it.date } ?: emptyList()
    }

    companion object {
        private val EASTERN_TIME: ZoneId = ZoneId.of("America/New_York")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AggregateBar(
    @param:JsonProperty("o") val open: Double = 0.0,
    @param:JsonProperty("h") val high: Double = 0.0,
    @param:JsonProperty("l") val low: Double = 0.0,
    @param:JsonProperty("c") val close: Double = 0.0,
    @param:JsonProperty("v") val volume: Long = 0L,
    @param:JsonProperty("vw") val volumeWeighted: Double = 0.0,
    @param:JsonProperty("n") val numberOfTransactions: Int = 0,
    @param:JsonProperty("t") val timestamp: Long = 0L,
)
