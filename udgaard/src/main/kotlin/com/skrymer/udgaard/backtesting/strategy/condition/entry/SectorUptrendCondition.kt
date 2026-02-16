package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if the sector is in an uptrend.
 * Sector is considered in uptrend when sector bull percentage is over 10 EMA.
 */
@Component
class SectorUptrendCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = evaluate(stock, quote, BacktestContext.EMPTY)

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = context.getSectorBreadth(stock.sectorSymbol, quote.date)?.isInUptrend() ?: false

  override fun description(): String = "Sector in uptrend"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorUptrend",
      displayName = "Sector in Uptrend",
      description = "Stock's sector is in uptrend",
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
    val message = if (passed) {
      "Sector bull %.1f%% > 10 EMA (%.1f%%) ✓".format(breadth?.bullPercentage ?: 0.0, breadth?.ema10 ?: 0.0)
    } else {
      "Sector bull %.1f%% ≤ 10 EMA (%.1f%%) ✗".format(breadth?.bullPercentage ?: 0.0, breadth?.ema10 ?: 0.0)
    }

    return ConditionEvaluationResult(
      conditionType = "SectorUptrendCondition",
      description = description(),
      passed = passed,
      actualValue = breadth?.bullPercentage?.let { "%.1f%%".format(it) },
      threshold = breadth?.ema10?.let { "> %.1f%% (10 EMA)".format(it) },
      message = message,
    )
  }
}
