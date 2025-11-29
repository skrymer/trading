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
@RegisteredStrategy(name = "VegardPlanEtf", type = StrategyType.EXIT)
class VegardPlanEtfExitStrategy: ExitStrategy {
  private val compositeStrategy = exitStrategy {
    emaCross(10, 20)
    profitTarget(2.9, 20)
    trailingStopLoss(3.1)
    priceBelowEmaForDays(emaPeriod = 10, consecutiveDays = 4)
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

  override fun description() = "Vegard Plan ETF exit strategy"

  override fun exitPrice(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Double {
    return if(quote.closePrice < 1.0) stock.getPreviousQuote(quote)?.closePrice ?: 0.0
    else quote.closePrice
  }

  /**
   * Get the underlying composite strategy for metadata extraction.
   */
  fun getCompositeStrategy(): CompositeExitStrategy = compositeStrategy
}