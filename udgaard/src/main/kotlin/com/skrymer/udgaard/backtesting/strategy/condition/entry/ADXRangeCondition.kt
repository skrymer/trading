package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if ADX is within a specified range.
 *
 * ADX (Average Directional Index) measures trend strength (not direction):
 * - 0-20: Weak or absent trend (ranging market)
 * - 20-25: Emerging trend
 * - 25-50: Strong trend (ideal for trend-following)
 * - 50-75: Very strong trend
 * - 75-100: Extremely strong trend (potential exhaustion)
 *
 * This condition allows filtering for optimal trend strength ranges.
 * For example:
 * - minADX=20, maxADX=50: Strong trending markets without exhaustion
 * - minADX=25, maxADX=100: Only strong trends
 * - minADX=0, maxADX=20: Ranging/choppy markets (for mean-reversion)
 *
 * @param minADX Minimum ADX value (default: 20)
 * @param maxADX Maximum ADX value (default: 50)
 */
@Component
class ADXRangeCondition(
  private val minADX: Double = 20.0,
  private val maxADX: Double = 50.0,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    val adx = quote.adx ?: return false
    return adx >= minADX && adx <= maxADX
  }

  override fun description(): String = when {
    minADX == 0.0 && maxADX >= 100.0 -> "ADX available"
    minADX == 0.0 -> "ADX ≤ ${"%.0f".format(maxADX)}"
    maxADX >= 100.0 -> "ADX ≥ ${"%.0f".format(minADX)}"
    else -> "${"%.0f".format(minADX)} ≤ ADX ≤ ${"%.0f".format(maxADX)}"
  }

  override fun getMetadata() =
    ConditionMetadata(
      type = "adxRange",
      displayName = "ADX Range",
      description = "ADX (trend strength) is within specified range",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minADX",
            displayName = "Minimum ADX",
            type = "number",
            defaultValue = 20.0,
            min = 0,
            max = 100,
          ),
          ParameterMetadata(
            name = "maxADX",
            displayName = "Maximum ADX",
            type = "number",
            defaultValue = 50.0,
            min = 0,
            max = 100,
          ),
        ),
      category = "Trend Strength",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val adx = quote.adx

    if (adx == null) {
      return ConditionEvaluationResult(
        conditionType = "ADXRangeCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "ADX not available ✗",
      )
    }

    val passed = adx >= minADX && adx <= maxADX

    val trendStrength =
      when {
        adx < 20 -> "weak/ranging"
        adx < 25 -> "emerging"
        adx < 50 -> "strong"
        adx < 75 -> "very strong"
        else -> "extreme"
      }

    val message =
      if (passed) {
        "ADX: ${"%.1f".format(adx)} ($trendStrength) ✓"
      } else {
        val reason =
          when {
            adx < minADX -> "below minimum ${"%.0f".format(minADX)}"
            adx > maxADX -> "above maximum ${"%.0f".format(maxADX)}"
            else -> "outside range"
          }
        "ADX: ${"%.1f".format(adx)} ($trendStrength, $reason) ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "ADXRangeCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.1f".format(adx)}",
      threshold = "${"%.0f".format(minADX)}-${"%.0f".format(maxADX)}",
      message = message,
    )
  }
}
