package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.MonteCarloRequest
import com.skrymer.udgaard.backtesting.model.MonteCarloTechniqueType
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.service.sizer.AtrRiskSizerConfig
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MonteCarloServiceTest {
  private val service = MonteCarloService(PositionSizingService())

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
    // Given: a Monte Carlo request with no position sizing
    val backtest = createBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
      )

    // When
    val result = service.runSimulation(request)

    // Then: compounded per-trade return and core scalars are reported, max DD is null
    // because there's no portfolio equity curve to draw down on without sizing
    val expectedReturn =
      backtest.trades
        .fold(1.0) { multiplier, trade -> multiplier * (1.0 + trade.profitPercentage / 100.0) }
        .let { (it - 1.0) * 100.0 }

    assertEquals(expectedReturn, result.originalReturnPercentage, 0.01)
    assertEquals(backtest.edge, result.originalEdge, 0.01)
    assertEquals(backtest.winRate, result.originalWinRate, 0.01)
    assertNull(result.originalMaxDrawdown)
  }

  @Test
  fun `originalMaxDrawdown is populated when position sizing is configured`() {
    // Given: trades whose entry quotes have non-zero ATR. Without ATR, AtrRiskSizer
    // short-circuits to 0 shares, no positions ever open, and maxDrawdownPct is
    // stuck at 0.0 — making any "is the field populated correctly" assertion vacuous.
    val backtest = createSizedBacktest()
    val request =
      MonteCarloRequest(
        backtestResult = backtest,
        techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
        iterations = 100,
        positionSizing = PositionSizingConfig(
          startingCapital = 10_000.0,
          sizer = AtrRiskSizerConfig(riskPercentage = 1.25, nAtr = 2.0),
          leverageRatio = 1.0,
        ),
      )

    // When
    val result = service.runSimulation(request)

    // Then: drawdown actually fires (sizer can size, losers reduce equity from peak)
    // and matches PositionSizingService output for these exact trades + config.
    val expectedMaxDD =
      PositionSizingService().applyPositionSizing(backtest.trades, request.positionSizing!!).maxDrawdownPct
    val actualMaxDD = requireNotNull(result.originalMaxDrawdown) { "originalMaxDrawdown should be populated when sizing is configured" }
    assertTrue(actualMaxDD > 0.0, "expected non-zero drawdown but got $actualMaxDD")
    assertEquals(expectedMaxDD, actualMaxDD, 1e-9)
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

  // ===== DRAWDOWN THRESHOLD PROBABILITIES + CVaR =====

  @Test
  fun `request without drawdownThresholds yields null probabilities field`() {
    // Given a sized backtest with non-trivial drawdowns
    val backtest = createSizedBacktest()
    val request = MonteCarloRequest(
      backtestResult = backtest,
      techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
      iterations = 200,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      // drawdownThresholds intentionally omitted
    )

    // When
    val result = service.runSimulation(request)

    // Then the new field is null — opt-in semantics
    assertNull(result.statistics.drawdownThresholdProbabilities)
  }

  @Test
  fun `threshold probabilities are sorted ascending and monotone non-increasing`() {
    // Given a request with thresholds in shuffled order
    val backtest = createSizedBacktest()
    val request = MonteCarloRequest(
      backtestResult = backtest,
      techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
      iterations = 500,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      drawdownThresholds = listOf(30.0, 10.0, 20.0, 5.0),
    )

    // When
    val result = service.runSimulation(request)
    val probabilities = result.statistics.drawdownThresholdProbabilities

    // Then output is sorted ascending by drawdownPercent
    assertNotNull(probabilities)
    val percents = probabilities!!.map { it.drawdownPercent }
    assertEquals(listOf(5.0, 10.0, 20.0, 30.0), percents)

    // And probabilities are monotone non-increasing as drawdownPercent rises
    probabilities.zipWithNext().forEach { (lower, higher) ->
      assertTrue(
        lower.probability >= higher.probability,
        "P(DD>${lower.drawdownPercent})=${lower.probability} should be >= P(DD>${higher.drawdownPercent})=${higher.probability}",
      )
    }
    // And every probability is in [0, 100]
    probabilities.forEach { p ->
      assertTrue(p.probability in 0.0..100.0, "probability ${p.probability} out of range for ${p.drawdownPercent}")
    }
  }

  @Test
  fun `duplicate thresholds in request are deduplicated in response`() {
    // Given duplicate values in the request list
    val backtest = createSizedBacktest()
    val request = MonteCarloRequest(
      backtestResult = backtest,
      techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
      iterations = 200,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      drawdownThresholds = listOf(20.0, 20.0, 25.0, 25.0, 30.0),
    )

    // When
    val result = service.runSimulation(request)

    // Then duplicates collapse to unique values
    val percents = result.statistics.drawdownThresholdProbabilities!!.map { it.drawdownPercent }
    assertEquals(listOf(20.0, 25.0, 30.0), percents)
  }

  @Test
  fun `same seed produces identical threshold probabilities and CVaR across runs`() {
    // Given a deterministic request
    val backtest = createSizedBacktest()
    val request = MonteCarloRequest(
      backtestResult = backtest,
      techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
      iterations = 500,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      drawdownThresholds = listOf(5.0, 10.0, 15.0),
    )

    // When run twice with the same seed
    val first = service.runSimulation(request)
    val second = service.runSimulation(request)

    // Then both probability lists are identical
    val firstList = first.statistics.drawdownThresholdProbabilities!!
    val secondList = second.statistics.drawdownThresholdProbabilities!!
    assertEquals(firstList.size, secondList.size)
    firstList.zip(secondList).forEach { (a, b) ->
      assertEquals(a.drawdownPercent, b.drawdownPercent)
      assertEquals(a.probability, b.probability, 1e-9)
      assertEquals(a.expectedDrawdownGivenExceeded, b.expectedDrawdownGivenExceeded)
    }
  }

  @Test
  fun `threshold above max observed drawdown gives zero probability and null CVaR`() {
    // Given a sized backtest with bounded drawdowns and an extreme threshold
    val backtest = createSizedBacktest()
    val request = MonteCarloRequest(
      backtestResult = backtest,
      techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
      iterations = 200,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      drawdownThresholds = listOf(99.0), // no scenario can exceed 99% DD on this fixture
    )

    // When
    val result = service.runSimulation(request)
    val record = result.statistics.drawdownThresholdProbabilities!!.first()

    // Then probability == 0 and CVaR is null (no exceedances to average)
    assertEquals(0.0, record.probability, 1e-9)
    assertNull(record.expectedDrawdownGivenExceeded)
  }

  @Test
  fun `CVaR is at least the threshold for any non-zero exceedance`() {
    // Given thresholds at low values where exceedances are likely
    val backtest = createSizedBacktest()
    val request = MonteCarloRequest(
      backtestResult = backtest,
      techniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
      iterations = 500,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      drawdownThresholds = listOf(0.5, 1.0, 2.0),
    )

    // When
    val result = service.runSimulation(request)

    // Then any record with non-null CVaR has CVaR > threshold
    // (CVaR = mean of values strictly greater than threshold ⇒ each contributing value > threshold ⇒ mean > threshold)
    result.statistics.drawdownThresholdProbabilities!!.forEach { p ->
      val cvar = p.expectedDrawdownGivenExceeded
      if (cvar != null) {
        assertTrue(
          cvar > p.drawdownPercent,
          "CVaR=$cvar should be > threshold=${p.drawdownPercent} when exceedances exist",
        )
      }
    }
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
    atr: Double = 0.0,
  ): Trade {
    val entryQuote =
      StockQuote(
        date = entryDate,
        closePrice = 100.0,
        atr = atr,
      )
    val exitQuote =
      StockQuote(
        date = entryDate.plusDays(5),
        closePrice = 100.0 + profitPercentage,
        atr = atr,
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

  /**
   * Trades carrying non-zero ATR so AtrRiskSizer can actually size positions.
   * Sequence is winner-then-loser-then-winner-… so the equity curve has visible
   * draw downs from peak — needed for max-DD assertions in the position-sized path.
   */
  private fun createSizedBacktest(): BacktestReport {
    val trades = listOf(
      createTrade(8.0, LocalDate.of(2024, 1, 1), atr = 2.0), // winner — set initial peak
      createTrade(-5.0, LocalDate.of(2024, 1, 8), atr = 2.0), // loser — drawdown from peak
      createTrade(-3.0, LocalDate.of(2024, 1, 15), atr = 2.0), // loser — deeper
      createTrade(6.0, LocalDate.of(2024, 1, 22), atr = 2.0), // winner — recovery starts
      createTrade(4.0, LocalDate.of(2024, 1, 29), atr = 2.0), // winner — full recovery / new peak
    )
    return BacktestReport(trades.filter { it.profit > 0 }, trades.filter { it.profit <= 0 })
  }
}
