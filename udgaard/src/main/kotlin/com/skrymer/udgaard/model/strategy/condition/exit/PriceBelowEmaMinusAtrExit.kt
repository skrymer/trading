package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when a RED CANDLE's low price breaches EMA minus ATR.
 *
 * This creates a dynamic trailing stop with two requirements:
 * 1. Must be a red candle (close < open) - indicating bearish pressure
 * 2. The LOW of the candle must fall below EMA minus an ATR buffer
 *
 * This is more conservative than checking the close price, as it only exits when:
 * - Bearish momentum is present (red candle), AND
 * - Price has actually broken through the support level during the day
 *
 * Example with 5 EMA and 0.5 ATR:
 * - 5 EMA = $100, ATR = $4
 * - Exit threshold = $100 - (0.5 × $4) = $98
 *
 * Scenario 1 (NO EXIT - green candle):
 * - Open: $99, Low: $97, Close: $100 -> No exit (green candle)
 *
 * Scenario 2 (NO EXIT - red but low above threshold):
 * - Open: $100, Low: $98.50, Close: $99 -> No exit (low didn't breach $98)
 *
 * Scenario 3 (EXIT):
 * - Open: $100, Low: $97, Close: $98 -> EXIT (red candle AND low below $98)
 *
 * Use cases:
 * - Tight trailing stop: emaPeriod=5, atrMultiplier=0.5
 * - Looser trailing stop: emaPeriod=10, atrMultiplier=1.0
 * - Very loose: emaPeriod=20, atrMultiplier=2.0
 *
 * @param emaPeriod EMA period to use as base level (default 5)
 * @param atrMultiplier ATR distance below EMA to trigger exit (default 0.5)
 */
@Component
class PriceBelowEmaMinusAtrExit(
  private val emaPeriod: Int = 5,
  private val atrMultiplier: Double = 0.5,
) : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean {
    // Must be a red candle (close < open)
    if (quote.closePrice >= quote.openPrice) {
      return false // Green candle or doji - no exit
    }

    // Check if low breached the threshold
    val emaValue = getEmaValue(quote, emaPeriod)
    val exitThreshold = emaValue - (atrMultiplier * quote.atr)

    return quote.low < exitThreshold
  }

  /**
   * Helper function to get the EMA value for the specified period
   */
  private fun getEmaValue(
    quote: StockQuoteDomain,
    period: Int,
  ): Double =
    when (period) {
      5 -> quote.closePriceEMA5
      10 -> quote.closePriceEMA10
      20 -> quote.closePriceEMA20
      50 -> quote.closePriceEMA50
      100 -> quote.closePriceEMA100
      200 -> 0.0 // 200 EMA not yet implemented
      else -> quote.closePriceEMA20 // Default to 20 EMA
    }

  override fun exitReason(): String = "Red candle with low below ${emaPeriod}EMA - ${atrMultiplier}ATR"

  override fun description(): String = "Red candle with low below ${emaPeriod}EMA - ${atrMultiplier}ATR"

  override fun evaluateWithDetails(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val isRedCandle = quote.closePrice < quote.openPrice
    val emaValue = getEmaValue(quote, emaPeriod)
    val exitThreshold = emaValue - (atrMultiplier * quote.atr)
    val lowBelowThreshold = quote.low < exitThreshold
    val passed = isRedCandle && lowBelowThreshold

    val redCandleStr =
      if (isRedCandle) {
        "Red candle (close ${"%.2f".format(quote.closePrice)} < open ${"%.2f".format(quote.openPrice)}) ✓"
      } else {
        "Green candle (close ${"%.2f".format(quote.closePrice)} >= open ${"%.2f".format(quote.openPrice)}) ✗"
      }
    val thresholdStr =
      if (lowBelowThreshold) {
        "Low ${"%.2f".format(quote.low)} < threshold ${"%.2f".format(exitThreshold)} ✓"
      } else {
        "Low ${"%.2f".format(quote.low)} >= threshold ${"%.2f".format(exitThreshold)} ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "PriceBelowEmaMinusAtrExit",
      description = description(),
      passed = passed,
      actualValue = "low=${"%.2f".format(quote.low)}, close=${"%.2f".format(quote.closePrice)}, open=${"%.2f".format(quote.openPrice)}",
      threshold = "low < EMA$emaPeriod(${"%.2f".format(emaValue)}) - ${atrMultiplier}xATR(${"%.2f".format(quote.atr)}) = ${"%.2f".format(exitThreshold)}",
      message = "$redCandleStr, $thresholdStr",
    )
  }

  override fun getMetadata() =
    ConditionMetadata(
      type = "priceBelowEmaMinusAtr",
      displayName = "Red Candle Low Below EMA Minus ATR",
      description = "Exit when a red candle's low falls below EMA minus an ATR buffer",
      parameters =
        listOf(
          ParameterMetadata(
            name = "emaPeriod",
            displayName = "EMA Period",
            type = "number",
            defaultValue = 5,
            options = listOf("5", "10", "20", "50", "100"),
          ),
          ParameterMetadata(
            name = "atrMultiplier",
            displayName = "ATR Multiplier",
            type = "number",
            defaultValue = 0.5,
            min = 0.1,
            max = 5.0,
          ),
        ),
      category = "StopLoss",
    )
}
