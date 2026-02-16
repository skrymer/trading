package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when BOTH market and sector breadth reverse to downtrend.
 * This provides defense against broad market weakness.
 */
@Component
class MarketAndSectorDowntrendExit : ExitCondition {
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
    val sectorInUptrend = context.getSectorBreadth(stock.sectorSymbol, quote.date)?.isInUptrend() ?: false
    val marketInUptrend = context.getMarketBreadth(quote.date)?.isInUptrend() ?: false
    return !sectorInUptrend && !marketInUptrend
  }

  override fun exitReason(): String = "Market and sector breadth turned bearish"

  override fun description(): String = "Market & sector downtrend"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketAndSectorDowntrend",
      displayName = "Market & Sector Downtrend",
      description = "Exit when both market and sector are in downtrend",
      parameters = emptyList(),
      category = "Trend",
    )
}
