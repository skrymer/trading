package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition
import com.skrymer.udgaard.model.strategy.condition.ConditionMetadata

/**
 * Exit condition that triggers when price extends beyond a profit target.
 * Target is defined as a number of ATRs above a specified EMA.
 */
class ProfitTargetExit(
    private val atrMultiplier: Double = 3.0,
    private val emaPeriod: Int = 20
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        val emaValue = getEmaValue(quote, emaPeriod)
        return quote.closePrice > (emaValue + (atrMultiplier * quote.atr))
    }

    override fun exitReason(): String = "Price is ${atrMultiplier} ATR above ${emaPeriod} EMA"

    override fun description(): String = "Price > ${emaPeriod}EMA + ${atrMultiplier}ATR"

    override fun getMetadata() = ConditionMetadata(
        type = "profitTarget",
        description = description()
    )

    private fun getEmaValue(quote: StockQuote, period: Int): Double {
        return when (period) {
            5 -> quote.closePriceEMA5
            10 -> quote.closePriceEMA10
            20 -> quote.closePriceEMA20
            50 -> quote.closePriceEMA50
            else -> 0.0
        }
    }
}
