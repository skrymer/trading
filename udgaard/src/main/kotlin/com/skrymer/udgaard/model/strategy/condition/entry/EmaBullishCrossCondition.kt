package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that triggers when a faster EMA crosses above a slower EMA (bullish cross).
 *
 * This detects the actual crossover by checking:
 * - Current: Fast EMA > Slow EMA
 * - Previous: Fast EMA <= Slow EMA
 *
 * @param fastEma The faster EMA period (default 10)
 * @param slowEma The slower EMA period (default 20)
 */
@Component
class EmaBullishCrossCondition(
  private val fastEma: Int = 10,
  private val slowEma: Int = 20,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    // Get current EMA values
    val currentFast = getEmaValue(quote, fastEma)
    val currentSlow = getEmaValue(quote, slowEma)

    // If either value is 0, we can't evaluate
    if (currentFast == 0.0 || currentSlow == 0.0) {
      return false
    }

    // Check if currently fast > slow (bullish)
    if (currentFast <= currentSlow) {
      return false
    }

    // Get previous quote to check if this is a crossover
    val previousQuote = stock.getPreviousQuote(quote)
    if (previousQuote == null) {
      // If no previous quote, just check if currently bullish
      return currentFast > currentSlow
    }

    // Get previous EMA values
    val previousFast = getEmaValue(previousQuote, fastEma)
    val previousSlow = getEmaValue(previousQuote, slowEma)

    // Crossover detection: was bearish (fast <= slow), now bullish (fast > slow)
    return previousFast <= previousSlow && currentFast > currentSlow
  }

  override fun description(): String = "${fastEma}EMA crosses above ${slowEma}EMA"

  override fun getMetadata() =
    ConditionMetadata(
      type = "emaBullishCross",
      displayName = "EMA Bullish Cross",
      description = "Fast EMA crosses above slow EMA (bullish crossover)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "fastEma",
            displayName = "Fast EMA",
            type = "number",
            defaultValue = 10,
            options = listOf("5", "10", "20"),
          ),
          ParameterMetadata(
            name = "slowEma",
            displayName = "Slow EMA",
            type = "number",
            defaultValue = 20,
            options = listOf("10", "20", "50"),
          ),
        ),
      category = "Trend",
    )

  private fun getEmaValue(
    quote: StockQuote,
    period: Int,
  ): Double =
    when (period) {
      5 -> quote.closePriceEMA5
      10 -> quote.closePriceEMA10
      20 -> quote.closePriceEMA20
      50 -> quote.closePriceEMA50
      else -> 0.0
    }

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val message = if (passed) description() + " ✓" else description() + " ✗"

    return ConditionEvaluationResult(
      conditionType = "EmaBullishCrossCondition",
      description = description(),
      passed = passed,
      actualValue = null,
      threshold = null,
      message = message,
    )
  }
}
