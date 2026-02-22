package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.MonteCarloRequest
import com.skrymer.udgaard.backtesting.model.MonteCarloTechniqueType
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MonteCarloServiceTest {
  private val service = MonteCarloService()

  @Test
  fun `should run trade shuffling simulation successfully`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
        seed = 12345L,
      )

    val result = service.runSimulation(request)

    assertEquals("Trade Shuffling", result.technique)
    assertEquals(100, result.iterations)
    assertNotNull(result.statistics)
    assertTrue(result.executionTimeMs >= 0)
  }

  @Test
  fun `should run bootstrap resampling simulation successfully`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING,
        iterations = 100,
        seed = 12345L,
      )

    val result = service.runSimulation(request)

    assertEquals("Bootstrap Resampling", result.technique)
    assertEquals(100, result.iterations)
    assertNotNull(result.statistics)
    assertTrue(result.executionTimeMs >= 0)
  }

  @Test
  fun `should calculate statistics correctly`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 1000,
        seed = 12345L,
      )

    val result = service.runSimulation(request)
    val stats = result.statistics

    // Verify statistics are within reasonable bounds
    assertTrue(stats.meanReturnPercentage.isFinite())
    assertTrue(stats.medianReturnPercentage.isFinite())
    assertTrue(stats.stdDevReturnPercentage >= 0)

    // Verify percentiles are ordered
    assertTrue(stats.returnPercentiles.p5 <= stats.returnPercentiles.p25)
    assertTrue(stats.returnPercentiles.p25 <= stats.returnPercentiles.p50)
    assertTrue(stats.returnPercentiles.p50 <= stats.returnPercentiles.p75)
    assertTrue(stats.returnPercentiles.p75 <= stats.returnPercentiles.p95)

    // Verify confidence intervals
    assertTrue(stats.returnConfidenceInterval95.lower <= stats.returnConfidenceInterval95.upper)

    // Verify probability of profit is between 0 and 100
    assertTrue(stats.probabilityOfProfit >= 0.0)
    assertTrue(stats.probabilityOfProfit <= 100.0)
  }

  @Test
  fun `should include original backtest metrics`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
      )

    val result = service.runSimulation(request)

    // Calculate expected compounded return
    val expectedReturn =
      backtest.trades
        .fold(1.0) { multiplier, trade -> multiplier * (1.0 + trade.profitPercentage / 100.0) }
        .let { (it - 1.0) * 100.0 }

    assertEquals(expectedReturn, result.originalReturnPercentage, 0.01)
    assertEquals(backtest.edge, result.originalEdge, 0.01)
    assertEquals(backtest.winRate, result.originalWinRate, 0.01)
  }

  @Test
  fun `should include percentile equity curves`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
      )

    val result = service.runSimulation(request)

    assertNotNull(result.percentileEquityCurves)
    assertTrue(result.percentileEquityCurves.p5.isNotEmpty())
    assertTrue(result.percentileEquityCurves.p25.isNotEmpty())
    assertTrue(result.percentileEquityCurves.p50.isNotEmpty())
    assertTrue(result.percentileEquityCurves.p75.isNotEmpty())
    assertTrue(result.percentileEquityCurves.p95.isNotEmpty())
  }

  @Test
  fun `should not include all scenarios when includeAllEquityCurves is false`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
        includeAllEquityCurves = false,
      )

    val result = service.runSimulation(request)

    assertTrue(result.scenarios.isEmpty(), "Should not include all scenarios when flag is false")
  }

  @Test
  fun `should include all scenarios when includeAllEquityCurves is true`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 10,
        includeAllEquityCurves = true,
      )

    val result = service.runSimulation(request)

    assertEquals(10, result.scenarios.size, "Should include all scenarios when flag is true")
  }

  @Test
  fun `should throw exception for unsupported technique`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.PRICE_PATH_RANDOMIZATION,
        iterations = 100,
      )

    assertThrows(IllegalArgumentException::class.java) {
      service.runSimulation(request)
    }
  }

  @Test
  fun `should throw exception for empty backtest`() {
    val backtest = BacktestReport(emptyList(), emptyList())
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
      )

    assertThrows(IllegalStateException::class.java) {
      service.runSimulation(request)
    }
  }

  @Test
  fun `should produce consistent results with same seed`() {
    val backtest = createBacktest()
    val seed = 99999L

    val request1 =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
        seed = seed,
      )

    val request2 =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
        seed = seed,
      )

    val result1 = service.runSimulation(request1)
    val result2 = service.runSimulation(request2)

    assertEquals(result1.statistics.meanReturnPercentage, result2.statistics.meanReturnPercentage, 0.01)
    assertEquals(result1.statistics.medianReturnPercentage, result2.statistics.medianReturnPercentage, 0.01)
  }

  @Test
  fun `should calculate edge percentiles correctly`() {
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
      )

    val result = service.runSimulation(request)

    // Edge percentiles should be ordered
    assertTrue(result.statistics.edgePercentiles.p5 <= result.statistics.edgePercentiles.p25)
    assertTrue(result.statistics.edgePercentiles.p25 <= result.statistics.edgePercentiles.p50)
    assertTrue(result.statistics.edgePercentiles.p50 <= result.statistics.edgePercentiles.p75)
    assertTrue(result.statistics.edgePercentiles.p75 <= result.statistics.edgePercentiles.p95)
  }

  // Helper methods

  private fun createBacktest(): BacktestReport {
    val winningTrades =
      listOf(
        createTrade(10.0, LocalDate.of(2024, 1, 1)),
        createTrade(5.0, LocalDate.of(2024, 1, 2)),
        createTrade(8.0, LocalDate.of(2024, 1, 3)),
        createTrade(12.0, LocalDate.of(2024, 1, 4)),
        createTrade(6.0, LocalDate.of(2024, 1, 5)),
      )
    val losingTrades =
      listOf(
        createTrade(-3.0, LocalDate.of(2024, 1, 6)),
        createTrade(-2.0, LocalDate.of(2024, 1, 7)),
        createTrade(-4.0, LocalDate.of(2024, 1, 8)),
      )

    return BacktestReport(winningTrades, losingTrades)
  }

  private fun createTrade(
    profitPercentage: Double,
    entryDate: LocalDate,
  ): Trade {
    val entryQuote =
      StockQuote(
        date = entryDate,
        closePrice = 100.0,
      )
    val exitQuote =
      StockQuote(
        date = entryDate.plusDays(5),
        closePrice = 100.0 + profitPercentage,
      )

    val profit = profitPercentage // Simplified for testing

    return Trade(
      stockSymbol = "TEST",
      entryQuote = entryQuote,
      quotes = listOf(exitQuote),
      exitReason = "Test exit",
      profit = profit,
      startDate = entryDate,
      sector = "Technology",
    )
  }
}
