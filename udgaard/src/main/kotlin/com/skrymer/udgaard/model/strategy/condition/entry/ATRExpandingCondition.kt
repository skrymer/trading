package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if ATR is expanding using ATR Percentile Rank.
 *
 * ATR Percentile Rank measures where the current ATR sits relative to its historical range:
 * - ATR Rank < 30%  → Compressed (low volatility)
 * - ATR Rank > 30% and rising → Expanding (entry signal)
 * - ATR Rank > 70%  → Extended (late to enter)
 *
 * This condition returns true when:
 * 1. Current ATR Percentile Rank is between minPercentile and maxPercentile (sweet spot for expansion)
 * 2. ATR is rising compared to the previous period
 *
 * Lookback period: 252 trading days (approximately 1 year)
 */
@Component
class ATRExpandingCondition(
  private val minPercentile: Double = 30.0,
  private val maxPercentile: Double = 70.0,
  private val lookbackPeriod: Int = 252,
) : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean {
    val currentATR = quote.atr

    // Get historical ATR values for percentile rank calculation
    val historicalATRs = getHistoricalATRs(stock, quote, lookbackPeriod)
    if (historicalATRs.size < 30) return false // Need minimum data points

    // Calculate percentile rank
    val percentileRank = calculatePercentileRank(currentATR, historicalATRs)

    // Check if ATR is rising
    val previousQuote = stock.getPreviousQuote(quote)
    val previousATR = previousQuote?.atr ?: return false
    val isRising = currentATR > previousATR

    // Entry signal: ATR Rank between minPercentile and maxPercentile, and rising
    return percentileRank > minPercentile && percentileRank <= maxPercentile && isRising
  }

  override fun description(): String =
    "ATR expanding (${"%.0f".format(minPercentile)}% < rank ≤ ${"%.0f".format(maxPercentile)}% and rising)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "atrExpanding",
      displayName = "ATR Expanding",
      description = "ATR percentile rank is between thresholds and rising (volatility expanding)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minPercentile",
            displayName = "Min Percentile",
            type = "number",
            defaultValue = 30.0,
            min = 0,
            max = 100,
          ),
          ParameterMetadata(
            name = "maxPercentile",
            displayName = "Max Percentile",
            type = "number",
            defaultValue = 70.0,
            min = 0,
            max = 100,
          ),
          ParameterMetadata(
            name = "lookbackPeriod",
            displayName = "Lookback Period (Days)",
            type = "number",
            defaultValue = 252,
            min = 30,
            max = 500,
          ),
        ),
      category = "Volatility",
    )

  override fun evaluateWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val currentATR = quote.atr

    val historicalATRs = getHistoricalATRs(stock, quote, lookbackPeriod)
    if (historicalATRs.size < 30) {
      return ConditionEvaluationResult(
        conditionType = "ATRExpandingCondition",
        description = description(),
        passed = false,
        actualValue = "N/A",
        threshold = "${"%.0f".format(minPercentile)}% < rank ≤ ${"%.0f".format(maxPercentile)}% and rising",
        message = "Insufficient historical data (${historicalATRs.size} < 30 days) ✗",
      )
    }

    val percentileRank = calculatePercentileRank(currentATR, historicalATRs)

    val previousQuote = stock.getPreviousQuote(quote)
    val previousATR = previousQuote?.atr ?: return ConditionEvaluationResult(
      conditionType = "ATRExpandingCondition",
      description = description(),
      passed = false,
      actualValue = "${"%.1f".format(percentileRank)}%",
      threshold = "${"%.0f".format(minPercentile)}% < rank ≤ ${"%.0f".format(maxPercentile)}% and rising",
      message = "Previous ATR not available ✗",
    )

    val isRising = currentATR > previousATR
    val inRange = percentileRank > minPercentile && percentileRank <= maxPercentile
    val passed = inRange && isRising

    val status =
      when {
        percentileRank <= minPercentile -> "Compressed (${String.format("%.1f", percentileRank)}%)"
        percentileRank > maxPercentile -> "Extended (${String.format("%.1f", percentileRank)}%)"
        !isRising -> "In range but not rising (${String.format("%.1f", percentileRank)}%)"
        else -> "Expanding (${String.format("%.1f", percentileRank)}%)"
      }

    val message =
      if (passed) {
        "ATR expanding: rank ${String.format("%.1f", percentileRank)}% (${
          String.format(
            "%.2f",
            previousATR,
          )
        } → ${String.format("%.2f", currentATR)}) ✓"
      } else {
        "ATR not expanding: $status ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "ATRExpandingCondition",
      description = description(),
      passed = passed,
      actualValue = "${String.format("%.1f", percentileRank)}% (${if (isRising) "rising" else "falling"})",
      threshold = "${"%.0f".format(minPercentile)}% < rank ≤ ${"%.0f".format(maxPercentile)}% and rising",
      message = message,
    )
  }

  /**
   * Get historical ATR values for percentile rank calculation.
   * Returns ATR values from the last N trading days including the current quote.
   */
  private fun getHistoricalATRs(
    stock: StockDomain,
    currentQuote: StockQuoteDomain,
    periods: Int,
  ): List<Double> =
    stock.quotes
      .filter { it.date <= currentQuote.date }
      .sortedByDescending { it.date }
      .take(periods)
      .map { it.atr }

  /**
   * Calculate percentile rank: what percentage of historical values are below the current value.
   *
   * Formula: (Number of values below current / Total values) × 100
   *
   * Examples:
   * - Rank 0%   → Current ATR is the lowest in the period
   * - Rank 50%  → Current ATR is at the median
   * - Rank 100% → Current ATR is the highest in the period
   */
  private fun calculatePercentileRank(
    currentValue: Double,
    historicalValues: List<Double>,
  ): Double {
    if (historicalValues.isEmpty()) return 0.0

    val countBelow = historicalValues.count { it < currentValue }
    return (countBelow.toDouble() / historicalValues.size) * 100.0
  }
}
