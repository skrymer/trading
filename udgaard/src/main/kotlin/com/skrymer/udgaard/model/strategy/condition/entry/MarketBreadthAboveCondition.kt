package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if market breadth is above a specific threshold.
 * Market breadth is measured as the percentage of stocks advancing (above their 10 EMA).
 *
 * @param threshold The minimum market breadth percentage required (0.0 to 100.0)
 */
@Component
class MarketBreadthAboveCondition(
  private val threshold: Double = 50.0,
) : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean = quote.marketAdvancingPercent >= threshold

  override fun description(): String = "Market breadth above ${threshold.toInt()}%"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthAbove",
      displayName = "Market Breadth Above Threshold",
      description = "Market breadth (% of stocks advancing) is above threshold",
      parameters =
        listOf(
          ParameterMetadata(
            name = "threshold",
            displayName = "Threshold",
            type = "number",
            defaultValue = 50.0,
            min = 0,
            max = 100,
          ),
        ),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val actualBreadth = quote.marketAdvancingPercent

    val message =
      if (passed) {
        "Market breadth is %.1f%% (≥ %.0f%%) ✓".format(actualBreadth, threshold)
      } else {
        "Market breadth is %.1f%% (requires ≥ %.0f%%) ✗".format(actualBreadth, threshold)
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthAboveCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f%%".format(actualBreadth),
      threshold = "≥ %.0f%%".format(threshold),
      message = message,
    )
  }
}
