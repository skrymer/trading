package com.skrymer.udgaard.model

import java.time.LocalDate

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
   * The quote high price
   */
  var high: Double = 0.0

  /**
   * The quote low price
   */
  var low: Double = 0.0

  /**
   * The heatmap value of the stock.
   *
   * A value between 0 and 100, 0 being max fear and 100 max greed.
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

  /**
   * Previous quote date.
   */
  var previousQuoteDate: LocalDate? = null

  /**
   *
   */
  var sectorBreadth: Double = 0.0

  /**
   * The number of stocks in the sector that are in a downtrend.
   */
  var sectorStocksInDowntrend: Int = 0

  /**
   * The number of stocks in the sector that are in an uptrend.
   */
  var sectorStocksInUptrend: Int = 0

  /**
   * Percentage of stocks in an uptrend for the sector.
   */
  var sectorBullPercentage: Double = 0.0

  /**
   * The average true range for this quote.
   */
  var atr: Double = 0.0

  /**
   * The donchian upper band value with a look-back period of 5.
   */
  var donchianUpperBand: Double = 0.0

  /**
   * The donchian upper band value for the market with a look-back period of 5.
   */
  var donchianUpperBandMarket: Double = 0.0

  constructor()

  constructor(
    symbol: String = "",
    date: LocalDate? = null,
    closePrice: Double = 0.0,
    openPrice: Double = 0.0,
    heatmap: Double = 0.0,
    previousHeatmap: Double = 0.0,
    sectorHeatmap: Double = 0.0,
    previousSectorHeatmap: Double = 0.0,
    sectorIsInUptrend: Boolean = false,
    signal: String? = null,
    closePriceEMA10: Double = 0.0,
    closePriceEMA20: Double = 0.0,
    closePriceEMA5: Double = 0.0,
    closePriceEMA50: Double = 0.0,
    trend: String? = null,
    lastBuySignal: LocalDate? = null,
    lastSellSignal: LocalDate? = null,
    spySignal: String? = null,
    spyIsInUptrend: Boolean = false,
    marketIsInUptrend: Boolean = false,
    previousQuoteDate: LocalDate? = null,
    atr: Double = 0.0,
    sectorStocksInDowntrend: Int = 0,
    sectorStocksInUptrend: Int = 0,
    sectorBullPercentage: Double = 0.0,
    high: Double = 0.0,
    low: Double = 0.0,
    donchianUpperBand: Double = 0.0,
    donchianUpperBandMarket: Double = 0.0
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
    this.sectorStocksInUptrend = sectorStocksInUptrend
    this.sectorStocksInDowntrend = sectorStocksInDowntrend
    this.sectorBullPercentage = sectorBullPercentage
    this.high = high
    this.low = low
    this.donchianUpperBand = donchianUpperBand
    this.donchianUpperBandMarket = donchianUpperBandMarket
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
  fun hasCurrentBuySignal() =
    lastBuySignal != null &&
      // The buy signal today or yesterday is within the last 2 days of the quote
      (lastBuySignal?.equals(date) == true || lastBuySignal?.equals(date!!.minusDays(1)) == true) &&
      // and buy signal is after sell signal
      (lastSellSignal == null || lastBuySignal?.isAfter(lastSellSignal) == true)

  /**
   * Get half ATR value
   */
  fun getClosePriceMinusHalfAtr() = closePrice - (atr / 2)

  /**
   * true if the quote has a sell signal
   */
  fun hasSellSignal() = signal == "Sell"

  override fun toString() =
    "Symbol: $symbol Date: $date Close price: $closePrice Heatmap: $heatmap Previous heatmap: $previousHeatmap"

  override fun equals(other: Any?): Boolean {
    return if (other is StockQuote) {
      this.date?.equals(other.date) == true
    } else {
      false
    }
  }

  override fun hashCode(): Int {
    var result = closePrice.hashCode()
    result = 31 * result + openPrice.hashCode()
    result = 31 * result + high.hashCode()
    result = 31 * result + low.hashCode()
    result = 31 * result + heatmap.hashCode()
    result = 31 * result + previousHeatmap.hashCode()
    result = 31 * result + sectorHeatmap.hashCode()
    result = 31 * result + previousSectorHeatmap.hashCode()
    result = 31 * result + sectorIsInUptrend.hashCode()
    result = 31 * result + closePriceEMA10.hashCode()
    result = 31 * result + closePriceEMA20.hashCode()
    result = 31 * result + closePriceEMA5.hashCode()
    result = 31 * result + closePriceEMA50.hashCode()
    result = 31 * result + spyInUptrend.hashCode()
    result = 31 * result + marketIsInUptrend.hashCode()
    result = 31 * result + sectorBreadth.hashCode()
    result = 31 * result + sectorStocksInDowntrend
    result = 31 * result + sectorStocksInUptrend
    result = 31 * result + atr.hashCode()
    result = 31 * result + sectorBullPercentage.hashCode()
    result = 31 * result + symbol.hashCode()
    result = 31 * result + (date?.hashCode() ?: 0)
    result = 31 * result + (signal?.hashCode() ?: 0)
    result = 31 * result + (trend?.hashCode() ?: 0)
    result = 31 * result + (lastBuySignal?.hashCode() ?: 0)
    result = 31 * result + (lastSellSignal?.hashCode() ?: 0)
    result = 31 * result + (spySignal?.hashCode() ?: 0)
    result = 31 * result + (previousQuoteDate?.hashCode() ?: 0)
    return result
  }
}
