package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.MarketBreadthQuote
import com.skrymer.udgaard.model.StockQuote
import java.time.LocalDate

/**
 * Ovtlyr payload:
 *
 * {
 * "stockSymbol": "NVDA",
 * "quotedate": "2025-06-05T00:00:00",
 * "quotedateStr": "2025-06-05",
 * "quotedateStrShow": "Jun 05, 2025",
 * "open": 142.17,
 * "low": 138.83,
 * "high": 144,
 * "close": 139.99,
 * "openStr": "$142.17",
 * "lowStr": "$138.83",
 * "highStr": "$144.00",
 * "closeStr": "$139.99",
 * "oscillator": 63.41708828753126,
 * "final_region": 1,
 * "final_region_combined": 1,
 * "heatmap": 13.417088287531257,
 * "heatMap_str": "G13",
 * "final_calls": null,
 * "tooltip": "Uptrend",
 * "minClosePrice": 4.89323,
 * "maxClosePrice": 149.43,
 * "maxClosePriceWithAdd5Perc": 223.29324900000003,
 * "gics_IndexTracked": null,
 * "gics_ExpenseRation": null,
 * "gics_sector": "Information Technology",
 * "sectorSymbol": "XLK",
 * "total_ClosePrice": 36826.9612,
 * "bull_Total": 140,
 * "bear_Total": 195,
 * "bull_per": 41.7910447761194,
 * "bull_EMA_5": 37.1546900350677,
 * "bull_EMA_10": 40.0799899269599,
 * "bull_EMA_20": 47.383792175294,
 * "bull_EMA_50": 47.4596137058766,
 * "lower": 25,
 * "midpoint": 50,
 * "upper": 75,
 * "uptrend": 222,
 * "uptrend_DifferenceWithPrevious": -5,
 * "neutral": 59,
 * "downtrend": 54,
 * "downtrend_DifferenceWithPrevious": 3,
 * "total": 335,
 * "bar_min": 4.50157428993044,
 * "bar_max": 195.933795071121,
 * "closePrice_EMA5": 139.448852098839,
 * "closePrice_EMA10": 137.267299816846,
 * "closePrice_EMA20": 132.438548429477,
 * "closePrice_EMA50": 124.876876126579,
 * "bull_per_spy": 36.7032967032967,
 * "bull_per_fullStock": 40.2183039462637,
 * "net_weighted_FG": 12.1244495236215,
 * "net_weighted_FG_display": 62.1244495236215,
 * "green_display_value": 12.1244495236215,
 * "red_display_value": 0,
 * "masterKey_value": null
 * }
 */
class OvtlyrStockQuote {
    @JsonProperty("stockSymbol")
    private val symbol: String? = null

    /**
     * The date when the stock quote was taken.
     */
    @JsonProperty("quotedate")
    private val date: LocalDate? = null

    /**
     * The stock price at close.
     */
    @JsonProperty("close")
    private val closePrice: Double = 0.0

    /**
     * The stock price at open.
     */
    @JsonProperty("open")
    private val openPrice: Double = 0.0

    /**
     * The heatmap of the stock.
     *
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    @JsonProperty("oscillator")
    private val heatmap: Double = 0.0

    /**
     * The heatmap of the sector the stock belongs to.
     *
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    @JsonProperty("net_weighted_FG_display")
    private val sectorHeatmap: Double = 0.0

    /**
     * The ovtlyr buy/sell signal or null if neither.
     */
    @JsonProperty("final_calls")
    val signal: String? = null

    /**
     * The 10 ema value on close
     */
    private val closePrice_EMA10: Double = 0.0

    /**
     * The 20 ema value on close
     */
    private val closePrice_EMA20: Double = 0.0

    /**
     * The 5 ema value on close
     */
    private val closePrice_EMA5: Double = 0.0

    /**
     * The 50 ema value on close
     */
    private val closePrice_EMA50: Double = 0.0

    /**
     * Is the stock in an uptrend or downtrend
     */
    @JsonProperty("tooltip")
    val trend: String? = null

    /**
     * The symbol of the sector the stock belongs to. E.g. NVDA belongs to XLK (THE TECHNOLOGY SELECT SECTOR SPDR FUND)
     */
    val sectorSymbol: String? = null

    fun toModel(
        stock: OvtlyrStockInformation,
        marketBreadthQuote: MarketBreadthQuote?,
        sectorMarketBreadthQuote: MarketBreadthQuote?,
        spy: OvtlyrStockInformation
    ): StockQuote {
        val previousQuote = stock.getQuotes().filter { this.date!!.isAfter(it!!.date) }[0]
        val previousHeatmap = previousQuote?.heatmap ?: 0.0
        val previousSectorHeatmap = previousQuote?.sectorHeatmap ?: 0.0
        val sectorIsInUptrend = sectorMarketBreadthQuote?.isInUptrend() ?: false
        val lastBuySignal = stock.getLastBuySignal(date!!)
        val lastSellSignal = stock.getLastSellSignal(date!!)
        val spySignal = spy.getCurrentSignalFrom(date!!)
        val spyIsInUptrend = spy.getQuotes().filter { it?.date == date }[0]?.isInUptrend ?: false
        val marketIsInUptrend = marketBreadthQuote?.isInUptrend() ?: false

        return StockQuote(
            this.symbol,
            this.date,
            this.closePrice,
            this.openPrice,
            this.heatmap,
            previousHeatmap,
            this.sectorHeatmap,
            previousSectorHeatmap,
            sectorIsInUptrend,
            this.signal,
            this.closePrice_EMA10,
            this.closePrice_EMA20,
            this.closePrice_EMA5,
            this.closePrice_EMA50,
            this.trend,
            lastBuySignal,
            lastSellSignal,
            spySignal,
            spyIsInUptrend,
            marketIsInUptrend
        )
    }

    fun getClosePrice(): Double {
        return closePrice
    }

    fun getHeatmap(): Double {
        return heatmap
    }

    fun getClosePrice_EMA20(): Double {
        return closePrice_EMA20!!
    }

    fun getClosePrice_EMA10(): Double {
        return closePrice_EMA10!!
    }

    fun getClosePrice_EMA5(): Double {
        return closePrice_EMA5!!
    }

    fun getClosePrice_EMA50(): Double {
        return closePrice_EMA50!!
    }

    fun getDate(): LocalDate {
        return date!!
    }

    fun getOpenPrice(): Double {
        return openPrice!!
    }

    fun hasBuySignal(): Boolean {
        return "Buy" == signal
    }

    fun hasSellSignal(): Boolean {
        return "Sell" == signal
    }

    val isInUptrend: Boolean
        get() = "Uptrend" == trend

    val isBullish: Boolean
        /**
         *
         * @return true if has buy signal and is in a uptrend,
         */
        get() = hasBuySignal() && this.isInUptrend

    override fun toString() =
        "Symbol: ${symbol} Signal: ${signal} Trend: ${trend}"
}
