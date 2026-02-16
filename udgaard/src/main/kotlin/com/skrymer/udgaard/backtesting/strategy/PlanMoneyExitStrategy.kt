package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Plan Money exit strategy using composition.
 */
@RegisteredStrategy(name = "PlanMoney", type = StrategyType.EXIT)
class PlanMoneyExitStrategy : ExitStrategy {
  private val compositeStrategy =
    exitStrategy {
      emaCross(10, 20)
      bearishOrderBlock(120)
      stopLoss(2.0)
      // Gap and crap
      exitBeforeEarnings(1)
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

  override fun description() = "Plan Money exit strategy"
}
