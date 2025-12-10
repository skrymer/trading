package com.skrymer.udgaard.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDate

/**
 * A stock quote.
 */
@Entity
@Table(
  name = "stock_quotes",
  indexes = [
    Index(name = "idx_stock_quote_symbol_date", columnList = "stock_symbol, quote_date", unique = true),
    Index(name = "idx_stock_quote_date", columnList = "quote_date"),
  ],
)
class StockQuote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stock_symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
  var stock: Stock? = null

  /**
   * The stock symbol (denormalized for easier querying)
   * This field manages the stock_symbol column value
   */
  @Column(name = "stock_symbol")
  var symbol: String = ""

  /**
   * The date when the stock quote was taken.
   */
  @Column(name = "quote_date")
  var date: LocalDate? = null

  /**
   * The stock price at close.
   */
  @Column(name = "close_price")
  var closePrice: Double = 0.0

  /**
   * The stock price at open.
   */
  @Column(name = "open_price")
  var openPrice: Double = 0.0

  /**
   * The quote high price
   */
  @Column(name = "high_price")
  var high: Double = 0.0

  /**
   * The quote low price
   */
  @Column(name = "low_price")
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
  @Column(name = "previous_heatmap")
  var previousHeatmap: Double = 0.0

  /**
   * The heatmap value of the sector the stock belongs to.
   *
   * A value between 0 and 100, 0 being max fear and 100 max greed.
   */
  @Column(name = "sector_heatmap")
  var sectorHeatmap: Double = 0.0

  /**
   * The previous heatmap value of the sector the stock belongs to.
   *
   * A value between 0 and 100, 0 being max fear and 100 max greed.
   */
  @Column(name = "previous_sector_heatmap")
  var previousSectorHeatmap: Double = 0.0

  /**
   * true if the sector the stock belongs to is in an uptrend.
   */
  @Column(name = "sector_is_in_uptrend")
  var sectorIsInUptrend = false

  /**
   * The donkey channel score of the sector +2 to -2
   */
  @Column(name = "sector_donkey_channel_score")
  var sectorDonkeyChannelScore: Int = 0

  /**
   * The ovtlyr Buy/Sell signal or null if neither.
   */
  @Column(length = 10)
  var signal: String? = null

  /**
   * The 10 ema value on close
   */
  @Column(name = "close_price_ema10")
  var closePriceEMA10: Double = 0.0

  /**
   * The 20 ema value on close
   */
  @Column(name = "close_price_ema20")
  var closePriceEMA20: Double = 0.0

  /**
   * The 5 ema value on close
   */
  @Column(name = "close_price_ema5")
  var closePriceEMA5: Double = 0.0

  /**
   * The 50 ema value on close
   */
  @Column(name = "close_price_ema50")
  var closePriceEMA50: Double = 0.0

  /**
   * Is the stock in an Uptrend or Downtrend
   */
  @Column(length = 10)
  var trend: String? = null

  /**
   * The date of the last buy signal
   */
  @Column(name = "last_buy_signal")
  var lastBuySignal: LocalDate? = null

  /**
   * The date of the last sell signal
   */
  @Column(name = "last_sell_signal")
  var lastSellSignal: LocalDate? = null

  /**
   * Current SPY Buy/Sell signal.
   */
  @Column(name = "spy_signal", length = 10)
  var spySignal: String? = null

  /**
   * SPY is in an uptrend. 10 > 20 price > 50
   */
  @Column(name = "spy_in_uptrend")
  var spyInUptrend: Boolean = false

  /**
   * SPY heatmap value
   */
  @Column(name = "spy_heatmap")
  var spyHeatmap: Double = 0.0

  /**
   * SPY previous heatmap value
   */
  @Column(name = "spy_previous_heatmap")
  var spyPreviousHeatmap: Double = 0.0

  /**
   * SPY 200-day EMA value (for market regime filter)
   */
  @Column(name = "spy_ema200")
  var spyEMA200: Double = 0.0

  /**
   * SPY 200-day SMA value (for market regime filter)
   */
  @Column(name = "spy_sma200")
  var spySMA200: Double = 0.0

  /**
   * SPY 50-day EMA value (for golden cross check in market regime filter)
   */
  @Column(name = "spy_ema50")
  var spyEMA50: Double = 0.0

  /**
   * Number of consecutive days SPY has been above 200-day SMA
   * Used to detect sustained trends vs temporary spikes
   */
  @Column(name = "spy_days_above_200_sma")
  var spyDaysAbove200SMA: Int = 0

  /**
   * Percentage of stocks in the market that are advancing (above their 10 EMA)
   * Value between 0.0 and 100.0
   * Used to measure market breadth and broad participation
   */
  @Column(name = "market_advancing_percent")
  var marketAdvancingPercent: Double = 0.0

  /**
   * Market is in an uptrend
   */
  @Column(name = "market_is_in_uptrend")
  var marketIsInUptrend: Boolean = false

  /**
   * The donkey channel score of the market +2 to -2
   */
  @Column(name = "market_donkey_channel_score")
  var marketDonkeyChannelScore: Int = 0

  /**
   * Previous quote date.
   */
  @Column(name = "previous_quote_date")
  var previousQuoteDate: LocalDate? = null

  /**
   *
   */
  @Column(name = "sector_breadth")
  var sectorBreadth: Double = 0.0

  /**
   * The number of stocks in the sector that are in a downtrend.
   */
  @Column(name = "sector_stocks_in_downtrend")
  var sectorStocksInDowntrend: Int = 0

  /**
   * The number of stocks in the sector that are in an uptrend.
   */
  @Column(name = "sector_stocks_in_uptrend")
  var sectorStocksInUptrend: Int = 0

  /**
   * Percentage of stocks in an uptrend for the sector.
   */
  @Column(name = "sector_bull_percentage")
  var sectorBullPercentage: Double = 0.0

  /**
   * The average true range for this quote.
   */
  var atr: Double = 0.0

  /**
   * Trading volume for this quote.
   * Number of shares traded during the period.
   */
  var volume: Long = 0L

  /**
   * The donchian upper band value with a look-back period of 5.
   */
  @Column(name = "donchian_upper_band")
  var donchianUpperBand: Double = 0.0

  /**
   * The donchian upper band value for the market with a look-back period of 5.
   */
  @Column(name = "donchian_upper_band_market")
  var donchianUpperBandMarket: Double = 0.0

  /**
   * The donchian upper band value for the market with a look-back period of 5.
   */
  @Column(name = "donchian_upper_band_sector")
  var donchianUpperBandSector: Double = 0.0

  /**
   * The donchian lower band value for the market.
   */
  @Column(name = "donchian_lower_band_market")
  var donchianLowerBandMarket: Double = 0.0

  /**
   * The donchian lower band value for the sector.
   */
  @Column(name = "donchian_lower_band_sector")
  var donchianLowerBandSector: Double = 0.0

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
    spyHeatmap: Double = 0.0,
    spyPreviousHeatmap: Double = 0.0,
    spyEMA200: Double = 0.0,
    spySMA200: Double = 0.0,
    spyEMA50: Double = 0.0,
    spyDaysAbove200SMA: Int = 0,
    marketAdvancingPercent: Double = 0.0,
    marketIsInUptrend: Boolean = false,
    marketDonkeyChannelScore: Int = 0,
    previousQuoteDate: LocalDate? = null,
    atr: Double = 0.0,
    volume: Long = 0L,
    sectorStocksInDowntrend: Int = 0,
    sectorStocksInUptrend: Int = 0,
    sectorBullPercentage: Double = 0.0,
    sectorDonkeyChannelScore: Int = 0,
    high: Double = 0.0,
    low: Double = 0.0,
    donchianUpperBand: Double = 0.0,
    donchianUpperBandMarket: Double = 0.0,
    donchianUpperBandSector: Double = 0.0,
    donchianLowerBandMarket: Double = 0.0,
    donchianLowerBandSector: Double = 0.0,
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
    this.sectorDonkeyChannelScore = sectorDonkeyChannelScore
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
    this.spyHeatmap = spyHeatmap
    this.spyPreviousHeatmap = spyPreviousHeatmap
    this.spyEMA200 = spyEMA200
    this.spySMA200 = spySMA200
    this.spyEMA50 = spyEMA50
    this.spyDaysAbove200SMA = spyDaysAbove200SMA
    this.marketAdvancingPercent = marketAdvancingPercent
    this.marketIsInUptrend = marketIsInUptrend
    this.marketDonkeyChannelScore = marketDonkeyChannelScore
    this.previousQuoteDate = previousQuoteDate
    this.atr = atr
    this.volume = volume
    this.sectorStocksInUptrend = sectorStocksInUptrend
    this.sectorStocksInDowntrend = sectorStocksInDowntrend
    this.sectorBullPercentage = sectorBullPercentage
    this.high = high
    this.low = low
    this.donchianUpperBand = donchianUpperBand
    this.donchianUpperBandMarket = donchianUpperBandMarket
    this.donchianUpperBandSector = donchianUpperBandSector
    this.donchianLowerBandMarket = donchianLowerBandMarket
    this.donchianLowerBandSector = donchianLowerBandSector
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
   * @return true if percentage of bullish stocks are higher than the 10ema.
   */
  fun isMarketInUptrend() = marketIsInUptrend

  /**
   * @return true if it has a buy signal and the signal is the day of the quote or one day prior
   */
  fun hasCurrentBuySignal() =
    lastBuySignal != null &&
      // The buy signal is for this quotes date or this quotes date -1
      (lastBuySignal?.equals(date) == true || lastBuySignal?.equals(date!!.minusDays(1)) == true) &&
      // and buy signal is after sell signal
      (lastSellSignal == null || lastBuySignal?.isAfter(lastSellSignal) == true)

  fun hasBuySignal() =
    lastBuySignal != null &&
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

  override fun toString() = "Symbol: $symbol Date: $date Close price: $closePrice Heatmap: $heatmap Previous heatmap: $previousHeatmap"

  override fun equals(other: Any?): Boolean =
    if (other is StockQuote) {
      this.date?.equals(other.date) == true && this.symbol == other.symbol
    } else {
      false
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
