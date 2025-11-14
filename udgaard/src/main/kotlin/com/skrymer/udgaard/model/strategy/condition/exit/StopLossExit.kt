package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition that triggers when price drops below entry price by a specified ATR multiple.
 * This acts as a stop loss to limit downside risk.
 *
 * @param atrMultiplier Number of ATRs below entry price to trigger exit (default 2.0)
 */
class StopLossExit(
    private val atrMultiplier: Double = 2.0
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        if (entryQuote == null) return false

        val stopLossLevel = entryQuote.closePrice - (atrMultiplier * entryQuote.atr)
        return quote.closePrice < stopLossLevel
    }

    override fun exitReason(): String =
        "Stop loss triggered (${atrMultiplier} ATR below entry)"

    override fun description(): String =
        "Stop loss (${atrMultiplier} ATR)"
}
