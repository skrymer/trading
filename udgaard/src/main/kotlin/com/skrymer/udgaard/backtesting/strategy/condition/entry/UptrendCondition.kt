package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Condition that checks if the stock is in an uptrend.
 *
 * An uptrend is defined as:
 * - 5 EMA is above 10 EMA (very short-term momentum alignment)
 * - 10 EMA is above 20 EMA (short-term above medium-term)
 * - Close price is above 50 EMA (price above long-term trend)
 */
@Component
class UptrendCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val ema5AboveEma10 = quote.closePriceEMA5 > quote.closePriceEMA10
    val ema10AboveEma20 = quote.closePriceEMA10 > quote.closePriceEMA20
    val priceAboveEma50 = quote.closePrice > quote.closePriceEMA50

    return ema5AboveEma10 && ema10AboveEma20 && priceAboveEma50
  }

  override fun description(): String = "Stock is in uptrend (5 EMA > 10 EMA > 20 EMA and price > 50 EMA)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "uptrend",
      displayName = "Stock in Uptrend",
      description = "Stock is in uptrend (5 EMA > 10 EMA > 20 EMA and price > 50 EMA)",
      parameters = emptyList(),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val ema5 = quote.closePriceEMA5
    val ema10 = quote.closePriceEMA10
    val ema20 = quote.closePriceEMA20
    val ema50 = quote.closePriceEMA50
    val price = quote.closePrice

    val ema5AboveEma10 = ema5 > ema10
    val ema10AboveEma20 = ema10 > ema20
    val priceAboveEma50 = price > ema50
    val passed = ema5AboveEma10 && ema10AboveEma20 && priceAboveEma50

    val message =
      buildString {
        append("EMA5 (${"%.2f".format(ema5)}) ")
        append(if (ema5AboveEma10) ">" else "≤")
        append(" EMA10 (${"%.2f".format(ema10)}) ")
        append(if (ema5AboveEma10) "✓" else "✗")
        append(" AND EMA10 (${"%.2f".format(ema10)}) ")
        append(if (ema10AboveEma20) ">" else "≤")
        append(" EMA20 (${"%.2f".format(ema20)}) ")
        append(if (ema10AboveEma20) "✓" else "✗")
        append(" AND Price (${"%.2f".format(price)}) ")
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
