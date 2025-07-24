package com.skrymer.udgaard.model

import java.time.LocalDate

class MarketBreadthQuote {
    var symbol: String? = null
    var quoteDate: LocalDate? = null

    /**
     * Number of stocks in the market with a buys signal.
     */
    var numberOfStocksWithABuySignal: Int = 0
    /**
     * Number of stocks in the market with a sell signal
     */
    var numberOfStocksWithASellSignal: Int = 0
    /**
     * Number of stocks in the market in an uptrend - 10ema > 20ema and price over 50ema.
     */
    var numberOfStocksInUptrend: Int = 0
    /**
     * Number of stocks in the market that are not in an up/down trend.
     */
    var numberOfStocksInNeutral: Int = 0
    /**
     * Number of stocks in the market in an downtrend - 10ema < 20ema and price under 50ema.
     */
    var numberOfStocksInDowntrend: Int = 0
    /**
     * The percentage of stocks with a buy signal in this market.
     */
    private var bullStocksPercentage: Double = 0.0

    /**
     * The 5 ema percentage value of stocks with a buy signal in this market.
     */
    var ema_5: Double = 0.0

    /**
     * The 10 ema percentage value of stocks with a buy signal in this market.
     */
    var ema_10: Double = 0.0

    /**
     * The 20 ema percentage value of stocks with a buy signal in this market.
     */
    var ema_20: Double = 0.0
        private set

    /**
     * The 50 ema percentage value of stocks with a buy signal in this market.
     */
    var ema_50: Double = 0.0
        private set

    /**
     * Donchian upper band the max value over the past 5 days.
     */
    var donchianUpperBand: Double = 0.0

    /**
     * Previous donchian upper band value.
     */
    var previousDonchianUpperBand: Double = 0.0

    /**
     * Donchian lower band the min value over the past 5 days.
     */
    var donchianLowerBand: Double = 0.0

    /**
     * Previous donchian lower band value.
     */
    var previousDonchianLowerBand: Double = 0.0

    constructor()

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
        previousDonchianLowerBand: Double
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
    }

    /**
     *
     * @return true if percentage of bullish stocks are higher than the 10ema.
     */
    fun isInUptrend() = bullStocksPercentage > ema_10
}
