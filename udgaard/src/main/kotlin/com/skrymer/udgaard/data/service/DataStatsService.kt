package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.*
import com.skrymer.udgaard.data.repository.BreadthJooqRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class DataStatsService(
  private val stockRepository: StockJooqRepository,
  private val breadthRepository: BreadthJooqRepository,
) {
  @Transactional(readOnly = true)
  fun calculateStats(): DatabaseStats =
    DatabaseStats(
      stockStats = calculateStockStats(),
      breadthStats = calculateBreadthStats(),
      totalDataPoints = 0, // Simplified - not counting quotes
      estimatedSizeKB = 0, // Simplified - not estimating size
      generatedAt = LocalDateTime.now(),
    )

  private fun calculateStockStats(): StockDataStats {
    // Only count stocks, not expensive quote counting
    val totalStocks = stockRepository.count()

    return StockDataStats(
      totalStocks = totalStocks.toInt(),
      totalQuotes = 0, // Simplified - not counting
      totalEarnings = 0, // Simplified - not counting
      totalOrderBlocks = 0, // Simplified - not counting
      dateRange = null, // Simplified - not querying date ranges
      averageQuotesPerStock = 0.0, // Simplified
      stocksWithEarnings = 0, // Simplified - not counting
      stocksWithOrderBlocks = 0, // Simplified - not counting
      lastUpdatedStock = null, // Simplified - not querying
      oldestDataStock = null, // Simplified - not querying
      recentlyUpdated = emptyList(), // Simplified - not querying
    )
  }

  private fun calculateBreadthStats(): BreadthDataStats {
    // Only count breadth symbols, not expensive quote counting
    val totalBreadthSymbols = breadthRepository.count()

    return BreadthDataStats(
      totalBreadthSymbols = totalBreadthSymbols.toInt(),
      totalBreadthQuotes = 0, // Simplified - not counting
      breadthSymbols = emptyList(), // Simplified - not querying
      dateRange = null, // Simplified - not querying
    )
  }
}
