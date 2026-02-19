package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if sector breadth is greater than market breadth.
 *
 * Compares sector bull percentage against market breadth percentage from context.
 */
@Component
class SectorBreadthGreaterThanMarketCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = evaluate(stock, quote, BacktestContext.EMPTY)

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val sectorBullPct = context.getSectorBreadth(stock.sectorSymbol, quote.date)?.bullPercentage ?: return false
    val marketBreadthPct = context.getMarketBreadth(quote.date)?.breadthPercent ?: return false
    return sectorBullPct > marketBreadthPct
  }

  override fun description(): String = "Sector breadth > Market breadth"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorBreadthGreaterThanMarket",
      displayName = "Sector Breadth > Market Breadth",
      description = "Sector breadth is greater than market breadth",
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
    val sectorBreadth = context.getSectorBreadth(stock.sectorSymbol, quote.date)?.bullPercentage ?: 0.0
    val marketBreadth = context.getMarketBreadth(quote.date)?.breadthPercent ?: 0.0
    val passed = evaluate(stock, quote, context)
    val difference = sectorBreadth - marketBreadth

    val message =
      if (passed) {
        "Sector breadth %.1f%% > Market breadth %.1f%% (diff: %+.1f%%) ✓".format(
          sectorBreadth,
          marketBreadth,
          difference,
        )
      } else {
        "Sector breadth %.1f%% ≤ Market breadth %.1f%% (diff: %+.1f%%) ✗".format(
          sectorBreadth,
          marketBreadth,
          difference,
        )
      }

    return ConditionEvaluationResult(
      conditionType = "SectorBreadthGreaterThanMarketCondition",
      description = description(),
      passed = passed,
      actualValue = "Sector: %.1f%%, Market: %.1f%%".format(sectorBreadth, marketBreadth),
      threshold = "Sector > Market",
      message = message,
    )
  }
}
