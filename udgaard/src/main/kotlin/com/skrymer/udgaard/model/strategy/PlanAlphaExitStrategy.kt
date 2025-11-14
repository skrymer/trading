package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Plan Alpha exit strategy using composition.
 *
 * Exit when ANY of the following conditions are met:
 * - Price closes below 10 EMA
 * - Sell signal occurs
 * - Within order block older than 120 days
 */
@RegisteredStrategy(name = "PlanAlpha", type = StrategyType.EXIT)
class PlanAlphaExitStrategy: ExitStrategy {
  private val compositeStrategy = exitStrategy {
    priceBelowEma(10)
    sellSignal()
    orderBlock(120)
  }

  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ): Boolean {
    return compositeStrategy.match(stock, entryQuote, quote)
  }

  override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): String? {
    return compositeStrategy.reason(stock, entryQuote, quote)
  }

  override fun description() = "Plan Alpha exit strategy"
}