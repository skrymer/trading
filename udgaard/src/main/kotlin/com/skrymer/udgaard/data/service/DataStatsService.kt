package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.DatabaseStats
import com.skrymer.udgaard.data.dto.StockDataStats
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class DataStatsService(
  private val stockRepository: StockJooqRepository,
) {
  @Transactional(readOnly = true)
  fun calculateStats(): DatabaseStats =
    DatabaseStats(
      stockStats = calculateStockStats(),
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
}
