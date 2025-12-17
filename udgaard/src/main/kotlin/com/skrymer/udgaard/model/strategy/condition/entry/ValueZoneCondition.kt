package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Condition that checks if price is within the value zone.
 * Value zone is defined as being above 20 EMA and below 20 EMA + (ATR multiplier * ATR).
 */
@Component
class ValueZoneCondition(
  private val atrMultiplier: Double = 2.0,
) : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean =
    quote.closePrice > quote.closePriceEMA20 &&
      quote.closePrice < (quote.closePriceEMA20 + (atrMultiplier * quote.atr))

  override fun description(): String = "Price within value zone (20EMA < price < 20EMA + ${atrMultiplier}ATR)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "valueZone",
      displayName = "In Value Zone",
      description = "Price is within value zone (20 EMA to 20 EMA + ATR multiplier)",
      parameters =
        listOf(
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
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val price = quote.closePrice
    val ema20 = quote.closePriceEMA20
    val atr = quote.atr
    val upperBound = ema20 + (atrMultiplier * atr)

    val aboveEma20 = price > ema20
    val belowUpperBound = price < upperBound
    val passed = aboveEma20 && belowUpperBound

    val message =
      buildString {
        append("Price ${"%.2f".format(price)} ")
        if (aboveEma20) {
          append("> EMA20 (${"%.2f".format(ema20)}) ✓")
        } else {
          append("≤ EMA20 (${"%.2f".format(ema20)}) ✗")
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
      threshold = "%.2f - %.2f".format(ema20, upperBound),
      message = message,
    )
  }
}
