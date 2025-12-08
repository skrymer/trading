package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 * Performance statistics grouped by time periods (year, quarter, month).
 * Strategy-agnostic - works with any backtest.
 */
data class TimeBasedStats(
    val byYear: Map<Int, PeriodStats>,
    val byQuarter: Map<String, PeriodStats>,  // "2025-Q1"
    val byMonth: Map<String, PeriodStats>     // "2025-01"
)

/**
 * Performance statistics for a specific time period.
 */
data class PeriodStats(
    val trades: Int,
    val winRate: Double,
    val avgProfit: Double,
    val avgHoldingDays: Double,
    val exitReasons: Map<String, Int>
)

/**
 * Snapshot of market conditions at trade entry.
 * Helps identify if poor performance correlates with market state.
 */
data class MarketConditionSnapshot(
    val spyClose: Double,
    val spyHeatmap: Double?,                    // Nullable - may not exist
    val spyInUptrend: Boolean,
    val marketBreadthBullPercent: Double?,      // Nullable - may not exist
    val entryDate: LocalDate
)

/**
 * Trade excursion metrics - how much profit/loss reached during trade.
 * Includes ATR-normalized drawdown analysis.
 */
data class ExcursionMetrics(
    val maxFavorableExcursion: Double,          // Highest % profit reached
    val maxFavorableExcursionATR: Double,       // In ATR units
    val maxAdverseExcursion: Double,            // Deepest % drawdown (negative)
    val maxAdverseExcursionATR: Double,         // In ATR units (positive value)
    val mfeReached: Boolean                     // Did trade reach positive territory?
)

/**
 * ATR drawdown statistics for winning trades.
 * Strategy-agnostic - no assumptions about stop loss levels.
 * Users can interpret percentiles based on their specific strategy.
 */
data class ATRDrawdownStats(
    val medianDrawdown: Double,                 // Median ATR drawdown
    val meanDrawdown: Double,                   // Mean ATR drawdown
    val percentile25: Double,                   // 25th percentile
    val percentile50: Double,                   // 50th percentile (median)
    val percentile75: Double,                   // 75th percentile
    val percentile90: Double,                   // 90th percentile
    val percentile95: Double,                   // 95th percentile
    val percentile99: Double,                   // 99th percentile
    val minDrawdown: Double,                    // Smallest drawdown observed
    val maxDrawdown: Double,                    // Largest drawdown observed
    val distribution: Map<String, DrawdownBucket>,  // Distribution with cumulative %
    val totalWinningTrades: Int
)

/**
 * Bucket for ATR drawdown distribution.
 * Includes cumulative percentage for easy analysis.
 */
data class DrawdownBucket(
    val range: String,                          // "0.0-0.5", "0.5-1.0", etc.
    val count: Int,                             // Number of trades in this bucket
    val percentage: Double,                     // Percentage of total
    val cumulativePercentage: Double            // Running total (answers "X% fall under Y ATR")
)

/**
 * Exit reason analysis - breakdown by reason with stats.
 */
data class ExitReasonAnalysis(
    val byReason: Map<String, ExitStats>,
    val byYearAndReason: Map<Int, Map<String, Int>>  // Year -> Reason -> Count
)

/**
 * Statistics for a specific exit reason.
 */
data class ExitStats(
    val count: Int,
    val avgProfit: Double,
    val avgHoldingDays: Double,
    val winRate: Double
)

/**
 * Performance statistics for a specific sector.
 */
data class SectorPerformance(
    val sector: String,
    val trades: Int,
    val winRate: Double,
    val avgProfit: Double,
    val avgHoldingDays: Double
)
