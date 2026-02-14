package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Condition that checks if the stock is in an uptrend.
 *
 * An uptrend is defined as:
 * - 10 EMA is above 20 EMA (short-term above medium-term)
 * - Close price is above 50 EMA (price above long-term trend)
 */
@Component
class UptrendCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    // Check if 10 EMA > 20 EMA
    val ema10AboveEma20 = quote.closePriceEMA10 > quote.closePriceEMA20

    // Check if close price > 50 EMA
    val priceAboveEma50 = quote.closePrice > quote.closePriceEMA50

    // Both conditions must be true for uptrend
    return ema10AboveEma20 && priceAboveEma50
  }

  override fun description(): String = "Stock is in uptrend (10 EMA > 20 EMA and price > 50 EMA)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "uptrend",
      displayName = "Stock in Uptrend",
      description = "Stock trend is 'Uptrend'",
      parameters = emptyList(),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val ema10 = quote.closePriceEMA10
    val ema20 = quote.closePriceEMA20
    val ema50 = quote.closePriceEMA50
    val price = quote.closePrice

    val ema10AboveEma20 = ema10 > ema20
    val priceAboveEma50 = price > ema50
    val passed = ema10AboveEma20 && priceAboveEma50

    val message =
      buildString {
        append("EMA10 (${"%.2f".format(ema10)}) ")
        append(if (ema10AboveEma20) ">" else "≤")
        append(" EMA20 (${"%.2f".format(ema20)}) ")
        if (ema10AboveEma20 && priceAboveEma50) {
          append("✓ AND ")
        } else {
          append("✗ AND ")
        }
        append("Price (${"%.2f".format(price)}) ")
        append(if (priceAboveEma50) ">" else "≤")
        append(" EMA50 (${"%.2f".format(ema50)}) ")
        append(if (priceAboveEma50) "✓" else "✗")
      }

    return ConditionEvaluationResult(
      conditionType = "UptrendCondition",
      description = description(),
      passed = passed,
      actualValue = null,
      threshold = null,
      message = message,
    )
  }
}
