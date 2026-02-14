package com.skrymer.udgaard.data.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.data.model.Breadth
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
  val low: Double = 0.0

  /**
   *  The high
   */
  @JsonProperty("high")
  val high: Double = 0.0

  /**
   * The heatmap of the stock.
   *
   * A value bewtween 0 and 100, 0 being max fear and 100 max greed.
   */
  @JsonProperty("oscillator")
  val heatmap: Double? = null

  /**
   * The heatmap of the sector the stock belongs to.
   *
   * A value between 0 and 100, 0 being max fear and 100 max greed.
   */
  @JsonProperty("net_weighted_FG_display")
  val sectorHeatmap: Double? = null

  /**
   * The ovtlyr buy/sell signal or null if neither.
   */
  @JsonProperty("final_calls")
  val signal: String? = null

  /**
   * The 10 ema value on close
   */
  @JsonProperty("closePrice_EMA10")
  val closePriceEMA10: Double? = null

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

  fun getDate(): LocalDate = date!!

  fun getSymbol(): String? = symbol

  fun getClosePrice(): Double = closePrice

  fun getOpenPrice(): Double? = openPrice

  fun getClosePriceEMA5(): Double? = closePriceEMA5

  fun getClosePriceEMA20(): Double? = closePriceEMA20

  fun getClosePriceEMA50(): Double? = closePriceEMA50

  fun hasBuySignal(): Boolean = "Buy" == signal

  fun hasSellSignal(): Boolean = "Sell" == signal

  val isInUptrend: Boolean
    get() = "Uptrend" == trend

  override fun toString() = "Symbol: $symbol Signal: $signal Trend: $trend Date: $date"

  /**
   * Calculate the ATR (Average True Range)
   *
   * ATR = (TR1 + TR2...Tn) / n where n is the number of periods, default 14.
   *
   * @param stock - the stock
   * @param nPeriods - the number of periods, default 14
   */
  private fun calculateATR(
    stock: OvtlyrStockInformation,
    nPeriods: Int = 14,
  ): Double {
    val previousQuotes =
      stock
        .getQuotes()
        .sortedByDescending { it?.getDate() }
        .filter { it?.getDate()?.isBefore(date) == true || it?.getDate()?.equals(date) == true }

    val toIndex = if (previousQuotes.size < nPeriods) previousQuotes.size else nPeriods
    val atrCalculationQuotes = previousQuotes.subList(0, toIndex)

    return atrCalculationQuotes.mapNotNull { it?.calculateTR(stock) }.sum() / atrCalculationQuotes.size
  }

  /**
   *  Calculate the TR (True Range)
   *
   *  TR = max[(High - Low), abs(High - Previous Close), abs(Low - Previous Close)]
   */
  fun calculateTR(stock: OvtlyrStockInformation): Double {
    val previousQuote = stock.getPreviousQuote(this)
    val previousClose = previousQuote?.closePrice ?: high // If no previous quote, use high as fallback

    val highLowDiff = high - low
    val highPreviousCloseDiff = kotlin.math.abs(high - previousClose)
    val lowPreviousCloseDiff = kotlin.math.abs(low - previousClose)

    return highLowDiff.coerceAtLeast(highPreviousCloseDiff.coerceAtLeast(lowPreviousCloseDiff))
  }

  /**
   * Calculate the doncian upper band for the given stock.
   */
  fun calculateDonchianUpperBand(
    stock: OvtlyrStockInformation,
    periods: Int = 5,
  ) = stock
    .getPreviousQuotes(this, periods)
    .maxOfOrNull { it.closePrice } ?: 0.0

  /**
   * Calculate the donchian upper band for the number of stocks in an uptrend.
   */
  fun calculateDonchianUpperBandMarket(
    market: Breadth?,
    periods: Int = 4,
  ) = market
    ?.getPreviousQuotes(this.date, periods)
    ?.maxOfOrNull { it.numberOfStocksInUptrend.toDouble() } ?: 0.0

  /**
   * Calculate the donchian lower band for the number of stocks in an uptrend.
   */
  fun calculateDonchianLowerBandMarket(
    market: Breadth?,
    periods: Int = 4,
  ) = market
    ?.getPreviousQuotes(this.date, periods)
    ?.minOfOrNull { it.numberOfStocksInUptrend.toDouble() } ?: 0.0

  /**
   * Calculate EMA for the stock's close price
   * @param stock - the stock information
   * @param period - the EMA period (e.g., 5, 10, 20, 50)
   * @return the calculated EMA value
   */
  private fun calculateStockEMA(
    stock: OvtlyrStockInformation,
    period: Int,
  ): Double {
    val prices =
      stock
        .getQuotes()
        .sortedBy { it?.getDate() }
        .filter { it?.getDate()?.isBefore(date) == true || it?.getDate()?.equals(date) == true }
        .mapNotNull { it?.closePrice }

    if (prices.size < period) return 0.0

    val multiplier = 2.0 / (period + 1)
    var ema = prices.take(period).average() // Start with SMA

    for (i in period until prices.size) {
      ema = (prices[i] - ema) * multiplier + ema
    }

    return ema
  }

  /**
   * Calculate 200-day EMA from SPY price history
   */
  fun calculateEMA200(spy: OvtlyrStockInformation): Double {
    val prices =
      spy
        .getQuotes()
        .sortedBy { it?.getDate() }
        .filter { it?.getDate()?.isBefore(date) == true || it?.getDate()?.equals(date) == true }
        .mapNotNull { it?.closePrice }

    if (prices.size < 200) return 0.0

    val multiplier = 2.0 / (200 + 1)
    var ema = prices.take(200).average() // Start with SMA

    for (i in 200 until prices.size) {
      ema = (prices[i] - ema) * multiplier + ema
    }

    return ema
  }

  /**
   * Calculate 200-day SMA from SPY price history
   */
  fun calculateSMA200(spy: OvtlyrStockInformation): Double {
    val prices =
      spy
        .getQuotes()
        .sortedBy { it?.getDate() }
        .filter { it?.getDate()?.isBefore(date) == true || it?.getDate()?.equals(date) == true }
        .mapNotNull { it?.closePrice }

    if (prices.size < 200) return 0.0
    return prices.takeLast(200).average()
  }

  /**
   * Calculate 50-day EMA from SPY price history
   */
  fun calculateEMA50(spy: OvtlyrStockInformation): Double {
    val prices =
      spy
        .getQuotes()
        .sortedBy { it?.getDate() }
        .filter { it?.getDate()?.isBefore(date) == true || it?.getDate()?.equals(date) == true }
        .mapNotNull { it?.closePrice }

    if (prices.size < 50) return 0.0

    val multiplier = 2.0 / (50 + 1)
    var ema = prices.take(50).average() // Start with SMA

    for (i in 50 until prices.size) {
      ema = (prices[i] - ema) * multiplier + ema
    }

    return ema
  }

  /**
   * Count consecutive days SPY has been above 200-day SMA
   */
  fun calculateDaysAbove200SMA(spy: OvtlyrStockInformation): Int {
    val quotes =
      spy
        .getQuotes()
        .sortedByDescending { it?.getDate() }
        .filter { it?.getDate()?.isBefore(date) == true || it?.getDate()?.equals(date) == true }

    if (quotes.size < 200) return 0

    var count = 0
    for (i in quotes.indices) {
      val quote = quotes[i] ?: continue

      // Calculate SMA200 for this point in time
      val pricesUpToThisPoint =
        quotes
          .subList(i, quotes.size)
          .mapNotNull { it?.closePrice }
          .reversed()

      if (pricesUpToThisPoint.size < 200) break

      val sma200 = pricesUpToThisPoint.takeLast(200).average()

      if (quote.closePrice > sma200) {
        count++
      } else {
        break
      }
    }

    return count
  }

  /**
   * Calculate market breadth - percentage of stocks advancing (above their uptrend status)
   * Uses the existing market breadth data
   */
  fun calculateMarketAdvancingPercent(marketBreadth: Breadth?): Double {
    if (marketBreadth == null) return 0.0

    val breadthQuote = marketBreadth.getQuoteForDate(date) ?: return 0.0

    val total = breadthQuote.numberOfStocksInUptrend + breadthQuote.numberOfStocksInDowntrend

    if (total == 0) return 0.0

    return (breadthQuote.numberOfStocksInUptrend.toDouble() / total) * 100.0
  }

  override fun equals(other: Any?): Boolean =
    if (other !is OvtlyrStockQuote) {
      false
    } else {
      other.date?.equals(date) == true && other.symbol.equals(symbol)
    }

  override fun hashCode(): Int {
    var result = closePrice.hashCode()
    result = 31 * result + (openPrice?.hashCode() ?: 0)
    result = 31 * result + low.hashCode()
    result = 31 * result + high.hashCode()
    result = 31 * result + (heatmap?.hashCode() ?: 0)
    result = 31 * result + (sectorHeatmap?.hashCode() ?: 0)
    result = 31 * result + (closePriceEMA10?.hashCode() ?: 0)
    result = 31 * result + (closePriceEMA20?.hashCode() ?: 0)
    result = 31 * result + (closePriceEMA5?.hashCode() ?: 0)
    result = 31 * result + (closePriceEMA50?.hashCode() ?: 0)
    result = 31 * result + sectorDowntrend
    result = 31 * result + sectorUptrend
    result = 31 * result + sectorBullPercentage.hashCode()
    result = 31 * result + (symbol?.hashCode() ?: 0)
    result = 31 * result + (date?.hashCode() ?: 0)
    result = 31 * result + (signal?.hashCode() ?: 0)
    result = 31 * result + (trend?.hashCode() ?: 0)
    result = 31 * result + (sectorSymbol?.hashCode() ?: 0)
    result = 31 * result + isInUptrend.hashCode()
    return result
  }
}
