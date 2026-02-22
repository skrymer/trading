package com.skrymer.udgaard.data.integration

import com.skrymer.udgaard.data.model.CompanyInfo
import com.skrymer.udgaard.data.model.Earning

/**
 * Interface for fundamental data providers
 *
 * Implementations should provide fundamental company data such as
 * earnings, financial statements, ratios, and other non-price metrics
 * used for fundamental analysis.
 *
 * All methods are suspend functions to support rate limiting with true backpressure.
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
  suspend fun getEarnings(symbol: String): List<Earning>?

  /**
   * Get company info (sector and market cap) for a stock
   *
   * Returns sector classification (mapped to S&P sector ETFs) and market capitalization.
   * Both come from the same API endpoint (Company Overview) to avoid extra API calls.
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @return CompanyInfo with sector and market cap, or null if unavailable
   */
  suspend fun getCompanyInfo(symbol: String): CompanyInfo?
}
