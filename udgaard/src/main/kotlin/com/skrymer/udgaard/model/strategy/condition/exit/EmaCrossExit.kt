package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when a faster EMA crosses under a slower EMA.
 *
 * This detects the actual crossover event:
 * - Previous day: fast EMA >= slow EMA
 * - Current day: fast EMA < slow EMA
 *
 * It's okay for the slow EMA to already be above the fast EMA (position exists),
 * but the trigger is specifically when the crossover happens.
 */
@Component
class EmaCrossExit(
  private val fastEma: Int = 10,
  private val slowEma: Int = 20,
) : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean {
    // Current day: fast EMA must be below slow EMA
    val currentFastValue = getEmaValue(quote, fastEma)
    val currentSlowValue = getEmaValue(quote, slowEma)

    if (currentFastValue >= currentSlowValue) {
      return false // Not crossed yet
    }

    // Get previous quote to check for crossover
    val previousQuote = stock.getPreviousQuote(quote) ?: return false

    // Previous day: fast EMA must have been above or equal to slow EMA
    val previousFastValue = getEmaValue(previousQuote, fastEma)
    val previousSlowValue = getEmaValue(previousQuote, slowEma)

    // Crossover occurred if fast was >= slow yesterday and < slow today
    return previousFastValue >= previousSlowValue
  }

  override fun exitReason(): String = "$fastEma ema has crossed under the $slowEma ema"

  override fun description(): String = "${fastEma}EMA crosses under ${slowEma}EMA"

  override fun evaluateWithDetails(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val currentFastValue = getEmaValue(quote, fastEma)
    val currentSlowValue = getEmaValue(quote, slowEma)
    val currentBelowOrEqual = currentFastValue < currentSlowValue

    val previousQuote = stock.getPreviousQuote(quote)
    val previousFastValue = previousQuote?.let { getEmaValue(it, fastEma) }
    val previousSlowValue = previousQuote?.let { getEmaValue(it, slowEma) }
    val previousAboveOrEqual = previousFastValue != null && previousSlowValue != null && previousFastValue >= previousSlowValue

    val passed = currentBelowOrEqual && previousAboveOrEqual

    val prevStr =
      if (previousQuote == null) {
        "No previous quote ✗"
      } else if (previousAboveOrEqual) {
        "Prev: EMA$fastEma(${"%.2f".format(previousFastValue)}) >= EMA$slowEma(${"%.2f".format(previousSlowValue)}) ✓"
      } else {
        "Prev: EMA$fastEma(${"%.2f".format(previousFastValue)}) < EMA$slowEma(${"%.2f".format(previousSlowValue)}) ✗ (already crossed)"
      }

    val nowStr =
      if (currentBelowOrEqual) {
        "Now: EMA$fastEma(${"%.2f".format(currentFastValue)}) < EMA$slowEma(${"%.2f".format(currentSlowValue)}) ✓"
      } else {
        "Now: EMA$fastEma(${"%.2f".format(currentFastValue)}) >= EMA$slowEma(${"%.2f".format(currentSlowValue)}) ✗"
      }

    val crossoverStr = if (passed) " -> Crossover" else ""

    return ConditionEvaluationResult(
      conditionType = "EmaCrossExit",
      description = description(),
      passed = passed,
      actualValue = "EMA$fastEma=${"%.2f".format(currentFastValue)}, EMA$slowEma=${"%.2f".format(currentSlowValue)}",
      threshold = "EMA$fastEma crosses below EMA$slowEma",
      message = "$prevStr, $nowStr$crossoverStr",
    )
  }

  override fun getMetadata() =
    ConditionMetadata(
      type = "emaCross",
      displayName = "EMA Crossover",
      description = "Exit when fast EMA crosses below slow EMA",
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
    quote: StockQuoteDomain,
    period: Int,
  ): Double =
    when (period) {
      5 -> quote.closePriceEMA5
      10 -> quote.closePriceEMA10
      20 -> quote.closePriceEMA20
      50 -> quote.closePriceEMA50
      else -> 0.0
    }
}
