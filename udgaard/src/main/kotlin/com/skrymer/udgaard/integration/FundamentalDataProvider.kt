package com.skrymer.udgaard.integration

import com.skrymer.udgaard.model.Earning

/**
 * Interface for fundamental data providers
 *
 * Implementations should provide fundamental company data such as
 * earnings, financial statements, ratios, and other non-price metrics
 * used for fundamental analysis.
 */
interface FundamentalDataProvider {
  /**
   * Get earnings history for a stock symbol
   *
   * Returns quarterly earnings data including:
   * - Reported earnings per share (EPS)
   * - Estimated EPS
   * - Earnings surprises
   * - Report dates and times
   *
   * Useful for strategies that need to avoid earnings-related volatility
   * or make decisions based on earnings performance.
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @return List of quarterly earnings, or null if unavailable
   */
  fun getEarnings(symbol: String): List<Earning>?
}
