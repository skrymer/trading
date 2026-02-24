package com.skrymer.udgaard.data.integration

import com.skrymer.udgaard.data.model.CompanyInfo

/**
 * Interface for company information providers.
 *
 * Implementations should provide company metadata such as sector classification
 * and market capitalization. This is separated from [FundamentalDataProvider]
 * to allow sourcing company info from a different API than earnings data.
 *
 * All methods are suspend functions to support rate limiting with true backpressure.
 */
interface CompanyInfoProvider {
  /**
   * Get company info (sector and market cap) for a stock
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @return CompanyInfo with sector and market cap, or null if unavailable
   */
  suspend fun getCompanyInfo(symbol: String): CompanyInfo?
}
