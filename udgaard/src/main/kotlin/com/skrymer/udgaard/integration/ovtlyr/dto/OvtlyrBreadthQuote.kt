package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.BreadthQuote
import java.time.LocalDate

/**
 * Represents a breadth quote from Ovtlyr API.
 * Can represent either market breadth or sector breadth.
 *
 * Ovtlyr payload example:
 * {
 *   "StockSymbol": "XLU",
 *   "Quotedate": "2025-09-09T00:00:00",
 *   "QuotedateStr": "Sep 09, 2025",
 *   "Total_ClosePrice": 4960.6794,
 *   "Bull_Total": 9,
 *   "Bear_Total": 75,
 *   "Uptrend_DifferenceWithPrevious": 4,
 *   "Downtrend_DifferenceWithPrevious": -1,
 *   "Uptrend_DifferenceWithPrevious_str": "+4",
 *   "Downtrend_DifferenceWithPrevious_str": "-1",
 *   "Bull_per": 10.7142857142857,
 *   "Bull_EMA_5": 12.0495890092481,
 *   "Bull_EMA_10": 16.1554939301461,
 *   "Bull_EMA_20": 22.3545515878047,
 *   "Bull_EMA_50": 28.5068082474362,
 *   "Lower": 25,
 *   "Midpoint": 50,
 *   "Upper": 75,
 *   "Uptrend": 25,
 *   "Neutral": 14,
 *   "Downtrend": 45,
 *   "Total": 84,
 *   "d4_Hi": 34,
 *   "d4_Lo": -36,
 *   "uptrend_display": null,
 *   "downtrend_display": null,
 *   "sector_Score": -1,
 *   "passing_Overall": 0
 * }
 */
class OvtlyrBreadthQuote {
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

    @JsonProperty("sector_Score")
    val donkeyChannelScore: Int = 0

    fun toModel(breadth: OvtlyrBreadth, stockInSector: OvtlyrStockInformation?): BreadthQuote {
        val stockQuote = stockInSector?.getPreviousQuote(stockInSector.getQuoteForDate(quoteDate!!))
        return BreadthQuote(
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
            bull_per,
            donchianUpperBand = calculateDonchianUpperBand(breadth, this),
            previousDonchianUpperBand = calculateDonchianUpperBand(breadth, breadth.getPreviousQuote(this)),
            donchianLowerBand = calculateDonchianLowerBand(breadth, this),
            previousDonchianLowerBand = calculateDonchianLowerBand(breadth, breadth.getPreviousQuote(this)),
            heatmap = stockQuote?.sectorHeatmap ?: 0.0,
            previousHeatmap = stockInSector?.getPreviousQuote(stockQuote)?.sectorHeatmap ?: 0.0,
            donkeyChannelScore = donkeyChannelScore
        )
    }

    fun calculateDonchianUpperBand(breadth: OvtlyrBreadth, quote: OvtlyrBreadthQuote, lookback: Int = 4) =
        (listOf(quote) + breadth.getPreviousQuotes(quote, lookback - 1))
            .maxOfOrNull { it.numberOfStocksInUptrend.toDouble() } ?: 0.0

    fun calculateDonchianLowerBand(breadth: OvtlyrBreadth, quote: OvtlyrBreadthQuote, lookback: Int = 2) =
        (listOf(quote) + breadth.getPreviousQuotes(quote, lookback - 1))
            .minOfOrNull { it.numberOfStocksInDowntrend.toDouble() } ?: 0.0
}
