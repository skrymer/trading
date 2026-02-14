package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when price drops below entry price by a specified ATR multiple.
 * This acts as a stop loss to limit downside risk.
 *
 * @param atrMultiplier Number of ATRs below entry price to trigger exit (default 2.0)
 */
@Component
class StopLossExit(
  private val atrMultiplier: Double = 2.0,
) : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean {
    if (entryQuote == null) return false

    val stopLossLevel = entryQuote.closePrice - (atrMultiplier * entryQuote.atr)
    return quote.closePrice < stopLossLevel
  }

  override fun exitReason(): String = "Stop loss triggered ($atrMultiplier ATR below entry)"

  override fun description(): String = "Stop loss ($atrMultiplier ATR)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "stopLoss",
      displayName = "Stop Loss",
      description = "Exit when price drops below entry - ATR multiplier",
      parameters =
        listOf(
          ParameterMetadata(
            name = "atrMultiplier",
            displayName = "ATR Multiplier",
            type = "number",
            defaultValue = 2.0,
            min = 0.5,
            max = 5.0,
          ),
        ),
      category = "StopLoss",
    )
}
