package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Project X exit strategy using composition.
 *  Wick below EMA5 - 0.5 ATR (VZ failure)
 *  EMA10 crosses below EMA20
 *  Price enters bearish Order Block
 *  Earnings tomorrow → mandatory exit
 */
@RegisteredStrategy(name = "ProjectXExitStrategy", type = StrategyType.EXIT)
class ProjectXExitStrategy : ExitStrategy {
  private val compositeStrategy =
    exitStrategy {
      // Wick below EMA5 - 0.5 ATR (VZ failure)
      priceBelowEmaMinusAtr(emaPeriod = 5, atrMultiplier = 0.5)
      // EMA10 crosses below EMA20
      emaCross(10, 20)
      // enters bearish order block
      bearishOrderBlock(ageInDays = 30, useHighPrice = true)
      // Earnings
//      exitBeforeEarnings(1)
    }

  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = compositeStrategy.match(stock, entryQuote, quote)

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): String? = compositeStrategy.reason(stock, entryQuote, quote)

  override fun description() = "Vegard Plan ETF exit strategy"

  override fun exitPrice(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Double =
    if (quote.closePrice < 1.0) {
      stock.getPreviousQuote(quote)?.closePrice ?: 0.0
    } else {
      quote.closePrice
    }

  /**
   * Get the underlying composite strategy for metadata extraction.
   */
  fun getCompositeStrategy(): CompositeExitStrategy = compositeStrategy
}
