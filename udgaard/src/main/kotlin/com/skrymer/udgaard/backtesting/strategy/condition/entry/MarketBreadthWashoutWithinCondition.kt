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
 * Entry condition that passes when market breadth was at a Donchian-low washout extreme
 * on any reading within the trailing `lookbackDays` window at or before `quote.date`.
 *
 * A bounded *memory* primitive: it remembers a recent washout so a strategy can fire on the
 * recovery bar (when breadth has already climbed off the floor and no longer reads as a
 * washout itself). The washout test reuses the Donchian-low threshold:
 * `breadthPercent <= donchianLowerBand + (upper - lower) * percentile`.
 *
 * Window inclusion: the current bar's reading is included; partial windows are allowed
 * (early history checks whatever readings exist, up to `lookbackDays`). Future readings
 * (after `quote.date`) are never visible — the breadth map is filtered to `<= quote.date`.
 *
 * @param percentile How close to the Donchian low counts as a washout (0.0-1.0). Default 0.10.
 * @param lookbackDays How many trailing breadth readings to remember (>= 1). Default 15.
 */
@Component
class MarketBreadthWashoutWithinCondition(
  private val percentile: Double = 0.10,
  private val lookbackDays: Int = 15,
) : EntryCondition {
  init {
    require(lookbackDays >= 1) { "lookbackDays must be >= 1, got $lookbackDays" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = recentReadings(context, quote).any { it.isWashout() }

  private fun recentReadings(context: BacktestContext, quote: StockQuote): List<MarketBreadthDaily> =
    context.marketBreadthMap
      .filterKeys { !it.isAfter(quote.date) }
      .toSortedMap()
      .values
      .toList()
      .takeLast(lookbackDays)

  private fun MarketBreadthDaily.isWashout(): Boolean {
    val range = donchianUpperBand - donchianLowerBand
    if (range <= 0) return false
    return breadthPercent <= donchianLowerBand + range * percentile
  }

  override fun description(): String =
    "Market breadth washed out (bottom ${(percentile * 100).toInt()}%) within last $lookbackDays days"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthWashoutWithin",
      displayName = "Market Breadth Washout Within",
      description = "Market breadth was near its Donchian low within the last N trading days (recent-washout memory)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "percentile",
            displayName = "Percentile",
            type = "number",
            defaultValue = percentile,
            min = 0.0,
            max = 1.0,
          ),
          ParameterMetadata(
            name = "lookbackDays",
            displayName = "Lookback Days",
            type = "number",
            defaultValue = lookbackDays,
            min = 1,
            max = 60,
          ),
        ),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val readings = recentReadings(context, quote)
    val washout = readings.lastOrNull { it.isWashout() }
    val passed = washout != null

    val message =
      if (passed) {
        "Market breadth washed out within last $lookbackDays days (last washout %.1f%% on %s)".format(
          washout.breadthPercent,
          washout.quoteDate,
        )
      } else {
        "No market breadth washout in the last $lookbackDays readings"
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthWashoutWithinCondition",
      description = description(),
      passed = passed,
      actualValue = washout?.let { "%.1f%% on %s".format(it.breadthPercent, it.quoteDate) } ?: "no washout",
      threshold = "washout within $lookbackDays days",
      message = message,
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    MarketBreadthWashoutWithinCondition(
      percentile = parameters.numberOr("percentile", percentile),
      lookbackDays = parameters.intOr("lookbackDays", lookbackDays),
    )
}
