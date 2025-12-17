package com.skrymer.udgaard.domain

import java.time.LocalDate

/**
 * Domain model for StockQuote (Hibernate-independent)
 * Contains all business logic from the original StockQuote entity
 */
data class StockQuoteDomain(
  val symbol: String = "",
  val date: LocalDate = LocalDate.now(),
  val closePrice: Double = 0.0,
  val openPrice: Double = 0.0,
  val high: Double = 0.0,
  val low: Double = 0.0,
  var heatmap: Double = 0.0,
  var previousHeatmap: Double = 0.0,
  var sectorHeatmap: Double = 0.0,
  var previousSectorHeatmap: Double = 0.0,
  var sectorIsInUptrend: Boolean = false,
  var sectorDonkeyChannelScore: Int = 0,
  var signal: String? = null,
  var closePriceEMA10: Double = 0.0,
  var closePriceEMA20: Double = 0.0,
  var closePriceEMA5: Double = 0.0,
  var closePriceEMA50: Double = 0.0,
  var trend: String? = null,
  var lastBuySignal: LocalDate? = null,
  var lastSellSignal: LocalDate? = null,
  var spySignal: String? = null,
  var spyInUptrend: Boolean = false,
  var spyHeatmap: Double = 0.0,
  var spyPreviousHeatmap: Double = 0.0,
  val spyEMA200: Double = 0.0,
  val spySMA200: Double = 0.0,
  val spyEMA50: Double = 0.0,
  val spyDaysAbove200SMA: Int = 0,
  var marketAdvancingPercent: Double = 0.0,
  var marketIsInUptrend: Boolean = false,
  var marketDonkeyChannelScore: Int = 0,
  var previousQuoteDate: LocalDate? = null,
  val sectorBreadth: Double = 0.0,
  var sectorStocksInDowntrend: Int = 0,
  var sectorStocksInUptrend: Int = 0,
  var sectorBullPercentage: Double = 0.0,
  var atr: Double = 0.0,
  var adx: Double? = null,
  val volume: Long = 0L,
  var donchianUpperBand: Double = 0.0,
  val donchianUpperBandMarket: Double = 0.0,
  val donchianUpperBandSector: Double = 0.0,
  val donchianLowerBandMarket: Double = 0.0,
  val donchianLowerBandSector: Double = 0.0,
) {
  fun isInUptrend() = "Uptrend" == trend

  /**
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
   * @return true if percentage of bullish stocks are higher than the 10ema.
   */
  fun isMarketInUptrend() = marketIsInUptrend

  /**
   * @return true if it has a buy signal and the signal is the day of the quote or one day prior
   */
  fun hasCurrentBuySignal() =
    lastBuySignal != null &&
      // The buy signal is for this quotes date or this quotes date -1
      (lastBuySignal == date || lastBuySignal == date?.minusDays(1)) &&
      // and buy signal is after sell signal
      (lastSellSignal == null || lastBuySignal!!.isAfter(lastSellSignal))

  fun hasBuySignal() =
    lastBuySignal != null &&
      // and buy signal is after sell signal
      (lastSellSignal == null || lastBuySignal!!.isAfter(lastSellSignal))

  /**
   * Get half ATR value
   */
  fun getClosePriceMinusHalfAtr() = closePrice - (atr / 2)

  /**
   * true if the quote has a sell signal
   */
  fun hasSellSignal() = signal == "Sell"

  override fun toString() = "Symbol: $symbol Date: $date Close price: $closePrice Heatmap: $heatmap Previous heatmap: $previousHeatmap"
}
