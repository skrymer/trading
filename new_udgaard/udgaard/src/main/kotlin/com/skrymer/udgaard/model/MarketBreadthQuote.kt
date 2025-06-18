package com.skrymer.udgaard.model

import java.lang.Double
import java.time.LocalDate
import kotlin.Boolean
import kotlin.Int
import kotlin.String

class MarketBreadthQuote {
    var symbol: String? = null
        private set
    var quoteDate: LocalDate? = null
        private set

    /**
     * Number of stocks in the market with a buys signal.
     */
    var numberOfStocksWithABuySignal: Int? = null
        private set

    /**
     * Number of stocks in the market with a sell signal
     */
    var numberOfStocksWithASellSignal: Int? = null
        private set

    /**
     * Number of stocks in the market in an uptrend - 10ema > 20ema and price over 50ema.
     */
    var numberOfStocksInUptrend: Int? = null
        private set

    /**
     * Number of stocks in the market that are not in an up/down trend.
     */
    var numberOfStocksInNeutral: Int? = null
        private set

    /**
     * Number of stocks in the market in an downtrend - 10ema < 20ema and price under 50ema.
     */
    var numberOfStocksInDowntrend: Int? = null
        private set

    /**
     * The percentage of stocks with a buy signal in this market.
     */
    private var bullStocksPercentage: Double? = null

    /**
     * The 5 ema percentage value of stocks with a buy signal in this market.
     */
    var ema_5: Double? = null
        private set

    /**
     * The 10 ema percentage value of stocks with a buy signal in this market.
     */
    private var ema_10: Double? = null

    /**
     * The 20 ema percentage value of stocks with a buy signal in this market.
     */
    var ema_20: Double? = null
        private set

    /**
     * The 50 ema percentage value of stocks with a buy signal in this market.
     */
    var ema_50: Double? = null
        private set

    constructor()

    constructor(
        symbol: String?,
        quoteDate: LocalDate?,
        numberOfStocksWithABuySignal: Int?,
        numberOfStocksWithASellSignal: Int?,
        numberOfStocksInUptrend: Int?,
        numberOfStocksInNeutral: Int?,
        numberOfStocksInDowntrend: Int?,
        ema_5: Double?,
        ema_10: Double,
        ema_20: Double?,
        ema_50: Double?,
        bullStocksPercentage: Double
    ) {
        this.symbol = symbol
        this.quoteDate = quoteDate
        this.numberOfStocksWithABuySignal = numberOfStocksWithABuySignal
        this.numberOfStocksWithASellSignal = numberOfStocksWithASellSignal
        this.numberOfStocksInUptrend = numberOfStocksInUptrend
        this.numberOfStocksInNeutral = numberOfStocksInNeutral
        this.numberOfStocksInDowntrend = numberOfStocksInDowntrend
        this.ema_5 = ema_5
        this.ema_10 = ema_10
        this.ema_20 = ema_20
        this.ema_50 = ema_50
        this.bullStocksPercentage = bullStocksPercentage
    }

    fun getEma_10(): Double {
        return ema_10!!
    }

    fun getBullStocksPercentage(): Double {
        return bullStocksPercentage!!
    }

    val isInUptrend: Boolean
        /**
         *
         * @return true if percentage of bullish stocks are higher than the 10ema.
         */
        get() = Double.compare(bullStocksPercentage!!, ema_10!!) > 0
}
