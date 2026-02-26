package com.skrymer.midgaard.integration.massive.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.midgaard.model.RawBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@JsonIgnoreProperties(ignoreUnknown = true)
data class MassiveAggregatesResponse(
    @JsonProperty("ticker") val ticker: String? = null,
    @JsonProperty("adjusted") val adjusted: Boolean? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("resultsCount") val resultsCount: Int? = null,
    @JsonProperty("results") val results: List<AggregateBar>? = null,
    @JsonProperty("next_url") val nextUrl: String? = null,
    @JsonProperty("error") val error: String? = null,
    @JsonProperty("message") val message: String? = null,
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
    @JsonProperty("o") val open: Double = 0.0,
    @JsonProperty("h") val high: Double = 0.0,
    @JsonProperty("l") val low: Double = 0.0,
    @JsonProperty("c") val close: Double = 0.0,
    @JsonProperty("v") val volume: Long = 0L,
    @JsonProperty("vw") val volumeWeighted: Double = 0.0,
    @JsonProperty("n") val numberOfTransactions: Int = 0,
    @JsonProperty("t") val timestamp: Long = 0L,
)
