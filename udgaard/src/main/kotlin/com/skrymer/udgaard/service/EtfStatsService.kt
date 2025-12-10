package com.skrymer.udgaard.service

import com.skrymer.udgaard.controller.dto.EtfCurrentStats
import com.skrymer.udgaard.controller.dto.EtfHistoricalDataPoint
import com.skrymer.udgaard.controller.dto.EtfStatsResponse
import com.skrymer.udgaard.model.EtfMembership
import com.skrymer.udgaard.model.EtfSymbol
import com.skrymer.udgaard.model.Stock
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EtfStatsService(
  val stockService: StockService,
) {
  fun getEtfStats(
    etf: EtfSymbol,
    fromDate: LocalDate,
    toDate: LocalDate,
    forceFetch: Boolean = false,
  ): EtfStatsResponse {
    // Get stock symbols for this ETF
    val stockSymbolsInEtf = EtfMembership.getStocksForEtf(etf)

    if (stockSymbolsInEtf.isEmpty()) {
      throw IllegalArgumentException("No stocks configured for ETF: ${etf.name}")
    }

    val expectedStockCount = stockSymbolsInEtf.size

    // Get only the stocks that are in this ETF (efficient repository query)
    // Fetches any missing stocks from API automatically
    val stocks =
      runBlocking {
        stockService.getStocksBySymbols(stockSymbolsInEtf, forceFetch)
      }

    val actualStockCount = stocks.size
    val missingStockCount = expectedStockCount - actualStockCount

    // Get all unique dates in the range
    val allDates =
      stocks
        .flatMap { stock ->
          stock.quotes
            .filter { quote ->
              quote.date != null &&
                quote.date!! >= fromDate &&
                quote.date!! <= toDate
            }.mapNotNull { it.date }
        }.distinct()
        .sorted()

    // Calculate historical data for each date
    val allHistoricalData =
      allDates.map { date ->
        calculateStatsForDate(stocks, date)
      }

    // Filter to only include dates where ALL stocks have data
    val completeHistoricalData = allHistoricalData.filter { it.totalStocks == actualStockCount }

    // Generate warning if stocks are missing
    val warning =
      if (missingStockCount > 0) {
        "Missing data for $missingStockCount stock(s). Please refresh to fetch missing data."
      } else if (completeHistoricalData.size < allHistoricalData.size) {
        "Some dates excluded due to incomplete stock data. Showing only dates with complete data for all $actualStockCount stocks."
      } else {
        null
      }

    // Calculate current stats using the most recent date with complete data
    val currentStats =
      if (completeHistoricalData.isNotEmpty()) {
        val latest = completeHistoricalData.last()
        val previous = if (completeHistoricalData.size > 1) completeHistoricalData[completeHistoricalData.size - 2] else latest

        EtfCurrentStats(
          bullishPercent = latest.bullishPercent,
          change = latest.bullishPercent - previous.bullishPercent,
          inUptrend = latest.bullishPercent > 50.0,
          stocksInUptrend = latest.stocksInUptrend,
          stocksInDowntrend = latest.stocksInDowntrend,
          stocksInNeutral = latest.totalStocks - latest.stocksInUptrend - latest.stocksInDowntrend,
          totalStocks = latest.totalStocks,
          lastUpdated = latest.date,
        )
      } else {
        EtfCurrentStats(
          bullishPercent = 0.0,
          change = 0.0,
          inUptrend = false,
          stocksInUptrend = 0,
          stocksInDowntrend = 0,
          stocksInNeutral = 0,
          totalStocks = 0,
          lastUpdated = null,
        )
      }

    return EtfStatsResponse(
      symbol = etf.name,
      name = etf.description,
      currentStats = currentStats,
      historicalData = completeHistoricalData,
      warning = warning,
      expectedStockCount = expectedStockCount,
      actualStockCount = actualStockCount,
    )
  }

  private fun calculateStatsForDate(
    stocks: List<Stock>,
    date: LocalDate,
  ): EtfHistoricalDataPoint {
    var stocksInUptrend = 0
    var stocksInDowntrend = 0
    var totalStocks = 0

    stocks.forEach { stock ->
      val quote = stock.quotes.find { it.date == date }
      if (quote != null) {
        totalStocks++

        // Check uptrend criteria: 10 EMA > 20 EMA AND Close > 50 EMA
        val is10Above20 = quote.closePriceEMA10 > quote.closePriceEMA20
        val isCloseAbove50 = quote.closePrice > quote.closePriceEMA50

        if (is10Above20 && isCloseAbove50) {
          stocksInUptrend++
        } else if (quote.closePriceEMA10 < quote.closePriceEMA20 && quote.closePrice < quote.closePriceEMA50) {
          stocksInDowntrend++
        }
      }
    }

    val bullishPercent =
      if (totalStocks > 0) {
        (stocksInUptrend.toDouble() / totalStocks.toDouble()) * 100.0
      } else {
        0.0
      }

    return EtfHistoricalDataPoint(
      date = date,
      bullishPercent = bullishPercent,
      stocksInUptrend = stocksInUptrend,
      stocksInDowntrend = stocksInDowntrend,
      totalStocks = totalStocks,
    )
  }
}
