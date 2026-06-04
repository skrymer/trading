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
 * Entry condition that passes when market breadth hit an *absolute* crisis floor — bull-list
 * percentage at or below `threshold` — on any reading within the trailing `lookbackDays` window
 * at or before `quote.date`.
 *
 * A bounded *memory* primitive for capitulation reversals: it remembers a recent absolute washout
 * so a strategy can fire on the recovery bar (when breadth has climbed back above the floor).
 * Unlike a Donchian-relative low (a range-local extreme that recurs at every minor trough), an
 * absolute floor on `breadthPercent` only fires in genuine market-wide capitulations.
 *
 * Window inclusion: the current bar's reading is included; partial windows are allowed (early
 * history checks whatever readings exist, up to `lookbackDays`). Future readings (after
 * `quote.date`) are never visible — the breadth map is filtered to `<= quote.date`.
 *
 * @param threshold Absolute breadth bull-% at or below which a reading counts as a washout. Default 15.0.
 * @param lookbackDays How many trailing breadth readings to remember (>= 1). Default 30.
 */
@Component
class MarketBreadthAbsoluteWashoutWithinCondition(
  private val threshold: Double = 15.0,
  private val lookbackDays: Int = 30,
) : EntryCondition {
  init {
    require(lookbackDays >= 1) { "lookbackDays must be >= 1, got $lookbackDays" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = recentReadings(context, quote).any { it.breadthPercent <= threshold }

  private fun recentReadings(context: BacktestContext, quote: StockQuote): List<MarketBreadthDaily> =
    context.marketBreadthMap
      .filterKeys { !it.isAfter(quote.date) }
      .toSortedMap()
      .values
      .toList()
      .takeLast(lookbackDays)

  override fun description(): String =
    "Market breadth washed out below $threshold% within last $lookbackDays days"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthAbsoluteWashoutWithin",
      displayName = "Market Breadth Absolute Washout Within",
      description = "Market breadth bull-% fell to an absolute crisis floor within the last N trading days (capitulation memory)",
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
            name = "lookbackDays",
            displayName = "Lookback Days",
            type = "number",
            defaultValue = lookbackDays,
            min = 1,
            max = 90,
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
    val washout = readings.lastOrNull { it.breadthPercent <= threshold }
    val passed = washout != null

    val message =
      if (passed) {
        "Market breadth hit an absolute washout (<= $threshold%) within last $lookbackDays days " +
          "(last %.1f%% on %s)".format(washout.breadthPercent, washout.quoteDate)
      } else {
        "No absolute breadth washout (<= $threshold%) in the last $lookbackDays readings"
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthAbsoluteWashoutWithinCondition",
      description = description(),
      passed = passed,
      actualValue = washout?.let { "%.1f%% on %s".format(it.breadthPercent, it.quoteDate) } ?: "no washout",
      threshold = "<= $threshold% within $lookbackDays days",
      message = message,
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    MarketBreadthAbsoluteWashoutWithinCondition(
      threshold = parameters.numberOr("threshold", threshold),
      lookbackDays = parameters.intOr("lookbackDays", lookbackDays),
    )
}
