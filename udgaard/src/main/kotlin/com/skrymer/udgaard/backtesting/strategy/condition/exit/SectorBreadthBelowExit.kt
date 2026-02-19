package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when sector breadth drops below a threshold.
 * If too few stocks in the sector are in an uptrend, the wind is against you.
 *
 * @param threshold Sector bull percentage floor (0.0 to 100.0). Default 30.0.
 */
@Component
class SectorBreadthBelowExit(
  private val threshold: Double = 30.0,
) : ExitCondition {
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
    val bullPct = context.getSectorBreadth(stock.sectorSymbol, quote.date)?.bullPercentage
      ?: return false
    return bullPct < threshold
  }

  override fun exitReason(): String = "Sector breadth below ${threshold.toInt()}%"

  override fun description(): String = "Sector breadth below ${threshold.toInt()}%"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorBreadthBelow",
      displayName = "Sector Breadth Below Threshold",
      description = "Exit when sector breadth drops below threshold percentage",
      parameters =
        listOf(
          ParameterMetadata(
            name = "threshold",
            displayName = "Threshold",
            type = "number",
            defaultValue = 30.0,
            min = 0,
            max = 100,
          ),
        ),
      category = "Sector",
    )
}
