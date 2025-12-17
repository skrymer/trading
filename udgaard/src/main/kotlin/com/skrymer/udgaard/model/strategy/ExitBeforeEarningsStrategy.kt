package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

/**
 * Exit strategy that exits positions the day before earnings announcements.
 *
 * This strategy helps avoid the increased volatility and risk associated with
 * earnings announcements by exiting positions one day before the earnings date.
 *
 * Exit Condition:
 * - There is an earnings announcement scheduled for tomorrow (1 day from current quote date)
 *
 * Example:
 * - Current date: 2024-10-29
 * - Earnings date: 2024-10-30
 * - Action: Exit on 2024-10-29 (the day before earnings)
 */
@RegisteredStrategy(name = "ExitBeforeEarnings", type = StrategyType.EXIT)
class ExitBeforeEarningsStrategy : ExitStrategy {
  override fun match(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean {
    val quoteDate = quote.date ?: return false

    // Check if there's an earnings announcement tomorrow (1 day from today)
    return stock.hasEarningsWithinDays(quoteDate, 1)
  }

  override fun reason(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): String {
    val quoteDate = quote.date ?: return "Exit before earnings"
    val nextEarning = stock.getNextEarningsDate(quoteDate)

    return if (nextEarning?.reportedDate != null) {
      "Exit before earnings (${nextEarning.reportedDate})"
    } else {
      "Exit before earnings"
    }
  }

  override fun description() = "Exit the day before earnings announcement"
}
