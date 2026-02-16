package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Performance statistics grouped by time periods (year, quarter, month).
 * Strategy-agnostic - works with any backtest.
 */
data class TimeBasedStats(
  val byYear: Map<Int, PeriodStats>,
  val byQuarter: Map<String, PeriodStats>, // "2025-Q1"
  val byMonth: Map<String, PeriodStats>, // "2025-01"
)

/**
 * Performance statistics for a specific time period.
 */
data class PeriodStats(
  val trades: Int,
  val winRate: Double,
  val avgProfit: Double,
  val avgHoldingDays: Double,
  val exitReasons: Map<String, Int>,
  val edge: Double = 0.0,
)

/**
 * Snapshot of market conditions at trade entry.
 * Helps identify if poor performance correlates with market state.
 */
data class MarketConditionSnapshot(
  val spyClose: Double,
  val spyInUptrend: Boolean,
  val marketBreadthBullPercent: Double?, // Nullable - may not exist
  val entryDate: LocalDate,
)

/**
 * Trade excursion metrics - how much profit/loss reached during trade.
 * Includes ATR-normalized drawdown analysis.
 */
data class ExcursionMetrics(
  val maxFavorableExcursion: Double, // Highest % profit reached
  val maxFavorableExcursionATR: Double, // In ATR units
  val maxAdverseExcursion: Double, // Deepest % drawdown (negative)
  val maxAdverseExcursionATR: Double, // In ATR units (positive value)
  val mfeReached: Boolean, // Did trade reach positive territory?
)

/**
 * ATR drawdown statistics for winning trades.
 * Strategy-agnostic - no assumptions about stop loss levels.
 * Users can interpret percentiles based on their specific strategy.
 */
data class ATRDrawdownStats(
  val medianDrawdown: Double, // Median ATR drawdown
  val meanDrawdown: Double, // Mean ATR drawdown
  val percentile25: Double, // 25th percentile
  val percentile50: Double, // 50th percentile (median)
  val percentile75: Double, // 75th percentile
  val percentile90: Double, // 90th percentile
  val percentile95: Double, // 95th percentile
  val percentile99: Double, // 99th percentile
  val minDrawdown: Double, // Smallest drawdown observed
  val maxDrawdown: Double, // Largest drawdown observed
  val distribution: Map<String, DrawdownBucket>, // Distribution with cumulative %
  val totalWinningTrades: Int,
  val losingTradesStats: LosingTradesATRStats? = null, // ATR metrics for losing trades
)

/**
 * ATR statistics for losing trades.
 * Shows how deep losses went in ATR units for comparison with winning trades.
 */
data class LosingTradesATRStats(
  val medianLoss: Double, // Median ATR loss
  val meanLoss: Double, // Mean ATR loss
  val percentile25: Double, // 25th percentile
  val percentile50: Double, // 50th percentile (median)
  val percentile75: Double, // 75th percentile
  val percentile90: Double, // 90th percentile
  val percentile95: Double, // 95th percentile
  val percentile99: Double, // 99th percentile
  val minLoss: Double, // Smallest loss observed
  val maxLoss: Double, // Largest loss observed
  val distribution: Map<String, DrawdownBucket>, // Distribution with cumulative %
  val totalLosingTrades: Int,
)

/**
 * Bucket for ATR drawdown distribution.
 * Includes cumulative percentage for easy analysis.
 */
data class DrawdownBucket(
  val range: String, // "0.0-0.5", "0.5-1.0", etc.
  val count: Int, // Number of trades in this bucket
  val percentage: Double, // Percentage of total
  val cumulativePercentage: Double, // Running total (answers "X% fall under Y ATR")
)

/**
 * Exit reason analysis - breakdown by reason with stats.
 */
data class ExitReasonAnalysis(
  val byReason: Map<String, ExitStats>,
  val byYearAndReason: Map<Int, Map<String, Int>>, // Year -> Reason -> Count
)

/**
 * Statistics for a specific exit reason.
 */
data class ExitStats(
  val count: Int,
  val avgProfit: Double,
  val avgHoldingDays: Double,
  val winRate: Double,
)

/**
 * Performance statistics for a specific sector.
 */
data class SectorPerformance(
  val sector: String,
  val trades: Int,
  val winRate: Double,
  val avgProfit: Double,
  val avgHoldingDays: Double,
)

/**
 * Measures how consistent a strategy's edge is across yearly periods.
 * Score 0–100: 80+ Excellent, 60–79 Good, 40–59 Moderate, 20–39 Poor, <20 Very Poor.
 * Null when fewer than 2 years of data.
 */
data class EdgeConsistencyScore(
  val score: Double,
  val profitablePeriodsScore: Double,
  val stabilityScore: Double,
  val downsideScore: Double,
  val yearsAnalyzed: Int,
  val yearlyEdges: Map<Int, Double>,
  val interpretation: String,
)

/**
 * Calculate edge consistency across yearly periods.
 * Returns null when fewer than 2 years have trades.
 *
 * Formula: score = profitablePeriods × 0.4 + stability × 0.4 + downside × 0.2
 *
 * - Profitable Periods (0–100): % of years with edge > 0
 * - Stability (0–100): max(0, 100 × (1 − CV)) where CV = stdDev / |mean|
 * - Downside (0–100): 100 if worst ≥ 0, else 100 × (1 + worstEdge/10) clamped to 0
 */
fun calculateEdgeConsistency(yearlyStats: Map<Int, PeriodStats>): EdgeConsistencyScore? {
  // Filter out years with zero trades
  val validYears = yearlyStats.filter { it.value.trades > 0 }
  if (validYears.size < 2) return null

  val yearlyEdges = validYears.mapValues { it.value.edge }
  val edges = yearlyEdges.values.toList()

  // 1. Profitable Periods Score
  val profitableCount = edges.count { it > 0 }
  val profitablePeriodsScore = (profitableCount.toDouble() / edges.size) * 100

  // 2. Stability Score (based on Coefficient of Variation)
  val mean = edges.average()
  val variance = edges.map { (it - mean) * (it - mean) }.average()
  val stdDev = sqrt(variance)

  val stabilityScore =
    if (abs(mean) < 0.001) {
      // Near-zero mean edge
      if (stdDev < 0.001) 50.0 else 0.0
    } else {
      val cv = stdDev / abs(mean)
      (100.0 * (1.0 - cv)).coerceAtLeast(0.0)
    }

  // 3. Downside Score
  val worstEdge = edges.min()
  val downsideScore =
    if (worstEdge >= 0) {
      100.0
    } else {
      (100.0 * (1.0 + worstEdge / 10.0)).coerceAtLeast(0.0)
    }

  // Final score
  val score = profitablePeriodsScore * 0.4 + stabilityScore * 0.4 + downsideScore * 0.2

  val interpretation =
    when {
      score >= 80 -> "Excellent"
      score >= 60 -> "Good"
      score >= 40 -> "Moderate"
      score >= 20 -> "Poor"
      else -> "Very Poor"
    }

  return EdgeConsistencyScore(
    score = score,
    profitablePeriodsScore = profitablePeriodsScore,
    stabilityScore = stabilityScore,
    downsideScore = downsideScore,
    yearsAnalyzed = edges.size,
    yearlyEdges = yearlyEdges,
    interpretation = interpretation,
  )
}
