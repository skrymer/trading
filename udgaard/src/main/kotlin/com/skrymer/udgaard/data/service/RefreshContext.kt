package com.skrymer.udgaard.data.service

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Session-scoped context for stock refresh operations.
 * Caches frequently accessed data to minimize redundant API calls and database queries.
 *
 * This context is created once per refresh session and reused across all stocks
 * in that session, significantly reducing:
 * - Sector symbol API calls (M instead of N, where M = unique sectors)
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
   * Cache of sector symbols by stock symbol.
   * Fetched lazily to avoid redundant API calls for sector lookup.
   * Thread-safe concurrent map for parallel stock processing.
   */
  val sectorSymbolCache: ConcurrentHashMap<String, String?> = ConcurrentHashMap(),
) {
  /**
   * Get cached sector symbol for a stock.
   * Returns null if not cached (caller should fetch and cache).
   */
  fun getSectorSymbol(stockSymbol: String): String? = sectorSymbolCache[stockSymbol]

  /**
   * Cache sector symbol for a stock.
   */
  fun cacheSectorSymbol(
    stockSymbol: String,
    sectorSymbol: String?,
  ) {
    sectorSymbolCache[stockSymbol] = sectorSymbol
  }

  /**
   * Get statistics about the cache usage.
   */
  fun getStats(): String =
    "RefreshContext stats: " +
      "sectorSymbolCache=${sectorSymbolCache.size} entries"
}
