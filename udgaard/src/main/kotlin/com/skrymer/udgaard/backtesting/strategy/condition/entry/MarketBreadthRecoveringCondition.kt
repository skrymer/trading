package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that detects market breadth crossing above its EMA from below.
 * Catches the early phase of a new uptrend rather than waiting for an established one.
 *
 * Logic: today breadthPercent > ema10, AND yesterday breadthPercent <= ema10.
 * Uses the stock quote's previous date to look up the prior day's breadth.
 */
@Component
class MarketBreadthRecoveringCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = evaluate(stock, quote, BacktestContext.EMPTY)

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val today = context.getMarketBreadth(quote.date) ?: return false
    val todayAbove = today.breadthPercent > today.ema10

    if (!todayAbove) return false

    val previousDate = quote.date.minusDays(1)
    val yesterday = context.getMarketBreadth(previousDate)

    // If no previous day data, check 2 and 3 days back (weekends/holidays)
    val prev = yesterday
      ?: context.getMarketBreadth(previousDate.minusDays(1))
      ?: context.getMarketBreadth(previousDate.minusDays(2))
      ?: return false

    return prev.breadthPercent <= prev.ema10
  }

  override fun description(): String = "Market breadth recovering (crosses above EMA10)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthRecovering",
      displayName = "Market Breadth Recovering",
      description = "Market breadth crosses above its 10 EMA from below (momentum shift)",
      parameters = emptyList(),
      category = "Market",
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
    val today = context.getMarketBreadth(quote.date)
    val breadth = today?.breadthPercent ?: 0.0
    val ema10 = today?.ema10 ?: 0.0

    val message =
      if (passed) {
        "Market breadth %.1f%% crossed above EMA10 %.1f%%".format(breadth, ema10)
      } else {
        "Market breadth %.1f%% has not crossed above EMA10 %.1f%%".format(breadth, ema10)
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthRecoveringCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f%%".format(breadth),
      threshold = "Crosses above %.1f%% (EMA10)".format(ema10),
      message = message,
    )
  }
}
