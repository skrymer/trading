package com.skrymer.udgaard.data.integration

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.SectorSymbol

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
   * Get sector symbol for a stock
   *
   * Maps the stock to one of the 11 S&P sector ETFs (XLK, XLF, XLV, etc.)
   * based on the company's industry classification.
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @return SectorSymbol enum, or null if sector cannot be determined
   */
  suspend fun getSectorSymbol(symbol: String): SectorSymbol?
}
