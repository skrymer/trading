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
    private val openPrice: Double? = null

    /**
     *  The low
     */
    @JsonProperty("low")
    private val low: Double = 0.0

    /**
     *  The high
     */
    @JsonProperty("high")
    private val high: Double = 0.0

    /**
     * The heatmap of the stock.
     *
     * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
     */
    @JsonProperty("oscillator")
    private val heatmap: Double? = null

    /**
     * The heatmap of the sector the stock belongs to.
     *
     * A value between 0 and 100, 0 being max fear and 100 max greed.
     */
    @JsonProperty("net_weighted_FG_display")
    private val sectorHeatmap: Double? = null

    /**
     * The ovtlyr buy/sell signal or null if neither.
     */
    @JsonProperty("final_calls")
    val signal: String? = null

    /**
     * The 10 ema value on close
     */
    @JsonProperty("closePrice_EMA10")
    private val closePriceEMA10: Double? = null

    /**
     * The 20 ema value on close
     */
    @JsonProperty("closePrice_EMA20")
    private val closePriceEMA20: Double? = null

    /**
     * The 5 ema value on close
     */
    @JsonProperty("closePrice_EMA5")
    private val closePriceEMA5: Double? = null

    /**
     * The 50 ema value on close
     */
    @JsonProperty("closePrice_EMA50")
    private val closePriceEMA50: Double? = null

    /**
     * Is the stock in an uptrend or downtrend
     */
    @JsonProperty("tooltip")
    val trend: String? = null

    /**
     * The symbol of the sector the stock belongs to. E.g. NVDA belongs to XLK (THE TECHNOLOGY SELECT SECTOR SPDR FUND)
     */
    val sectorSymbol: String? = null

    /**
     * The number of stocks in the sector that are in a downtrend.
     */
    @JsonProperty("downtrend")
    val sectorDowntrend: Int = 0

    /**
     * The number of stocks in the sector that are in an uptrend.
     */
    @JsonProperty("uptrend")
    val sectorUptrend: Int = 0

    /**
     * Percentage of stocks in an uptrend for the sector.
     */
    @JsonProperty("bull_per")
    val sectorBullPercentage: Double = 0.0

    fun toModel(
        stock: OvtlyrStockInformation,
        marketBreadthQuote: MarketBreadthQuote?,
        sectorMarketBreadthQuote: MarketBreadthQuote?,
        spy: OvtlyrStockInformation
    ): StockQuote {
        val previousQuote = stock.getPreviousQuote(this)
        val previousPreviousQuote = if(previousQuote != null) stock.getPreviousQuote(previousQuote) else null
        val sectorIsInUptrend = sectorMarketBreadthQuote?.isInUptrend() ?: false
        val lastBuySignal = stock.getLastBuySignal(date!!)
        val lastSellSignal = stock.getLastSellSignal(date)
        val spySignal = spy.getCurrentSignalFrom(date)
        val spyIsInUptrend = spy.getQuoteForDate(date)?.isInUptrend ?: false
        val marketIsInUptrend = marketBreadthQuote?.isInUptrend() ?: false
        val atr = calculateATR(stock)

        return StockQuote(
            symbol = this.symbol ?: "",
            date = this.date,
            closePrice = this.closePrice,
            openPrice = this.openPrice ?: 0.0,
            heatmap = previousQuote?.heatmap ?: 0.0,
            previousHeatmap = previousPreviousQuote?.heatmap ?: 0.0,
            sectorHeatmap = previousQuote?.sectorHeatmap ?: 0.0,
            previousSectorHeatmap = previousPreviousQuote?.sectorHeatmap ?: 0.0,
            sectorIsInUptrend = sectorIsInUptrend,
            signal = this.signal,
            closePriceEMA10 = this.closePriceEMA10 ?: 0.0,
            closePriceEMA20 = this.closePriceEMA20 ?: 0.0,
            closePriceEMA5 = this.closePriceEMA5 ?: 0.0,
            closePriceEMA50 = this.closePriceEMA50 ?: 0.0,
            trend = this.trend,
            lastBuySignal = lastBuySignal,
            lastSellSignal = lastSellSignal,
            spySignal = spySignal,
            spyIsInUptrend = spyIsInUptrend,
            marketIsInUptrend = marketIsInUptrend,
            previousQuoteDate = previousQuote?.getDate(),
            atr = atr,
            sectorStocksInDowntrend = sectorDowntrend,
            sectorStocksInUptrend = sectorUptrend,
            sectorBullPercentage = previousQuote?.sectorBullPercentage ?: 0.0
        )
    }

    fun getDate(): LocalDate {
        return date!!
    }

    fun hasBuySignal(): Boolean {
        return "Buy" == signal
    }

    fun hasSellSignal(): Boolean {
        return "Sell" == signal
    }

    val isInUptrend: Boolean
        get() = "Uptrend" == trend

    override fun toString() =
        "Symbol: $symbol Signal: $signal Trend: $trend Date: $date"


    /**
     * Calculate the ATR (Average True Range)
     *
     * ATR = (TR1 + TR2...Tn) / n where n is the number of periods, default 14.
     *
     * @param stock - the stock
     * @param nPeriods - the number of periods, default 14
     */
    private fun calculateATR(stock: OvtlyrStockInformation, nPeriods: Int = 14): Double {
        val previousQuotes = stock.getQuotes()
            .sortedByDescending { it?.getDate() }
            .filter { it?.getDate()?.isBefore(date) == true || it?.getDate()?.equals(date) == true }

        val toIndex = if(previousQuotes.size < nPeriods) previousQuotes.size else nPeriods
        val atrCalculationQuotes = previousQuotes.subList(0, toIndex)

        return atrCalculationQuotes.mapNotNull { it?.calculateTR(stock) }.sum() / atrCalculationQuotes.size
    }

    /**
     *  Calculate the TR (True Range)
     *
     *  TR = max[(High - Low), (High - Previous Close), (Low - Previous Close)]
     */
    fun calculateTR(stock: OvtlyrStockInformation): Double {
        val previousQuote = stock.getPreviousQuote(this)
        val highLowDiff = high - low
        val highPreviousCloseDiff = high - (previousQuote?.closePrice ?: 0.0)
        val lowPreviousCloseDiff = low - (previousQuote?.closePrice ?: 0.0)
        return highLowDiff.coerceAtLeast(highPreviousCloseDiff.coerceAtLeast(lowPreviousCloseDiff))
    }
}
