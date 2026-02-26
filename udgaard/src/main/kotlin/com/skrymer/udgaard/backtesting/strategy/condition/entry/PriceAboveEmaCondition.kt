package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Condition that checks if the price is above a specific EMA.
 */
@Component
class PriceAboveEmaCondition(
  private val emaPeriod: Int = 10,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean =
    when (emaPeriod) {
      5 -> quote.closePrice > quote.closePriceEMA5
      10 -> quote.closePrice > quote.closePriceEMA10
      20 -> quote.closePrice > quote.closePriceEMA20
      50 -> quote.closePrice > quote.closePriceEMA50
      100 -> quote.closePrice > quote.closePriceEMA100
      200 -> quote.closePrice > quote.ema200
      else -> false
    }

  override fun description(): String = "Price > ${emaPeriod}EMA"

  override fun getMetadata() =
    ConditionMetadata(
      type = "priceAboveEma",
      displayName = "Price Above EMA",
      description = "Price is above the specified EMA",
      parameters =
        listOf(
          ParameterMetadata(
            name = "emaPeriod",
            displayName = "EMA Period",
            type = "number",
            defaultValue = 10,
            options = listOf("5", "10", "20", "50", "100", "200"),
          ),
        ),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val price = quote.closePrice
    val emaValue =
      when (emaPeriod) {
        5 -> quote.closePriceEMA5
        10 -> quote.closePriceEMA10
        20 -> quote.closePriceEMA20
        50 -> quote.closePriceEMA50
        100 -> quote.closePriceEMA100
        200 -> quote.ema200
        else -> 0.0
      }
    val passed = price > emaValue

    val message =
      if (passed) {
        "Price ${"%.2f".format(price)} > EMA$emaPeriod ${"%.2f".format(emaValue)} ✓"
      } else {
        "Price ${"%.2f".format(price)} ≤ EMA$emaPeriod ${"%.2f".format(emaValue)} ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "PriceAboveEmaCondition",
      description = description(),
      passed = passed,
      actualValue = "Price: ${"%.2f".format(price)}, EMA$emaPeriod: ${"%.2f".format(emaValue)}",
      threshold = "Price > EMA$emaPeriod",
      message = message,
    )
  }
}
