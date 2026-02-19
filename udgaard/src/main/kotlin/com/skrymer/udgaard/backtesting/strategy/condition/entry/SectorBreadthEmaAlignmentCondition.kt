package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if sector breadth EMAs are bullishly aligned.
 * Confirms accelerating sector participation (ema5 > ema10 > ema20).
 */
@Component
class SectorBreadthEmaAlignmentCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = evaluate(stock, quote, BacktestContext.EMPTY)

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val breadth = context.getSectorBreadth(stock.sectorSymbol, quote.date) ?: return false
    return breadth.ema5 > breadth.ema10 && breadth.ema10 > breadth.ema20
  }

  override fun description(): String = "Sector breadth EMA alignment (5 > 10 > 20)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorBreadthEmaAlignment",
      displayName = "Sector Breadth EMA Alignment",
      description = "Sector breadth EMAs are bullishly stacked (EMA5 > EMA10 > EMA20)",
      parameters = emptyList(),
      category = "Sector",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult = evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val breadth = context.getSectorBreadth(stock.sectorSymbol, quote.date)
    val ema5 = breadth?.ema5 ?: 0.0
    val ema10 = breadth?.ema10 ?: 0.0
    val ema20 = breadth?.ema20 ?: 0.0

    val message =
      if (passed) {
        "Sector breadth EMAs aligned: EMA5 %.1f > EMA10 %.1f > EMA20 %.1f".format(ema5, ema10, ema20)
      } else {
        "Sector breadth EMAs not aligned: EMA5 %.1f, EMA10 %.1f, EMA20 %.1f".format(ema5, ema10, ema20)
      }

    return ConditionEvaluationResult(
      conditionType = "SectorBreadthEmaAlignmentCondition",
      description = description(),
      passed = passed,
      actualValue = "EMA5=%.1f, EMA10=%.1f, EMA20=%.1f".format(ema5, ema10, ema20),
      threshold = "EMA5 > EMA10 > EMA20",
      message = message,
    )
  }
}
