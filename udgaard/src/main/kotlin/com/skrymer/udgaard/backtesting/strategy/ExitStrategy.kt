package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitProximity
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * An exit strategy.
 */
interface ExitStrategy {
  /**
   * @param stock - the Stock
   * @param entryQuote - the quote that matched the entry strategy.
   * @param quote - the current quote
   * @return true when exit criteria has been met.
   */
  fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean

  fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = match(stock, entryQuote, quote)

  /**
   * @return a ExitStrategyReport.
   */
  fun test(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ExitStrategyReport =
    if (match(stock, entryQuote, quote)) {
      ExitStrategyReport(true, reason(stock, entryQuote, quote), exitPrice(stock, entryQuote, quote))
    } else {
      ExitStrategyReport(false)
    }

  fun test(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): ExitStrategyReport =
    if (match(stock, entryQuote, quote, context)) {
      ExitStrategyReport(true, reason(stock, entryQuote, quote, context), exitPrice(stock, entryQuote, quote))
    } else {
      ExitStrategyReport(false)
    }

  /**
   * @return the exit reason.
   */
  fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): String?

  fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): String? = reason(stock, entryQuote, quote)

  /**
   * A description of this exit strategy.
   */
  fun description(): String

  /**
   * The price when the exit was hit.
   */
  fun exitPrice(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ) = quote.closePrice

  /**
   * Proximity to triggering for every condition in the strategy that reports one.
   *
   * Unlike `test()`/`match()` which short-circuit on the first triggering condition,
   * this MUST evaluate every condition and return non-null results in declaration order.
   * Scanner UI uses this to warn users when a trade is close to any exit, not just the
   * one that would fire first.
   *
   * Default returns empty — strategies opt in by overriding. `CompositeExitStrategy`
   * overrides to delegate to its composed conditions.
   */
  fun exitProximities(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): List<ExitProximity> = emptyList()
}
