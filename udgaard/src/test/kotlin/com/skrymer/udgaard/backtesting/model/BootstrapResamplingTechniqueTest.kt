package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

class BootstrapResamplingTechniqueTest {
  private val technique = BootstrapResamplingTechnique()

  @Test
  fun `should generate correct number of scenarios`() {
    val backtest = createSimpleBacktest()
    val iterations = 100

    val scenarios = technique.generateScenarios(backtest, iterations)

    assertEquals(iterations, scenarios.size, "Should generate correct number of scenarios")
  }

  @Test
  fun `should preserve number of trades through resampling`() {
    val backtest = createSimpleBacktest()
    val originalTradeCount = backtest.trades.size
    val iterations = 50

    val scenarios = technique.generateScenarios(backtest, iterations)

    scenarios.forEach { scenario ->
      assertEquals(
        originalTradeCount,
        scenario.trades.size,
        "Each scenario should have same number of trades as original",
      )
    }
  }

  @Test
  fun `should allow trades to appear multiple times`() {
    val backtest = createBacktestWithDistinctProfits()
    val iterations = 100

    val scenarios = technique.generateScenarios(backtest, iterations)

    // Check that at least one scenario has duplicate trades
    val hasScenarioWithDuplicates =
      scenarios.any { scenario ->
        val profits = scenario.trades.map { it.profitPercentage }
        profits.size != profits.distinct().size
      }

    assertTrue(
      hasScenarioWithDuplicates,
      "Bootstrap should allow trades to appear multiple times",
    )
  }

  @Test
  fun `should produce different total returns across scenarios`() {
    val backtest = createBacktestWithDistinctProfits()
    val iterations = 100

    val scenarios = technique.generateScenarios(backtest, iterations)

    val totalReturns = scenarios.map { it.totalReturnPercentage }.distinct()

    assertTrue(
      totalReturns.size > 1,
      "Bootstrap should produce different total returns due to resampling",
    )
  }

  @Test
  fun `should produce reproducible results with same seed`() {
    val backtest = createSimpleBacktest()
    val seed = 54321L

    val scenarios1 = technique.generateScenarios(backtest, 10, seed)
    val scenarios2 = technique.generateScenarios(backtest, 10, seed)

    scenarios1.zip(scenarios2).forEach { (s1, s2) ->
      assertEquals(
        s1.totalReturnPercentage,
        s2.totalReturnPercentage,
        0.01,
        "Same seed should produce same scenarios",
      )
    }
  }

  @Test
  fun `should calculate equity curve correctly`() {
    val backtest = createSimpleBacktest()

    val scenarios = technique.generateScenarios(backtest, 1)
    val scenario = scenarios.first()

    assertEquals(
      scenario.trades.size,
      scenario.equityCurve.size,
      "Equity curve should have one point per trade",
    )

    // Monte Carlo uses compounding returns, not additive
    var multiplier = 1.0
    scenario.trades.zip(scenario.equityCurve).forEach { (trade, point) ->
      multiplier *= (1.0 + trade.profitPercentage / 100.0)
      val expectedCumulative = (multiplier - 1.0) * 100.0
      assertEquals(
        expectedCumulative,
        point.cumulativeReturnPercentage,
        0.01,
        "Equity curve should be cumulative with compounding",
      )
    }
  }

  @Test
  fun `should calculate win rate correctly`() {
    val backtest = createBacktestWithMixedTrades()

    val scenarios = technique.generateScenarios(backtest, 10)

    scenarios.forEach { scenario ->
      val expectedWinRate = scenario.trades.count { it.profitPercentage > 0 }.toDouble() / scenario.trades.size
      assertEquals(
        expectedWinRate,
        scenario.winRate,
        0.01,
        "Win rate should be calculated correctly",
      )
    }
  }

  @Test
  fun `should handle empty backtest`() {
    val backtest = BacktestReport(emptyList(), emptyList())

    val scenarios = technique.generateScenarios(backtest, 10)

    assertTrue(scenarios.isEmpty(), "Should return empty list for empty backtest")
  }

  @Test
  fun `should have correct name and description`() {
    assertEquals("Bootstrap Resampling", technique.name())
    assertTrue(technique.description().contains("replacement"))
  }

  @Test
  fun `block bootstrap exposes block-mode name and description`() {
    // Given a technique configured with blockSize >= 2
    val blockTechnique = BootstrapResamplingTechnique(blockSize = 5)

    // Then name + description switch to block-mode wording
    assertEquals("Block Bootstrap Resampling", blockTechnique.name())
    assertTrue(blockTechnique.description().contains("Circular Block Bootstrap"))
    assertTrue(blockTechnique.description().contains("block size=5"))
  }

  @Test
  fun `blockSize null and blockSize=1 produce bit-identical scenarios with the same seed`() {
    // Given the same fixture and the same seed
    val backtest = createSimpleBacktest()
    val seed = 12345L

    // When generating with both spellings of "IID"
    val nullScenarios = BootstrapResamplingTechnique(blockSize = null).generateScenarios(backtest, 100, seed)
    val oneScenarios = BootstrapResamplingTechnique(blockSize = 1).generateScenarios(backtest, 100, seed)

    // Then trade-by-trade identity holds — both code paths take the same RNG calls
    // (effectiveBlock=1 makes the CBB loop call random.nextInt(n) per trade with no extra
    // calls, mirroring the legacy IID loop). A statistical test would silently pass even
    // if a future regression made one path slightly biased — bit-identity is the actual
    // invariant, so assert it.
    assertEquals(nullScenarios.size, oneScenarios.size)
    nullScenarios.zip(oneScenarios).forEachIndexed { i, (s1, s2) ->
      assertEquals(
        s1.trades.map { it.profitPercentage },
        s2.trades.map { it.profitPercentage },
        "Trade-level identity must hold for scenario $i",
      )
      assertEquals(
        s1.totalReturnPercentage,
        s2.totalReturnPercentage,
        1e-9,
        "totalReturnPercentage must match exactly for scenario $i",
      )
    }
  }

  @Test
  fun `CBB structural — blockSize=N produces a single contiguous wrap-around slice`() {
    // Given a backtest with N distinguishable trades
    val backtest = createBacktestWithDistinctProfits() // 5 trades with profits 10, 20, 30, -5, -10
    val n = backtest.trades.size

    // When CBB runs with blockSize == N (only one block needed per scenario)
    val seed = 7L
    val technique = BootstrapResamplingTechnique(blockSize = n)
    val scenarios = technique.generateScenarios(backtest, 10, seed)

    // Then each scenario's resampled trade sequence is exactly trades[start], trades[start+1 mod N], ...
    // for the start picked by the per-iteration RNG
    scenarios.forEachIndexed { i, scenario ->
      val iteration = i + 1
      val expectedStart = Random(seed + iteration).nextInt(n)
      val expectedSequence = (0 until n).map { backtest.trades[(expectedStart + it) % n].profitPercentage }
      val actualSequence = scenario.trades.map { it.profitPercentage }
      assertEquals(
        expectedSequence,
        actualSequence,
        "Iteration $iteration: CBB with blockSize=N must produce a contiguous wrap-around slice",
      )
    }
  }

  @Test
  fun `CBB empirical variance matches truncated Bartlett kernel at rho zero point three — realistic regime`() {
    assertCbbVarianceMatches(rho = 0.3, blockSize = 10)
  }

  @Test
  fun `CBB empirical variance matches truncated Bartlett kernel at rho zero point seven — stress regime`() {
    assertCbbVarianceMatches(rho = 0.7, blockSize = 10)
  }

  @Test
  fun `block bootstrap widens sample-mean variance vs IID — sanity smoke on AR(1) fixture`() {
    // Given an AR(1) regime-correlated fixture (rho=0.3, the realistic upper bound)
    val backtest = createAr1Backtest(rho = 0.3, n = 200, seed = 42L)
    val mcSeed = 999L
    val iterations = 5000

    // When running both IID and CBB(blockSize=10) on the same fixture
    val iidScenarios = BootstrapResamplingTechnique(blockSize = null).generateScenarios(backtest, iterations, mcSeed)
    val cbbScenarios = BootstrapResamplingTechnique(blockSize = 10).generateScenarios(backtest, iterations, mcSeed)

    // Then CBB widens the sample-mean variance (autocorrelation is preserved, not destroyed)
    val iidVar = variance(iidScenarios.map { sampleMean(it.trades) })
    val cbbVar = variance(cbbScenarios.map { sampleMean(it.trades) })
    assertTrue(
      cbbVar > iidVar,
      "CBB sample-mean variance ($cbbVar) must exceed IID sample-mean variance ($iidVar) on regime-correlated trades",
    )
  }

  @Test
  fun `blockSize larger than N is silently capped at N`() {
    // Given a 5-trade backtest and a request for blockSize 1000
    val backtest = createBacktestWithDistinctProfits()
    val n = backtest.trades.size
    val technique = BootstrapResamplingTechnique(blockSize = 1000)

    // When generating scenarios
    val scenarios = technique.generateScenarios(backtest, 10, seed = 11L)

    // Then no error; effective blockSize is N so each scenario is a single contiguous wrap-around slice
    assertEquals(10, scenarios.size)
    scenarios.forEachIndexed { i, scenario ->
      val expectedStart = Random(11L + i + 1).nextInt(n)
      val expectedSequence = (0 until n).map { backtest.trades[(expectedStart + it) % n].profitPercentage }
      assertEquals(expectedSequence, scenario.trades.map { it.profitPercentage })
    }
  }

  // ===== HELPERS =====

  /**
   * Run the CBB variance assertion against the Bartlett kernel computed from the fixture's own
   * sample autocorrelations.
   *
   * Theory: for a CBB with block length L on a stationary sequence, the variance of the resample
   * sample mean satisfies
   *   Var_CBB / Var_IID = 1 + 2 * sum_{k=1..L-1} (1 - k/L) * sample_autocorr(k)
   *
   * Computing the kernel from the *fixture's* sample autocorrelations (rather than the AR(1)
   * population value `rho^k`) eliminates the finite-sample discrepancy between the realized
   * autocorrelation function and its theoretical value — at N=200 the two diverge by O(1/sqrt(N))
   * ≈ 7% per lag, which compounds across the kernel.
   *
   * The kernel applies to the **sample mean** (linear in trades). `edge` is non-linear
   * (winRate × avgWin − lossRate × avgLoss) and follows a different variance kernel, so this test
   * asserts on the sample mean, not on `MonteCarloScenario.edge`.
   *
   * Runs at 10k iterations / fixed seed with positionSizing = null (sizing recompounding would
   * confound the LRV signal). Asserts ±15% of the fixture-derived expected ratio.
   */
  private fun assertCbbVarianceMatches(rho: Double, blockSize: Int) {
    // Given an AR(1) fixture with the requested autocorrelation
    val n = 200
    val (backtest, profits) = createAr1BacktestWithProfits(rho = rho, n = n, seed = 42L)
    val mcSeed = 999L
    val iterations = 10000

    // When running IID and CBB with the same MC seed
    val iidMeans = BootstrapResamplingTechnique(blockSize = null)
      .generateScenarios(backtest, iterations, mcSeed)
      .map { sampleMean(it.trades) }
    val cbbMeans = BootstrapResamplingTechnique(blockSize = blockSize)
      .generateScenarios(backtest, iterations, mcSeed)
      .map { sampleMean(it.trades) }

    // Then the empirical variance ratio matches the Bartlett kernel computed from the fixture
    val expectedRatio = bartlettRatioFromSampleAutocorr(profits, blockSize)
    val empiricalRatio = variance(cbbMeans) / variance(iidMeans)
    val relativeError = kotlin.math.abs(empiricalRatio - expectedRatio) / expectedRatio
    assertTrue(
      relativeError <= 0.15,
      "CBB sample-mean variance ratio mismatch at rho=$rho, L=$blockSize: " +
        "expected=$expectedRatio (from fixture's sample autocorr), empirical=$empiricalRatio, relativeError=$relativeError",
    )
  }

  /** Bartlett kernel computed from the sequence's own sample autocorrelations. */
  private fun bartlettRatioFromSampleAutocorr(values: DoubleArray, blockSize: Int): Double {
    val n = values.size
    val mean = values.average()
    val gamma0 = values.sumOf { (it - mean) * (it - mean) } / n
    var sum = 0.0
    for (k in 1 until blockSize) {
      val gammaK = (0 until n - k).sumOf { (values[it] - mean) * (values[it + k] - mean) } / n
      val rhoK = gammaK / gamma0
      val kernelWeight = max(0.0, 1.0 - k.toDouble() / blockSize)
      sum += kernelWeight * rhoK
    }
    return 1.0 + 2.0 * sum
  }

  private fun sampleMean(trades: List<Trade>): Double = trades.sumOf { it.profitPercentage } / trades.size

  /**
   * Build an AR(1) backtest fixture: trade(i).profit = rho * trade(i-1).profit + sqrt(1-rho²) * eps(i),
   * eps ~ N(0, 1). entryQuote.date is strictly increasing day-by-day so the ordering check passes.
   * Even/odd split between winning/losing lists is irrelevant because BacktestReport.trades sorts on
   * entryQuote.date — the resulting sequence has the AR(1) structure regardless.
   */
  private fun createAr1Backtest(rho: Double, n: Int, seed: Long): BacktestReport =
    createAr1BacktestWithProfits(rho, n, seed).first

  /** Same as createAr1Backtest, but also returns the underlying profit array for sample-stat use. */
  private fun createAr1BacktestWithProfits(rho: Double, n: Int, seed: Long): Pair<BacktestReport, DoubleArray> {
    val rng = Random(seed)
    val noiseScale = sqrt(1.0 - rho * rho)
    val profits = DoubleArray(n)
    var prev = 0.0
    for (i in 0 until n) {
      val eps = gaussian(rng)
      val p = rho * prev + noiseScale * eps
      profits[i] = p
      prev = p
    }
    val trades = profits.mapIndexed { i, p -> createTrade(p, LocalDate.of(2024, 1, 1).plusDays(i.toLong())) }
    val (winners, losers) = trades.partition { it.profitPercentage > 0 }
    return BacktestReport(winningTrades = winners, losingTrades = losers) to profits
  }

  /** Box-Muller transform — kotlin.random.Random doesn't ship with a Gaussian. */
  private fun gaussian(rng: Random): Double {
    var u1: Double
    do {
      u1 = rng.nextDouble()
    } while (u1 == 0.0)
    val u2 = rng.nextDouble()
    return sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
  }

  private fun variance(values: List<Double>): Double {
    val mean = values.average()
    return values.sumOf { (it - mean) * (it - mean) } / values.size
  }

  @Test
  fun `mean return should approximate original with many iterations`() {
    val backtest = createSimpleBacktest()
    val originalReturn = backtest.trades.sumOf { it.profitPercentage }
    val iterations = 1000

    val scenarios = technique.generateScenarios(backtest, iterations)

    val meanReturn = scenarios.map { it.totalReturnPercentage }.average()

    // Mean should be within 20% of original (bootstrap property)
    assertTrue(
      kotlin.math.abs(meanReturn - originalReturn) < originalReturn * 0.2,
      "Mean return should approximate original with many iterations",
    )
  }

  // Helper methods

  private fun createSimpleBacktest(): BacktestReport {
    val winningTrades =
      listOf(
        createTrade(10.0, LocalDate.of(2024, 1, 1)),
        createTrade(5.0, LocalDate.of(2024, 1, 2)),
        createTrade(8.0, LocalDate.of(2024, 1, 3)),
      )
    val losingTrades =
      listOf(
        createTrade(-3.0, LocalDate.of(2024, 1, 4)),
        createTrade(-2.0, LocalDate.of(2024, 1, 5)),
      )

    return BacktestReport(winningTrades, losingTrades)
  }

  private fun createBacktestWithDistinctProfits(): BacktestReport {
    val winningTrades =
      listOf(
        createTrade(10.0, LocalDate.of(2024, 1, 1)),
        createTrade(20.0, LocalDate.of(2024, 1, 2)),
        createTrade(30.0, LocalDate.of(2024, 1, 3)),
      )
    val losingTrades =
      listOf(
        createTrade(-5.0, LocalDate.of(2024, 1, 4)),
        createTrade(-10.0, LocalDate.of(2024, 1, 5)),
      )

    return BacktestReport(winningTrades, losingTrades)
  }

  private fun createBacktestWithMixedTrades(): BacktestReport {
    val winningTrades =
      listOf(
        createTrade(10.0, LocalDate.of(2024, 1, 1)),
        createTrade(5.0, LocalDate.of(2024, 1, 2)),
      )
    val losingTrades =
      listOf(
        createTrade(-3.0, LocalDate.of(2024, 1, 3)),
        createTrade(-2.0, LocalDate.of(2024, 1, 4)),
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
