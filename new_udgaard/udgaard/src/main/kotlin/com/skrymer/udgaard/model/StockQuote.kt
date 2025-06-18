package com.skrymer.udgaard.model

import java.lang.Double
import java.time.LocalDate
import kotlin.Boolean
import kotlin.String

/**
 * A stock quote.
 */
class StockQuote {
    var symbol: String? = null
        private set

    /**
     * The date when the stock quote was taken.
     */
    var date: LocalDate? = null
        private set

    /**
     * The stock price at close.
     */
    var closePrice: Double? = null
        private set

    /**
     * The stock price at open.
     */
    var openPrice: Double? = null
        private set

    /**
     * The heatmap value of the stock.
     *
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    private var heatmap: Double? = null

    /**
     * The previous heatmap value of the stock.
     *
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    private var previousHeatmap: Double? = null

    /**
     * The heatmap value of the sector the stock belongs to.
     *
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    private var sectorHeatmap: Double? = null

    /**
     * The previous heatmap value of the sector the stock belongs to.
     *
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    private var previousSectorHeatmap: Double? = null

    /**
     * true if the sector the stock belongs to is in an uptrend.
     */
    private var sectorIsInUptrend = false

    /**
     * The ovtlyr Buy/Sell signal or null if neither.
     */
    var signal: String? = null
        private set

    /**
     * The 10 ema value on close
     */
    var closePrice_EMA10: Double? = null
        private set

    /**
     * The 20 ema value on close
     */
    var closePrice_EMA20: Double? = null
        private set

    /**
     * The 5 ema value on close
     */
    var closePrice_EMA5: Double? = null
        private set

    /**
     * The 50 ema value on close
     */
    var closePrice_EMA50: Double? = null
        private set

    /**
     * Is the stock in an Uptrend or Downtrend
     */
    var trend: String? = null
        private set

    /**
     * The date of the last buy signal
     */
    var lastBuySignal: LocalDate? = null
        private set

    /**
     * The date of the last sell signal
     */
    var lastSellSignal: LocalDate? = null
        private set

    /**
     * Current SPY Buy/Sell signal.
     */
    private var spySignal: String? = null

    /**
     * SPY is in an uptrend
     */
    var isSpyInUptrend: Boolean = false
        private set

    /**
     * Market is in an uptrend
     */
    private var marketIsInUptrend = false

    constructor()

    constructor(
        symbol: String?,
        date: LocalDate?,
        closePrice: Double?,
        openPrice: Double?,
        heatmap: Double,
        previousHeatmap: Double,
        sectorHeatmap: Double,
        previousSectorHeatmap: Double,
        sectorIsInUptrend: Boolean,
        signal: String?,
        closePrice_EMA10: Double?,
        closePrice_EMA20: Double?,
        closePrice_EMA5: Double?,
        closePrice_EMA50: Double?,
        trend: String?,
        lastBuySignal: LocalDate?,
        lastSellSignal: LocalDate?,
        spySignal: String?,
        spyIsInUptrend: Boolean,
        marketIsInUptrend: Boolean
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
        this.closePrice_EMA10 = closePrice_EMA10
        this.closePrice_EMA20 = closePrice_EMA20
        this.closePrice_EMA5 = closePrice_EMA5
        this.closePrice_EMA50 = closePrice_EMA50
        this.trend = trend
        this.lastBuySignal = lastBuySignal
        this.lastSellSignal = lastSellSignal
        this.spySignal = spySignal
        this.isSpyInUptrend = spyIsInUptrend
        this.marketIsInUptrend = marketIsInUptrend
    }

    fun getHeatmap(): Double {
        return heatmap!!
    }

    fun getPreviousHeatmap(): Double {
        return previousHeatmap!!
    }

    fun getSectorHeatmap(): Double {
        return sectorHeatmap!!
    }

    val isInUptrend: Boolean
        get() = "Uptrend" == trend

    fun hasBuySignal(): Boolean {
        return "Buy" == signal
    }

    val isGettingGreeder: Boolean
        /**
         *
         * @return true if this quotes heatmap value is greater than previous quotes heatmap value.
         */
        get() = Double.compare(heatmap!!, previousHeatmap!!) > 0

    fun sectorIsGettingGreeder(): Boolean {
        return Double.compare(sectorHeatmap!!, previousSectorHeatmap!!) > 0
    }

    /**
     *
     * @return true if the sector the stock belongs to is in an uptrend.
     */
    fun sectorIsInUptrend(): Boolean {
        return sectorIsInUptrend
    }

    /**
     *
     * @return true if SPY has a Buy signal.
     */
    fun hasSpyBuySignal(): Boolean {
        return "Buy" == spySignal
    }

    fun marketIsInUptrend(): Boolean {
        return marketIsInUptrend
    }

    override fun toString(): String {
        return "Symbol: %s".formatted(symbol)
    }
}
