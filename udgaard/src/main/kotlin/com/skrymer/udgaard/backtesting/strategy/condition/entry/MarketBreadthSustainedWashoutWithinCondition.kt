package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.intOr
import com.skrymer.udgaard.backtesting.service.numberOr
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that passes when market breadth held at or below an absolute floor for at least
 * `consecutiveDays` consecutive readings within the trailing `lookbackDays` window at or before
 * `quote.date`.
 *
 * A *sustained-depth* capitulation memory. Distinct from a single-touch washout: on a short-horizon
 * breadth oscillator a single low touch is routine (≤15% ≈ the 7th percentile, seen every year), so
 * only a sustained run isolates genuine crises (≤15% for ≥10 consecutive days ≈ 17 episodes over
 * 2000-2026). The strategy fires on the *recovery* bar once the run has ended and breadth has lifted.
 *
 * Window inclusion: the current bar's reading is included; partial windows are allowed; a run that
 * extends before the window is counted only over its in-window portion. Future readings (after
 * `quote.date`) are never visible.
 *
 * @param threshold Absolute breadth bull-% at or below which a reading is part of a washout. Default 15.0.
 * @param consecutiveDays Required length of the consecutive sub-floor run (>= 1). Default 10.
 * @param lookbackDays How many trailing breadth readings to scan (>= 1). Default 40.
 */
@Component
class MarketBreadthSustainedWashoutWithinCondition(
  private val threshold: Double = 15.0,
  private val consecutiveDays: Int = 10,
  private val lookbackDays: Int = 40,
) : EntryCondition {
  init {
    require(consecutiveDays >= 1) { "consecutiveDays must be >= 1, got $consecutiveDays" }
    require(lookbackDays >= 1) { "lookbackDays must be >= 1, got $lookbackDays" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = longestSubFloorRun(recentReadings(context, quote)) >= consecutiveDays

  private fun recentReadings(context: BacktestContext, quote: StockQuote): List<MarketBreadthDaily> =
    context.marketBreadthMap
      .filterKeys { !it.isAfter(quote.date) }
      .toSortedMap()
      .values
      .toList()
      .takeLast(lookbackDays)

  private fun longestSubFloorRun(readings: List<MarketBreadthDaily>): Int {
    var longest = 0
    var current = 0
    for (reading in readings) {
      if (reading.breadthPercent <= threshold) {
        current++
        if (current > longest) longest = current
      } else {
        current = 0
      }
    }
    return longest
  }

  override fun description(): String =
    "Market breadth held <= $threshold% for >= $consecutiveDays consecutive days within last $lookbackDays days"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthSustainedWashoutWithin",
      displayName = "Market Breadth Sustained Washout Within",
      description =
        "Market breadth held at an absolute crisis floor for N consecutive days " +
          "within the last M days (sustained capitulation memory)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "threshold",
            displayName = "Threshold",
            type = "number",
            defaultValue = threshold,
            min = 0.0,
            max = 100.0,
          ),
          ParameterMetadata(
            name = "consecutiveDays",
            displayName = "Consecutive Days",
            type = "number",
            defaultValue = consecutiveDays,
            min = 1,
            max = 60,
          ),
          ParameterMetadata(
            name = "lookbackDays",
            displayName = "Lookback Days",
            type = "number",
            defaultValue = lookbackDays,
            min = 1,
            max = 120,
          ),
        ),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    return ConditionEvaluationResult(
      conditionType = "MarketBreadthSustainedWashoutWithinCondition",
      description = description(),
      passed = passed,
      actualValue = if (passed) "sustained washout present" else "no sustained washout",
      threshold = "<= $threshold% for >= $consecutiveDays days within $lookbackDays days",
      message =
        if (passed) {
          "Market breadth held a sustained washout within last $lookbackDays days"
        } else {
          "No sustained breadth washout (>= $consecutiveDays consecutive days <= $threshold%) in the last $lookbackDays readings"
        },
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    MarketBreadthSustainedWashoutWithinCondition(
      threshold = parameters.numberOr("threshold", threshold),
      consecutiveDays = parameters.intOr("consecutiveDays", consecutiveDays),
      lookbackDays = parameters.intOr("lookbackDays", lookbackDays),
    )
}
