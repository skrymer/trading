package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.MarketBreadthQuote
import java.time.LocalDate

/**
 * Represents a market-breadth quote.
 *
 * Ovtlyr payload:
 * {
 * "StockSymbol": "FULLSTOCK",
 * "Quotedate": "2025-06-05T00:00:00",
 * "QuotedateStr": "Jun 05, 2025",
 * "Total_ClosePrice": 980203.1443,
 * "Bull_Total": 958,
 * "Bear_Total": 1424,
 * "Uptrend_DifferenceWithPrevious": -29,
 * "Downtrend_DifferenceWithPrevious": 33,
 * "Uptrend_DifferenceWithPrevious_str": "-29",
 * "Downtrend_DifferenceWithPrevious_str": "+33",
 * "Bull_per": 40.2183039462637,
 * "Bull_EMA_5": 38.8829413699683,
 * "Bull_EMA_10": 41.453098749149,
 * "Bull_EMA_20": 46.8689974278856,
 * "Bull_EMA_50": 47.1806479235534,
 * "Lower": 25,
 * "Midpoint": 50,
 * "Upper": 75,
 * "Uptrend": 1163,
 * "Neutral": 571,
 * "Downtrend": 648,
 * "Total": 2382
 * }
 */
class OvtlyrMarketBreadthQuote {
    @JsonProperty("StockSymbol")
    val symbol: String? = null

    @JsonProperty("Quotedate")
    val quoteDate: LocalDate? = null

    @JsonProperty("Bull_Total")
    val numberOfStocksWithABuySignal: Int = 0

    @JsonProperty("Bear_Total")
    val numberOfStocksWithASellSignal: Int = 0

    @JsonProperty("Uptrend")
    val numberOfStocksInUptrend: Int = 0

    @JsonProperty("Neutral")
    val numberOfStocksInNeutral: Int = 0

    @JsonProperty("Downtrend")
    val numberOfStocksInDowntrend: Int = 0

    @JsonProperty("Bull_per")
    private val bull_per: Double = 0.0

    @JsonProperty("Bull_EMA_5")
    val ema_5: Double = 0.0

    @JsonProperty("Bull_EMA_10")
    val ema_10: Double = 0.0

    @JsonProperty("Bull_EMA_20")
    val ema_20: Double = 0.0

    @JsonProperty("Bull_EMA_50")
    val ema_50: Double = 0.0

    fun toModel(): MarketBreadthQuote {
        return MarketBreadthQuote(
            symbol,
            quoteDate,
            numberOfStocksWithABuySignal,
            numberOfStocksWithASellSignal,
            numberOfStocksInUptrend,
            numberOfStocksInNeutral,
            numberOfStocksInDowntrend,
            ema_5,
            ema_10,
            ema_20,
            ema_50,
            bull_per
        )
    }
}