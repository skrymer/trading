package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Plan Money exit strategy using composition.
 * Exits when:
 * - 10 EMA crosses under 20 EMA
 * - Sell signal occurs
 * - Price is within an order block older than 120 days
 * - Stop loss triggered (2 ATR below entry)
 */
@RegisteredStrategy(name = "PlanMoney", type = StrategyType.EXIT)
class PlanMoneyExitStrategy: ExitStrategy {
  private val compositeStrategy = exitStrategy {
    sellSignal()
    emaCross(10, 20)
    orderBlock(120)
    stopLoss(2.0)
      // Gap and crap
      // Earnings

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

  override fun description() = "Plan Money exit strategy"
}