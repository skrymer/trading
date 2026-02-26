package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that requires price to be within [maxDistancePercent] of
 * the Donchian upper band.
 *
 * Winners tend to enter closer to the Donchian high (1.30% headroom) compared
 * to never-green losers (1.69% headroom). Entering near new highs indicates
 * genuine breakout momentum.
 */
@Component
class PriceNearDonchianHighCondition(
  private val maxDistancePercent: Double = 1.5,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    if (quote.donchianUpperBand <= 0 || quote.closePrice <= 0) return false
    val distance = (quote.donchianUpperBand - quote.closePrice) / quote.closePrice * 100
    return distance <= maxDistancePercent
  }

  override fun description(): String =
    "Price near Donchian high (within ${"%.1f".format(maxDistancePercent)}%)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "priceNearDonchianHigh",
      displayName = "Price Near Donchian High",
      description = "Price is within a percentage of the Donchian upper band (breakout proximity)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "maxDistancePercent",
            displayName = "Max Distance %",
            type = "number",
            defaultValue = 1.5,
            min = 0,
            max = 10,
          ),
        ),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val distance =
      if (quote.donchianUpperBand > 0 && quote.closePrice > 0) {
        (quote.donchianUpperBand - quote.closePrice) / quote.closePrice * 100
      } else {
        0.0
      }

    return ConditionEvaluationResult(
      conditionType = "PriceNearDonchianHighCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.2f".format(distance)}% below Donchian high",
      threshold = "≤${"%.1f".format(maxDistancePercent)}%",
      message =
        if (passed) {
          "Price ${"%.2f".format(distance)}% from Donchian high (within threshold)"
        } else {
          "Price ${"%.2f".format(distance)}% from Donchian high (too far)"
        },
    )
  }
}
