package com.skrymer.udgaard.controller.dto

import java.time.LocalDate
import java.time.LocalDateTime

// Rate Limiting DTOs
data class RateLimitStats(
  val requestsLastMinute: Int,
  val requestsLastDay: Int,
  val remainingMinute: Int,
  val remainingDaily: Int,
  val minuteLimit: Int,
  val dailyLimit: Int,
  val resetMinute: Long, // seconds until reset
  val resetDaily: Long, // seconds until reset
)

data class RateLimitConfigDto(
  val requestsPerMinute: Int,
  val requestsPerDay: Int,
  val tier: String, // "FREE", "PREMIUM", or "ULTIMATE"
)

// Database Statistics DTOs
data class DatabaseStats(
  val stockStats: StockDataStats,
  val breadthStats: BreadthDataStats,
  val totalDataPoints: Long,
  val estimatedSizeKB: Long,
  val generatedAt: LocalDateTime,
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

data class BreadthDataStats(
  val totalBreadthSymbols: Int,
  val totalBreadthQuotes: Long,
  val breadthSymbols: List<BreadthSymbolInfo>,
  val dateRange: DateRange?,
)

data class BreadthSymbolInfo(
  val symbol: String,
  val quoteCount: Int,
  val lastQuoteDate: LocalDate,
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

enum class RefreshType {
  STOCK,
  BREADTH,
}

data class RefreshTask(
  val type: RefreshType,
  val identifier: String,
  val priority: Int = 0, // Higher = more important
)
