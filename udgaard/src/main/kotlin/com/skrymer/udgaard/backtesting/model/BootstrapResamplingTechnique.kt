package com.skrymer.udgaard.backtesting.model

import kotlin.random.Random

/**
 * Bootstrap resampling Monte Carlo technique with optional block-bootstrap support.
 *
 * - `blockSize` null or 1 → IID bootstrap (each "block" is one trade picked uniformly at random with replacement).
 * - `blockSize >= 2` → Circular Block Bootstrap (CBB): repeatedly sample a uniform-random start index in `[0, n)` and
 *   emit `blockSize` trades with `mod n` wrap-around; concatenate blocks until the resampled sequence has length `n`.
 *
 * Block bootstrap preserves short-range autocorrelation in the trade sequence (regime clustering — multiple
 * longs hit by the same selloff, multiple wins during the same uptrend). IID bootstrap breaks this structure
 * and produces edge confidence intervals that are too narrow for regime-correlated strategies.
 *
 * Stationary bootstrap (Politis-Romano 1994) was considered but rejected: CBB has lower variance on the
 * variance estimator at the same mean block length, simpler to test, and we don't need stationarity for
 * trading-edge / drawdown CIs.
 */
class BootstrapResamplingTechnique(
  private val blockSize: Int? = null,
) : MonteCarloTechnique() {
  override fun generateScenarios(
    backtestResult: BacktestReport,
    iterations: Int,
    seed: Long?,
    positionSizing: PositionSizingConfig?,
  ): List<MonteCarloScenario> {
    val trades = backtestResult.trades
    if (trades.isEmpty()) return emptyList()

    val n = trades.size
    val effectiveBlock = (blockSize ?: 1).coerceIn(1, n)
    if (effectiveBlock >= 2) requireSortedByEntryDate(trades)
    val baseSeed = seed ?: System.nanoTime()

    return (1..iterations)
      .toList()
      .parallelStream()
      .map { iteration ->
        val random = Random(baseSeed + iteration)
        val resampled = circularBlockBootstrap(trades, n, effectiveBlock, random)
        if (positionSizing != null) {
          createScenarioWithSizing(iteration, resampled, positionSizing)
        } else {
          createScenario(iteration, resampled)
        }
      }.toList()
  }

  private fun circularBlockBootstrap(
    trades: List<Trade>,
    n: Int,
    blockSize: Int,
    random: Random,
  ): List<Trade> {
    val out = ArrayList<Trade>(n)
    while (out.size < n) {
      val start = random.nextInt(n)
      val take = minOf(blockSize, n - out.size)
      for (i in 0 until take) {
        out.add(trades[(start + i) % n])
      }
    }
    return out
  }

  /**
   * Defensive: `BacktestReport.trades` is structurally sorted by `entryQuote.date` via its lazy
   * computed property, so this check is unreachable through the normal call path. It exists to
   * fail loudly if a future engine refactor bypasses that lazy property.
   */
  private fun requireSortedByEntryDate(trades: List<Trade>) {
    require(trades.zipWithNext().all { (a, b) -> a.entryQuote.date <= b.entryQuote.date }) {
      "Block bootstrap requires trades sorted by entryQuote.date ascending"
    }
  }

  override fun name(): String = if ((blockSize ?: 1) >= 2) "Block Bootstrap Resampling" else "Bootstrap Resampling"

  override fun description(): String =
    if ((blockSize ?: 1) >= 2) {
      "Resamples contiguous blocks of trades (Circular Block Bootstrap, requested block size=$blockSize; " +
        "clamped to min(blockSize, N) at runtime when the request exceeds the trade count) " +
        "to preserve short-range autocorrelation. Produces wider, more honest edge CIs than IID " +
        "bootstrap when trades cluster in regimes."
    } else {
      "Randomly samples trades with replacement (IID) to test edge consistency. " +
        "Some trades may appear multiple times and others not at all."
    }
}
