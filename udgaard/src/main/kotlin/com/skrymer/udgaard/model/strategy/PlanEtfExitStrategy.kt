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
@RegisteredStrategy(name = "PlanEtf", type = StrategyType.EXIT)
class PlanEtfExitStrategy : ExitStrategy {
  private val compositeStrategy =
    exitStrategy {
      emaCross(10, 20)
      orderBlock(30)
      profitTarget(3.5, 20)
      trailingStopLoss(2.7)
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

  override fun description() = "Plan ETF exit strategy"

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
}
