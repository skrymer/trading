package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.BreadthDomain
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import java.util.concurrent.ConcurrentHashMap

/**
 * Session-scoped context for stock refresh operations.
 * Caches frequently accessed data to minimize redundant API calls and database queries.
 *
 * This context is created once per refresh session and reused across all stocks
 * in that session, significantly reducing:
 * - SPY API calls (1 instead of N)
 * - Market breadth DB queries (1 instead of N)
 * - Sector breadth DB queries (M instead of N, where M = unique sectors)
 *
 * Thread-safe for concurrent stock processing.
 */
data class RefreshContext(
  /**
   * SPY reference data from Ovtlyr, fetched once per session.
   * Used to enrich all stocks with SPY context (heatmap, signals, trend).
   * Null when skipOvtlyrEnrichment is true.
   */
  val spy: OvtlyrStockInformation?,
  /**
   * Market breadth data, fetched once per session.
   * Provides overall market context for all stocks.
   */
  val marketBreadth: BreadthDomain?,
  /**
   * Whether to skip Ovtlyr enrichment (signals, heatmaps, sector data).
   * When true, stocks are enriched with AlphaVantage data only (OHLCV, EMAs, ATR, ADX).
   * This significantly speeds up the refresh process when Ovtlyr data is not needed.
   */
  val skipOvtlyrEnrichment: Boolean = false,
  /**
   * Cache of sector breadth data by sector symbol.
   * Fetched lazily - only when a stock from that sector is processed.
   * Thread-safe concurrent map for parallel stock processing.
   */
  val sectorBreadthCache: ConcurrentHashMap<String, BreadthDomain?> = ConcurrentHashMap(),
  /**
   * Cache of sector symbols by stock symbol.
   * Fetched lazily to avoid redundant API calls for sector lookup.
   * Thread-safe concurrent map for parallel stock processing.
   */
  val sectorSymbolCache: ConcurrentHashMap<String, String?> = ConcurrentHashMap(),
) {
  /**
   * Get sector breadth for a given sector symbol.
   * Returns cached value if available, otherwise null (caller should fetch and cache).
   */
  fun getSectorBreadth(sectorSymbol: String): BreadthDomain? = sectorBreadthCache[sectorSymbol]

  /**
   * Cache sector breadth data for a given sector symbol.
   */
  fun cacheSectorBreadth(
    sectorSymbol: String,
    breadth: BreadthDomain?,
  ) {
    sectorBreadthCache[sectorSymbol] = breadth
  }

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
      "sectorBreadthCache=${sectorBreadthCache.size} entries, " +
      "sectorSymbolCache=${sectorSymbolCache.size} entries"
}
