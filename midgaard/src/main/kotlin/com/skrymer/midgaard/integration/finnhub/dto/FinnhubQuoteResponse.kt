package com.skrymer.midgaard.integration.finnhub.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.midgaard.model.LatestQuote

@JsonIgnoreProperties(ignoreUnknown = true)
data class FinnhubQuoteResponse(
    @param:JsonProperty("c") val currentPrice: Double = 0.0,
    @param:JsonProperty("d") val change: Double? = null,
    @param:JsonProperty("dp") val changePercent: Double? = null,
    @param:JsonProperty("h") val highPrice: Double = 0.0,
    @param:JsonProperty("l") val lowPrice: Double = 0.0,
    @param:JsonProperty("o") val openPrice: Double = 0.0,
    @param:JsonProperty("pc") val previousClose: Double = 0.0,
    @param:JsonProperty("t") val timestamp: Long = 0L,
) {
    fun isValid(): Boolean = currentPrice > 0.0 && timestamp > 0L

    fun toLatestQuote(symbol: String): LatestQuote =
        LatestQuote(
            symbol = symbol,
            price = currentPrice,
            previousClose = previousClose,
            change = change ?: (currentPrice - previousClose),
            changePercent = changePercent ?: 0.0,
            volume = 0L,
            timestamp = timestamp,
            high = highPrice,
            low = lowPrice,
        )
}
