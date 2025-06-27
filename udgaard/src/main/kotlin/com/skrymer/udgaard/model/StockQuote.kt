package com.skrymer.udgaard.model

import java.time.LocalDate
import kotlin.Boolean
import kotlin.String

/**
 * A stock quote.
 */
class StockQuote {
    /**
     * The stock symbol
     */
    var symbol: String = ""

    /**
     * The date when the stock quote was taken.
     */
    var date: LocalDate? = null

    /**
     * The stock price at close.
     */
    var closePrice: Double = 0.0

    /**
     * The stock price at open.
     */
    var openPrice: Double = 0.0

    /**
     * The heatmap value of the stock.
     *
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    var heatmap: Double = 0.0

    /**
     * The previous heatmap value of the stock.
     *
     * A value between 0 and 100, 0 being max fear and 100 max greed.
     */
    var previousHeatmap: Double = 0.0

    /**
     * The heatmap value of the sector the stock belongs to.
     *
     * A value between 0 and 100, 0 being max fear and 100 max greed.
     */
    var sectorHeatmap: Double = 0.0

    /**
     * The previous heatmap value of the sector the stock belongs to.
     *
     * A value between 0 and 100, 0 being max fear and 100 max greed.
     */
    var previousSectorHeatmap: Double = 0.0

    /**
     * true if the sector the stock belongs to is in an uptrend.
     */
    var sectorIsInUptrend = false

    /**
     * The ovtlyr Buy/Sell signal or null if neither.
     */
    var signal: String? = null

    /**
     * The 10 ema value on close
     */
    var closePriceEMA10: Double = 0.0

    /**
     * The 20 ema value on close
     */
    var closePriceEMA20: Double = 0.0

    /**
     * The 5 ema value on close
     */
    var closePriceEMA5: Double = 0.0

    /**
     * The 50 ema value on close
     */
    var closePriceEMA50: Double = 0.0

    /**
     * Is the stock in an Uptrend or Downtrend
     */
    var trend: String? = null

    /**
     * The date of the last buy signal
     */
    var lastBuySignal: LocalDate? = null

    /**
     * The date of the last sell signal
     */
    var lastSellSignal: LocalDate? = null

    /**
     * Current SPY Buy/Sell signal.
     */
    var spySignal: String? = null

    /**
     * SPY is in an uptrend
     */
    var spyInUptrend: Boolean = false

    /**
     * Market is in an uptrend
     */
    var marketIsInUptrend: Boolean = false

    var previousQuoteDate: LocalDate? = null

    /**
     * The average true range for this quote.
     */
    var atr: Double = 0.0

    constructor()

    constructor(
        symbol: String,
        date: LocalDate?,
        closePrice: Double,
        openPrice: Double,
        heatmap: Double,
        previousHeatmap: Double,
        sectorHeatmap: Double,
        previousSectorHeatmap: Double,
        sectorIsInUptrend: Boolean,
        signal: String?,
        closePriceEMA10: Double,
        closePriceEMA20: Double,
        closePriceEMA5: Double,
        closePriceEMA50: Double,
        trend: String?,
        lastBuySignal: LocalDate?,
        lastSellSignal: LocalDate?,
        spySignal: String?,
        spyIsInUptrend: Boolean,
        marketIsInUptrend: Boolean,
        previousQuoteDate: LocalDate?,
        atr: Double
    ) {
        this.symbol = symbol
        this.date = date
        this.closePrice = closePrice
        this.openPrice = openPrice
        this.heatmap = heatmap
        this.sectorHeatmap = sectorHeatmap
        this.previousHeatmap = previousHeatmap
        this.previousSectorHeatmap = previousSectorHeatmap
        this.sectorIsInUptrend = sectorIsInUptrend
        this.signal = signal
        this.closePriceEMA10 = closePriceEMA10
        this.closePriceEMA20 = closePriceEMA20
        this.closePriceEMA5 = closePriceEMA5
        this.closePriceEMA50 = closePriceEMA50
        this.trend = trend
        this.lastBuySignal = lastBuySignal
        this.lastSellSignal = lastSellSignal
        this.spySignal = spySignal
        this.spyInUptrend = spyIsInUptrend
        this.marketIsInUptrend = marketIsInUptrend
        this.previousQuoteDate = previousQuoteDate
        this.atr = atr
    }

    fun isInUptrend() = "Uptrend" == trend

    /**
     *
     * true if this quotes heatmap value is greater than previous quotes heatmap value.
     */
    fun isGettingGreedier() = heatmap > previousHeatmap

    /**
     * True if previous heatmap value is greater than current.
     */
    fun isGettingMoreFearful() = heatmap < previousHeatmap

    /**
     * @return true if the sector is getting greedier
     */
    fun sectorIsGettingGreedier() = sectorHeatmap > previousSectorHeatmap

    /**
     *
     * @return true if the sector the stock belongs to is in an uptrend.
     */
    fun sectorIsInUptrend() = sectorIsInUptrend

    /**
     * @return true if SPY has a Buy signal.
     */
    fun hasSpyBuySignal() = "Buy" == spySignal

    /**
     * @return true if SPY is in an uptrend.
     */
    fun isSpyInUptrend() = spyInUptrend

    /**
     * @return true if overall stock market is in an uptrend
     */
    fun isMarketInUptrend() = marketIsInUptrend

    /**
     * @return true if it has a buy signal and the signal is within the last 2 days of the quote and the signal is more recent than any sell signal
     */
    fun hasCurrentBuySignal(): Boolean {
        return lastBuySignal != null &&
                // The buy signal is within the last 2 days of the quote
                lastBuySignal?.isAfter(date!!.minusDays(2)) == true &&
                // and buy signal is after sell signal
                (lastSellSignal == null || lastBuySignal?.isAfter(lastSellSignal) == true)
    }

    /**
     * Get half ATR value
     */
    fun getClosePriceMinusHalfAtr() = closePrice - (atr / 2)

    /**
     * true if the quote has a sell signal
     */
    fun hasSellSignal() = signal == "Sell"

    override fun toString() = "Symbol: $symbol heatmap: $heatmap previous heatmap: $previousHeatmap"
}
