package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.CompanyInfo
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Session-scoped context for stock refresh operations.
 * Caches frequently accessed data to minimize redundant API calls and database queries.
 *
 * This context is created once per refresh session and reused across all stocks
 * in that session, significantly reducing:
 * - Company info API calls (sector + market cap from single OVERVIEW endpoint)
 *
 * Thread-safe for concurrent stock processing.
 */
data class RefreshContext(
  /**
   * Minimum date for data filtering. Only data from this date onwards is included.
   * Defaults to 2016-01-01 to provide 10 years of history for backtesting.
   */
  val minDate: LocalDate = LocalDate.of(2016, 1, 1),
  /**
   * Cache of company info (sector + market cap) by stock symbol.
   * Fetched lazily to avoid redundant API calls.
   * Thread-safe concurrent map for parallel stock processing.
   */
  val companyInfoCache: ConcurrentHashMap<String, CompanyInfo> = ConcurrentHashMap(),
) {
  /**
   * Get cached company info for a stock.
   * Returns null if not cached (caller should fetch and cache).
   */
  fun getCachedCompanyInfo(stockSymbol: String): CompanyInfo? = companyInfoCache[stockSymbol]

  /**
   * Cache company info for a stock.
   */
  fun cacheCompanyInfo(
    stockSymbol: String,
    companyInfo: CompanyInfo,
  ) {
    companyInfoCache[stockSymbol] = companyInfo
  }

  /**
   * Get statistics about the cache usage.
   */
  fun getStats(): String =
    "RefreshContext stats: " +
      "companyInfoCache=${companyInfoCache.size} entries"
}
