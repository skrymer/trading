package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when market breadth EMAs invert bearishly.
 * Early warning that market participation is rolling over (ema5 < ema10 < ema20),
 * even if breadth is still technically "above average."
 */
@Component
class MarketBreadthDeterioratingExit : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = shouldExit(stock, entryQuote, quote, BacktestContext.EMPTY)

  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val breadth = context.getMarketBreadth(quote.date) ?: return false
    return breadth.ema5 < breadth.ema10 && breadth.ema10 < breadth.ema20
  }

  override fun exitReason(): String = "Market breadth deteriorating (EMAs inverted)"

  override fun description(): String = "Market breadth deteriorating"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthDeteriorating",
      displayName = "Market Breadth Deteriorating",
      description = "Exit when market breadth EMAs invert bearishly (EMA5 < EMA10 < EMA20)",
      parameters = emptyList(),
      category = "Market",
    )
}
