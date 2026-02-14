package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Represents a trade with an entry, the quotes while the trade was on and an exit.
 * @param stockSymbol - the stock symbol the trade was for.
 * @param underlyingSymbol - optional underlying symbol used for strategy evaluation (null = same as stockSymbol).
 * @param entryQuote - the stock quote on the day of entry.
 * @param quotes - the stock quotes included in the trade, excluding entry and exit.
 * @param exitReason - the reason for exiting the trade.
 * @param startDate - the start date of the trade.
 * @param sector - the sector of the stock.
 */
class Trade(
  var stockSymbol: String,
  var underlyingSymbol: String? = null,
  var entryQuote: StockQuote,
  var quotes: List<StockQuote>,
  var exitReason: String,
  var profit: Double = 0.0,
  var startDate: LocalDate?,
  var sector: String,
) {
  /**
   * Market conditions at the time of trade entry.
   * Helps identify if poor performance correlates with market state.
   */
  @Transient
  var marketConditionAtEntry: MarketConditionSnapshot? = null

  /**
   * Trade excursion metrics (MFE/MAE and ATR drawdown).
   * Helps understand trade quality and pain tolerance required.
   */
  @Transient
  var excursionMetrics: ExcursionMetrics? = null

  /**
   * Calculate the profit percentage of this trade: (profit/entry close price) * 100
   * @return
   */
  val profitPercentage: Double
    get() = (profit / entryQuote.closePrice) * 100.0

  /**
   * The number of days the trade lasted
   */
  val tradingDays: Long
    get() {
      val exitDate =
        quotes.maxByOrNull { it.date }!!.date

      return ChronoUnit.DAYS.between(entryQuote.date, exitDate)
    }

  fun containsQuote(stockQuote: StockQuote) = quotes.contains(stockQuote)

  override fun toString(): String = "Start date $startDate"
}
