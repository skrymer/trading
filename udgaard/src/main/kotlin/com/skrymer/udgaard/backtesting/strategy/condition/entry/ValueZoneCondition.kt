package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Condition that checks if price is within the value zone.
 * Value zone is defined as being above EMA and below EMA + (ATR multiplier * ATR).
 */
@Component
class ValueZoneCondition(
  private val atrMultiplier: Double = 2.0,
  private val emaPeriod: Int = 20,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    val emaValue = getEmaValue(quote, emaPeriod)
    return quote.closePrice > emaValue &&
      quote.closePrice < (emaValue + (atrMultiplier * quote.atr))
  }

  override fun description(): String = "Price within value zone (${emaPeriod}EMA < price < ${emaPeriod}EMA + ${atrMultiplier}ATR)"

  /**
   * Helper function to get the EMA value for the specified period
   */
  private fun getEmaValue(
    quote: StockQuote,
    period: Int,
  ): Double =
    when (period) {
      5 -> quote.closePriceEMA5
      10 -> quote.closePriceEMA10
      20 -> quote.closePriceEMA20
      50 -> quote.closePriceEMA50
      100 -> quote.closePriceEMA100
      200 -> 0.0 // 200 EMA not yet implemented, return 0 for now
      else -> quote.closePriceEMA20 // Default to 20 EMA if invalid period
    }

  override fun getMetadata() =
    ConditionMetadata(
      type = "valueZone",
      displayName = "In Value Zone",
      description = "Price is within value zone (EMA to EMA + ATR multiplier)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "emaPeriod",
            displayName = "EMA Period",
            type = "number",
            defaultValue = 20,
            options = listOf("5", "10", "20", "50", "100", "200"),
          ),
          ParameterMetadata(
            name = "atrMultiplier",
            displayName = "ATR Multiplier",
            type = "number",
            defaultValue = 2.0,
            min = 0.5,
            max = 5.0,
          ),
        ),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val price = quote.closePrice
    val emaValue = getEmaValue(quote, emaPeriod)
    val atr = quote.atr
    val upperBound = emaValue + (atrMultiplier * atr)

    val aboveEma = price > emaValue
    val belowUpperBound = price < upperBound
    val passed = aboveEma && belowUpperBound

    val message =
      buildString {
        append("Price ${"%.2f".format(price)} ")
        if (aboveEma) {
          append("> EMA$emaPeriod (${"%.2f".format(emaValue)}) ✓")
        } else {
          append("≤ EMA$emaPeriod (${"%.2f".format(emaValue)}) ✗")
        }
        append(" AND ")
        if (belowUpperBound) {
          append("< Upper Bound (${"%.2f".format(upperBound)}) ✓")
        } else {
          append("≥ Upper Bound (${"%.2f".format(upperBound)}) ✗")
        }
      }

    return ConditionEvaluationResult(
      conditionType = "ValueZoneCondition",
      description = description(),
      passed = passed,
      actualValue = "%.2f".format(price),
      threshold = "%.2f - %.2f".format(emaValue, upperBound),
      message = message,
    )
  }
}
