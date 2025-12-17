package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

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
@RegisteredStrategy(name = "PlanQExitStrategy", type = StrategyType.EXIT)
class PlanQExitStrategy : ExitStrategy {
  private val compositeStrategy =
    exitStrategy {
      emaCross(10, 20)
      profitTarget(2.9, 20)
      trailingStopLoss(3.1)
      priceBelowEmaForDays(emaPeriod = 10, consecutiveDays = 4)
    }

  override fun match(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean = compositeStrategy.match(stock, entryQuote, quote)

  override fun reason(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): String? = compositeStrategy.reason(stock, entryQuote, quote)

  override fun description() = "Vegard Plan ETF exit strategy"

  override fun exitPrice(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
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
