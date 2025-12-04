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
        val stocks = stockRepository.findAll()

        val totalQuotes = stocks.sumOf { it.quotes.size.toLong() }
        val totalEarnings = stocks.sumOf { it.earnings.size.toLong() }
        val totalOrderBlocks = stocks.sumOf { it.orderBlocks.size.toLong() }

        val allQuotes: List<LocalDate> = stocks.flatMap { stock ->
            stock.quotes.mapNotNull { quote -> quote.date }
        }

        val dateRange = if (allQuotes.isNotEmpty()) {
            val earliest = allQuotes.min()
            val latest = allQuotes.max()
            DateRange(
                earliest = earliest,
                latest = latest,
                days = ChronoUnit.DAYS.between(earliest, latest)
            )
        } else null

        val recentlyUpdated = stocks
            .filter { it.quotes.isNotEmpty() }
            .sortedByDescending { stock -> stock.quotes.mapNotNull { q -> q.date }.maxOrNull() ?: LocalDate.MIN }
            .take(10)
            .mapNotNull { stock ->
                val symbolString = stock.symbol ?: return@mapNotNull null
                val lastDate = stock.quotes.mapNotNull { q -> q.date }.maxOrNull() ?: return@mapNotNull null
                StockUpdateInfo(
                    symbol = symbolString,
                    lastQuoteDate = lastDate,
                    quoteCount = stock.quotes.size,
                    hasEarnings = stock.earnings.isNotEmpty(),
                    orderBlockCount = stock.orderBlocks.size
                )
            }

        return StockDataStats(
            totalStocks = stocks.size,
            totalQuotes = totalQuotes,
            totalEarnings = totalEarnings,
            totalOrderBlocks = totalOrderBlocks,
            dateRange = dateRange,
            averageQuotesPerStock = if (stocks.isNotEmpty()) totalQuotes.toDouble() / stocks.size else 0.0,
            stocksWithEarnings = stocks.count { it.earnings.isNotEmpty() },
            stocksWithOrderBlocks = stocks.count { it.orderBlocks.isNotEmpty() },
            lastUpdatedStock = recentlyUpdated.firstOrNull(),
            oldestDataStock = recentlyUpdated.lastOrNull(),
            recentlyUpdated = recentlyUpdated
        )
    }

    private fun calculateBreadthStats(): BreadthDataStats {
        val breadths = breadthRepository.findAll()

        val totalBreadthQuotes = breadths.sumOf { it.quotes.size.toLong() }

        val allQuotes: List<LocalDate> = breadths.flatMap { breadth ->
            breadth.quotes.mapNotNull { quote -> quote.quoteDate }
        }

        val dateRange = if (allQuotes.isNotEmpty()) {
            val earliest = allQuotes.min()
            val latest = allQuotes.max()
            DateRange(
                earliest = earliest,
                latest = latest,
                days = ChronoUnit.DAYS.between(earliest, latest)
            )
        } else null

        val breadthSymbols = breadths.mapNotNull { breadth ->
            val symbolString = breadth.symbol?.toIdentifier() ?: return@mapNotNull null
            BreadthSymbolInfo(
                symbol = symbolString,
                quoteCount = breadth.quotes.size,
                lastQuoteDate = if (breadth.quotes.isNotEmpty()) {
                    breadth.quotes.mapNotNull { q -> q.quoteDate }.maxOrNull() ?: LocalDate.now()
                } else {
                    LocalDate.now()
                }
            )
        }

        return BreadthDataStats(
            totalBreadthSymbols = breadths.size,
            totalBreadthQuotes = totalBreadthQuotes,
            breadthSymbols = breadthSymbols,
            dateRange = dateRange
        )
    }

    private fun calculateEtfStats(): EtfDataStats {
        val etfs = etfRepository.findAll()

        val totalEtfQuotes = etfs.sumOf { it.quotes.size.toLong() }
        val totalHoldings = etfs.sumOf { it.holdings.size.toLong() }

        val allQuotes: List<LocalDate> = etfs.flatMap { etf ->
            etf.quotes.mapNotNull { quote -> quote.date }
        }

        val dateRange = if (allQuotes.isNotEmpty()) {
            val earliest = allQuotes.min()
            val latest = allQuotes.max()
            DateRange(
                earliest = earliest,
                latest = latest,
                days = ChronoUnit.DAYS.between(earliest, latest)
            )
        } else null

        return EtfDataStats(
            totalEtfs = etfs.size,
            totalEtfQuotes = totalEtfQuotes,
            totalHoldings = totalHoldings,
            dateRange = dateRange,
            etfsWithHoldings = etfs.count { it.holdings.isNotEmpty() },
            averageHoldingsPerEtf = if (etfs.isNotEmpty()) totalHoldings.toDouble() / etfs.size else 0.0
        )
    }

    private fun calculateTotalDataPoints(): Long {
        val stocks = stockRepository.findAll()
        val breadths = breadthRepository.findAll()
        val etfs = etfRepository.findAll()

        return stocks.sumOf { it.quotes.size.toLong() } +
                breadths.sumOf { it.quotes.size.toLong() } +
                etfs.sumOf { it.quotes.size.toLong() }
    }

    private fun estimateDatabaseSize(): Long {
        // Rough estimate: each quote ~500 bytes, each earning ~300 bytes
        val stocks = stockRepository.findAll()
        val breadths = breadthRepository.findAll()
        val etfs = etfRepository.findAll()

        val stockDataSize = stocks.sumOf { stock ->
            (stock.quotes.size * 500L) + (stock.earnings.size * 300L) + (stock.orderBlocks.size * 200L)
        }

        val breadthDataSize = breadths.sumOf { it.quotes.size * 400L }
        val etfDataSize = etfs.sumOf { (it.quotes.size * 500L) + (it.holdings.size * 200L) }

        return (stockDataSize + breadthDataSize + etfDataSize) / 1024  // Convert to KB
    }
}
