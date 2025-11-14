package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Plan ETF exit strategy using composition.
 * Exits when any of the following conditions are met:
 * - 10 EMA crosses under 20 EMA
 * - Within order block more than 30 days old
 * - Price extends 3.5 ATR above 20 EMA (profit target)
 * - Trailing stop loss at 2.7 ATR below highest high since entry
 *
 * Note: Sell signal exit was removed as it degraded performance (-0.04% avg)
 */
@RegisteredStrategy(name = "PlanEtf", type = StrategyType.EXIT)
class PlanEtfExitStrategy: ExitStrategy {
  private val compositeStrategy = exitStrategy {
    emaCross(10, 20)
    orderBlock(30)
    profitTarget(3.5, 20)
    trailingStopLoss(2.7)
  }

  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ): Boolean {
    return compositeStrategy.match(stock, entryQuote, quote)
  }

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ): String? {
    return compositeStrategy.reason(stock, entryQuote, quote)
  }

  override fun description() = "Plan ETF exit strategy"

  override fun exitPrice(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Double {
    return if(quote.closePrice < 1.0) stock.getPreviousQuote(quote)?.closePrice ?: 0.0
    else quote.closePrice
  }
}