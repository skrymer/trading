package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Plan Alpha exit strategy using composition.
 *
 * Exit when ANY of the following conditions are met:
 * - Price closes below 10 EMA
 * - Within order block older than 120 days
 * - Market and sector in downtrend
 */
@RegisteredStrategy(name = "PlanAlpha", type = StrategyType.EXIT)
class PlanAlphaExitStrategy : ExitStrategy {
  private val compositeStrategy =
    exitStrategy {
      priceBelowEma(10)
      bearishOrderBlock(120)
      marketAndSectorDowntrend()
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

  override fun description() = "Plan Alpha exit strategy"
}
