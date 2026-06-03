package com.skrymer.udgaard.data.dto

import com.skrymer.udgaard.data.model.AssetType
import java.time.LocalDate
import java.time.LocalDateTime

// A symbol in the trading universe with its asset type. The universe is derived from the
// ingested `stocks` table (ADR 0011) — there is no separate symbol catalogue.
data class SymbolRecord(
  val symbol: String,
  val assetType: AssetType,
)

// Database Statistics DTOs
data class DatabaseStats(
  val stockStats: StockDataStats,
  val totalDataPoints: Long,
  val estimatedSizeKB: Long,
  val generatedAt: LocalDateTime,
  val lastRefreshedAt: LocalDateTime? = null,
)

data class StockDataStats(
  val totalStocks: Int,
  val totalQuotes: Long,
  val totalEarnings: Long,
  val totalOrderBlocks: Long,
  val dateRange: DateRange?,
  val averageQuotesPerStock: Double,
  val stocksWithEarnings: Int,
  val stocksWithOrderBlocks: Int,
  val lastUpdatedStock: StockUpdateInfo?,
  val oldestDataStock: StockUpdateInfo?,
  val recentlyUpdated: List<StockUpdateInfo>,
)

data class DateRange(
  val earliest: LocalDate,
  val latest: LocalDate,
  val days: Long,
)

data class StockUpdateInfo(
  val symbol: String,
  val lastQuoteDate: LocalDate,
  val quoteCount: Int,
  val hasEarnings: Boolean,
  val orderBlockCount: Int,
)

data class SimpleStockInfo(
  val symbol: String,
  val sector: String,
  val quoteCount: Int,
  val orderBlockCount: Int,
  val lastQuoteDate: LocalDate?,
  val hasData: Boolean,
)

// Breadth Coverage DTOs
data class BreadthCoverageStats(
  val totalStocks: Int,
  val sectors: List<SectorStockCount>,
)

data class SectorStockCount(
  val sectorSymbol: String,
  val totalStocks: Int,
)

// Refresh DTOs
data class RefreshProgress(
  val total: Int = 0,
  val completed: Int = 0,
  val failed: Int = 0,
  val lastSuccess: String? = null,
  val lastError: String? = null,
)

data class RefreshResponse(
  val queued: Int,
  val message: String,
)

// Outcome of a full-universe reconcile: how many stocks were pruned as drifted-dead and how
// many catalogue symbols were queued. reconciled = false means the catalogue lookup was
// unusable (null/empty) and the universe was left untouched.
data class ReconcileResult(
  val reconciled: Boolean,
  val queued: Int,
  val pruned: Int,
)

enum class RefreshType {
  STOCK,
}

data class RefreshTask(
  val type: RefreshType,
  val identifier: String,
  val priority: Int = 0, // Higher = more important
)
