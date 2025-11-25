package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 * A single breadth data point for a specific date.
 * Can represent either market breadth (all stocks) or sector breadth (stocks in a sector).
 */
class BreadthQuote {
    /**
     * The symbol identifier (market or sector)
     */
    var symbol: String? = null

    /**
     * The quote day
     */
    var quoteDate: LocalDate? = null

    /**
     * Number of stocks with a buy signal.
     */
    var numberOfStocksWithABuySignal: Int = 0

    /**
     * Number of stocks with a sell signal.
     */
    var numberOfStocksWithASellSignal: Int = 0

    /**
     * Number of stocks in an uptrend - 10ema > 20ema and price over 50ema.
     */
    var numberOfStocksInUptrend: Int = 0

    /**
     * Number of stocks that are not in an up/down trend.
     */
    var numberOfStocksInNeutral: Int = 0

    /**
     * Number of stocks in a downtrend - 10ema < 20ema and price under 50ema.
     */
    var numberOfStocksInDowntrend: Int = 0

    /**
     * The percentage of stocks with a buy signal.
     */
    private var bullStocksPercentage: Double = 0.0

    /**
     * The 5 EMA percentage value of stocks with a buy signal.
     */
    var ema_5: Double = 0.0

    /**
     * The 10 EMA percentage value of stocks with a buy signal.
     */
    var ema_10: Double = 0.0

    /**
     * The 20 EMA percentage value of stocks with a buy signal.
     */
    var ema_20: Double = 0.0
        private set

    /**
     * The 50 EMA percentage value of stocks with a buy signal.
     */
    var ema_50: Double = 0.0
        private set

    /**
     * The heatmap value 0-100. 0 being max fear and 100 being max greed.
     */
    var heatmap: Double = 0.0

    /**
     * The previous heatmap value.
     */
    var previousHeatmap: Double = 0.0

    /**
     * Donchian upper band - the max value over the past 5 days.
     */
    var donchianUpperBand: Double = 0.0

    /**
     * Previous Donchian upper band value.
     */
    var previousDonchianUpperBand: Double = 0.0

    /**
     * Donchian lower band - the min value over the past 5 days.
     */
    var donchianLowerBand: Double = 0.0

    /**
     * Previous Donchian lower band value.
     */
    var previousDonchianLowerBand: Double = 0.0

    /**
     * Donkey channel score (-2 to +2).
     */
    var donkeyChannelScore: Int = 0

    constructor(
        symbol: String?,
        quoteDate: LocalDate?,
        numberOfStocksWithABuySignal: Int,
        numberOfStocksWithASellSignal: Int,
        numberOfStocksInUptrend: Int,
        numberOfStocksInNeutral: Int,
        numberOfStocksInDowntrend: Int,
        ema_5: Double,
        ema_10: Double,
        ema_20: Double,
        ema_50: Double,
        bullStocksPercentage: Double,
        donchianUpperBand: Double,
        previousDonchianUpperBand: Double,
        donchianLowerBand: Double,
        previousDonchianLowerBand: Double,
        heatmap: Double,
        previousHeatmap: Double,
        donkeyChannelScore: Int
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
        this.donchianUpperBand = donchianUpperBand
        this.previousDonchianUpperBand = previousDonchianUpperBand
        this.donchianLowerBand = donchianLowerBand
        this.previousDonchianLowerBand = previousDonchianLowerBand
        this.heatmap = heatmap
        this.previousHeatmap = previousHeatmap
        this.donkeyChannelScore = donkeyChannelScore
    }

    /**
     * @return true if percentage of bullish stocks are higher than the 10ema.
     */
    fun isInUptrend() = bullStocksPercentage > ema_10
}
