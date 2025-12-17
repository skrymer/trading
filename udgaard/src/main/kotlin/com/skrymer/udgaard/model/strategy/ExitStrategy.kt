package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

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
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean

  /**
   * @return a ExitStrategyReport.
   */
  fun test(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): ExitStrategyReport =
    if (match(stock, entryQuote, quote)) {
      ExitStrategyReport(true, reason(stock, entryQuote, quote), exitPrice(stock, entryQuote, quote))
    } else {
      ExitStrategyReport(false)
    }

  /**
   * @return the exit reason.
   */
  fun reason(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): String?

  /**
   * A description of this exit strategy.
   */
  fun description(): String

  /**
   * The price when the exit was hit.
   */
  fun exitPrice(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ) = quote.closePrice
}
