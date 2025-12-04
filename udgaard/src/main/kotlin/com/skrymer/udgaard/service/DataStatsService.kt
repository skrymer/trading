package com.skrymer.udgaard.service

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.repository.BreadthRepository
import com.skrymer.udgaard.repository.EtfRepository
import com.skrymer.udgaard.repository.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class DataStatsService(
    private val stockRepository: StockRepository,
    private val breadthRepository: BreadthRepository,
    private val etfRepository: EtfRepository
) {

    @Transactional(readOnly = true)
    fun calculateStats(): DatabaseStats {
        return DatabaseStats(
            stockStats = calculateStockStats(),
            breadthStats = calculateBreadthStats(),
            etfStats = calculateEtfStats(),
            totalDataPoints = 0,  // Simplified - not counting quotes
            estimatedSizeKB = 0,  // Simplified - not estimating size
            generatedAt = LocalDateTime.now()
        )
    }

    private fun calculateStockStats(): StockDataStats {
        // Only count stocks, not expensive quote counting
        val totalStocks = stockRepository.count()

        return StockDataStats(
            totalStocks = totalStocks.toInt(),
            totalQuotes = 0,  // Simplified - not counting
            totalEarnings = 0,  // Simplified - not counting
            totalOrderBlocks = 0,  // Simplified - not counting
            dateRange = null,  // Simplified - not querying date ranges
            averageQuotesPerStock = 0.0,  // Simplified
            stocksWithEarnings = 0,  // Simplified - not counting
            stocksWithOrderBlocks = 0,  // Simplified - not counting
            lastUpdatedStock = null,  // Simplified - not querying
            oldestDataStock = null,  // Simplified - not querying
            recentlyUpdated = emptyList()  // Simplified - not querying
        )
    }

    private fun calculateBreadthStats(): BreadthDataStats {
        // Only count breadth symbols, not expensive quote counting
        val totalBreadthSymbols = breadthRepository.count()

        return BreadthDataStats(
            totalBreadthSymbols = totalBreadthSymbols.toInt(),
            totalBreadthQuotes = 0,  // Simplified - not counting
            breadthSymbols = emptyList(),  // Simplified - not querying
            dateRange = null  // Simplified - not querying
        )
    }

    private fun calculateEtfStats(): EtfDataStats {
        // Only count ETFs, not expensive quote/holdings counting
        val totalEtfs = etfRepository.count()

        return EtfDataStats(
            totalEtfs = totalEtfs.toInt(),
            totalEtfQuotes = 0,  // Simplified - not counting
            totalHoldings = 0,  // Simplified - not counting
            dateRange = null,  // Simplified - not querying
            etfsWithHoldings = 0,  // Simplified - not counting
            averageHoldingsPerEtf = 0.0  // Simplified
        )
    }
}
