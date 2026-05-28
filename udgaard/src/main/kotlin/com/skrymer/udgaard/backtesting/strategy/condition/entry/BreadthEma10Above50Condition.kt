package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Hard-line market-breadth gate. Permits entries only when the 10-EMA of the Full-Stocks-Breadth
 * bull-list-% is strictly above 50%.
 *
 * Complements `MarketUptrendCondition` (breadth-vs-EMA10 trend) and `SpyTrendUpCondition`
 * (SPY price-vs-200EMA) — three independent regime dimensions per the regime-conditional
 * portfolio framework (memory: regime-conditional-portfolio-framework).
 *
 * Returns false if no breadth row exists for the date — by convention, missing data means
 * the gate cannot confirm "safe regime", so entries are suppressed.
 */
@Component
class BreadthEma10Above50Condition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = (context.getMarketBreadth(quote.date)?.ema10 ?: 0.0) > THRESHOLD

  override fun description(): String = "Market-breadth EMA(10) > 50%"

  override fun getMetadata() =
    ConditionMetadata(
      type = "breadthEma10Above50",
      displayName = "Breadth EMA(10) above 50%",
      description = "Permits entries only when 10-EMA of Full-Stocks-Breadth bull-list-% is above 50",
      parameters = emptyList(),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val ema10 = context.getMarketBreadth(quote.date)?.ema10 ?: 0.0
    val message =
      if (passed) {
        "Breadth EMA(10) %.1f%% is above 50%% ✓".format(ema10)
      } else {
        "Breadth EMA(10) %.1f%% is at or below 50%% ✗".format(ema10)
      }
    return ConditionEvaluationResult(
      conditionType = "BreadthEma10Above50Condition",
      description = description(),
      passed = passed,
      actualValue = "%.1f%%".format(ema10),
      threshold = "> 50%",
      message = message,
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this

  companion object {
    private const val THRESHOLD = 50.0
  }
}
