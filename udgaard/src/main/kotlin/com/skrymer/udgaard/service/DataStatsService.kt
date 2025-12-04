package com.skrymer.udgaard.service

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.repository.BreadthRepository
import com.skrymer.udgaard.repository.EtfRepository
import com.skrymer.udgaard.repository.StockRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class DataStatsService(
    private val stockRepository: StockRepository,
    private val breadthRepository: BreadthRepository,
    private val etfRepository: EtfRepository
) {

    fun calculateStats(): DatabaseStats {
        return DatabaseStats(
            stockStats = calculateStockStats(),
            breadthStats = calculateBreadthStats(),
            etfStats = calculateEtfStats(),
            totalDataPoints = calculateTotalDataPoints(),
            estimatedSizeKB = estimateDatabaseSize(),
            generatedAt = LocalDateTime.now()
        )
    }

    private fun calculateStockStats(): StockDataStats {
        // Use SQL queries instead of loading all data into memory
        val totalStocks = stockRepository.count()
        val totalQuotes = stockRepository.countAllQuotes()
        val totalEarnings = stockRepository.countAllEarnings()
        val totalOrderBlocks = stockRepository.countAllOrderBlocks()

        val earliestDate = stockRepository.findEarliestQuoteDate()
        val latestDate = stockRepository.findLatestQuoteDate()

        val dateRange = if (earliestDate != null && latestDate != null) {
            DateRange(
                earliest = earliestDate,
                latest = latestDate,
                days = ChronoUnit.DAYS.between(earliestDate, latestDate)
            )
        } else null

        val stocksWithEarnings = stockRepository.countStocksWithEarnings()
        val stocksWithOrderBlocks = stockRepository.countStocksWithOrderBlocks()

        // Get recently updated stocks from SQL query
        val recentlyUpdatedResults = stockRepository.findRecentlyUpdatedStocks()
        val recentlyUpdated = recentlyUpdatedResults.take(10).mapNotNull { row ->
            try {
                StockUpdateInfo(
                    symbol = row[0] as String,
                    lastQuoteDate = row[1] as LocalDate,
                    quoteCount = (row[2] as Long).toInt(),
                    hasEarnings = row[3] as Boolean,
                    orderBlockCount = (row[4] as Long).toInt()
                )
            } catch (e: Exception) {
                null
            }
        }

        return StockDataStats(
            totalStocks = totalStocks.toInt(),
            totalQuotes = totalQuotes,
            totalEarnings = totalEarnings,
            totalOrderBlocks = totalOrderBlocks,
            dateRange = dateRange,
            averageQuotesPerStock = if (totalStocks > 0) totalQuotes.toDouble() / totalStocks else 0.0,
            stocksWithEarnings = stocksWithEarnings.toInt(),
            stocksWithOrderBlocks = stocksWithOrderBlocks.toInt(),
            lastUpdatedStock = recentlyUpdated.firstOrNull(),
            oldestDataStock = recentlyUpdated.lastOrNull(),
            recentlyUpdated = recentlyUpdated
        )
    }

    private fun calculateBreadthStats(): BreadthDataStats {
        // Use SQL queries instead of loading all data into memory
        val totalBreadthSymbols = breadthRepository.count()
        val totalBreadthQuotes = breadthRepository.countAllBreadthQuotes()

        val earliestDate = breadthRepository.findEarliestBreadthQuoteDate()
        val latestDate = breadthRepository.findLatestBreadthQuoteDate()

        val dateRange = if (earliestDate != null && latestDate != null) {
            DateRange(
                earliest = earliestDate,
                latest = latestDate,
                days = ChronoUnit.DAYS.between(earliestDate, latestDate)
            )
        } else null

        // Get breadth symbol stats from SQL query
        val breadthStatsResults = breadthRepository.findBreadthSymbolStats()
        val breadthSymbols = breadthStatsResults.mapNotNull { row ->
            try {
                BreadthSymbolInfo(
                    symbol = row[0] as String,
                    quoteCount = (row[1] as Long).toInt(),
                    lastQuoteDate = row[2] as? LocalDate ?: LocalDate.now()
                )
            } catch (e: Exception) {
                null
            }
        }

        return BreadthDataStats(
            totalBreadthSymbols = totalBreadthSymbols.toInt(),
            totalBreadthQuotes = totalBreadthQuotes,
            breadthSymbols = breadthSymbols,
            dateRange = dateRange
        )
    }

    private fun calculateEtfStats(): EtfDataStats {
        // Use SQL queries instead of loading all data into memory
        val totalEtfs = etfRepository.count()
        val totalEtfQuotes = etfRepository.countAllEtfQuotes()
        val totalHoldings = etfRepository.countAllHoldings()

        val earliestDate = etfRepository.findEarliestEtfQuoteDate()
        val latestDate = etfRepository.findLatestEtfQuoteDate()

        val dateRange = if (earliestDate != null && latestDate != null) {
            DateRange(
                earliest = earliestDate,
                latest = latestDate,
                days = ChronoUnit.DAYS.between(earliestDate, latestDate)
            )
        } else null

        val etfsWithHoldings = etfRepository.countEtfsWithHoldings()

        return EtfDataStats(
            totalEtfs = totalEtfs.toInt(),
            totalEtfQuotes = totalEtfQuotes,
            totalHoldings = totalHoldings,
            dateRange = dateRange,
            etfsWithHoldings = etfsWithHoldings.toInt(),
            averageHoldingsPerEtf = if (totalEtfs > 0) totalHoldings.toDouble() / totalEtfs else 0.0
        )
    }

    private fun calculateTotalDataPoints(): Long {
        // Use SQL queries instead of loading all data into memory
        return stockRepository.countAllQuotes() +
                breadthRepository.countAllBreadthQuotes() +
                etfRepository.countAllEtfQuotes()
    }

    private fun estimateDatabaseSize(): Long {
        // Rough estimate: each quote ~500 bytes, each earning ~300 bytes
        // Use SQL queries instead of loading all data into memory
        val stockQuotes = stockRepository.countAllQuotes()
        val stockEarnings = stockRepository.countAllEarnings()
        val stockOrderBlocks = stockRepository.countAllOrderBlocks()

        val breadthQuotes = breadthRepository.countAllBreadthQuotes()

        val etfQuotes = etfRepository.countAllEtfQuotes()
        val etfHoldings = etfRepository.countAllHoldings()

        val stockDataSize = (stockQuotes * 500L) + (stockEarnings * 300L) + (stockOrderBlocks * 200L)
        val breadthDataSize = breadthQuotes * 400L
        val etfDataSize = (etfQuotes * 500L) + (etfHoldings * 200L)

        return (stockDataSize + breadthDataSize + etfDataSize) / 1024  // Convert to KB
    }
}
