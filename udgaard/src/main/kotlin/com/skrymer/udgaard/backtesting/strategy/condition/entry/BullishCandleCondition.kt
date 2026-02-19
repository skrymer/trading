package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if the entry day candle is bullish.
 * Requires close > open by at least [minPercent].
 *
 * Winners tend to have stronger intraday pushes on the entry day (+1.75% avg)
 * compared to never-green losers (+0.89% avg).
 */
@Component
class BullishCandleCondition(
  private val minPercent: Double = 0.5,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    if (quote.openPrice <= 0) return false
    val change = (quote.closePrice - quote.openPrice) / quote.openPrice * 100
    return change >= minPercent
  }

  override fun description(): String =
    "Bullish candle (close > open by ≥${"%.1f".format(minPercent)}%)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "bullishCandle",
      displayName = "Bullish Candle",
      description = "Entry day candle is bullish (close above open by minimum percentage)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minPercent",
            displayName = "Min Close-Open %",
            type = "number",
            defaultValue = 0.5,
            min = 0,
            max = 10,
          ),
        ),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val change =
      if (quote.openPrice > 0) {
        (quote.closePrice - quote.openPrice) / quote.openPrice * 100
      } else {
        0.0
      }

    return ConditionEvaluationResult(
      conditionType = "BullishCandleCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.2f".format(change)}%",
      threshold = "≥${"%.1f".format(minPercent)}%",
      message =
        if (passed) {
          "Bullish candle: close-open ${"%.2f".format(change)}%"
        } else {
          "Not bullish enough: close-open ${"%.2f".format(change)}%"
        },
    )
  }
}
