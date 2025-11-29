package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition that triggers when price closes below a specified EMA.
 * More aggressive than EMA cross - exits immediately on first close below EMA.
 *
 * @param emaPeriod The EMA period to check against (default 10)
 */
class PriceBelowEmaExit(
    private val emaPeriod: Int = 10
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        return when (emaPeriod) {
            5 -> quote.closePrice < quote.closePriceEMA5
            10 -> quote.closePrice < quote.closePriceEMA10
            20 -> quote.closePrice < quote.closePriceEMA20
            50 -> quote.closePrice < quote.closePriceEMA50
            else -> throw IllegalArgumentException("Unsupported EMA period: $emaPeriod. Supported: 5, 10, 20, 50")
        }
    }

    override fun exitReason(): String =
        "Price closed under the $emaPeriod EMA"

    override fun description(): String =
        "Price below $emaPeriod EMA"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "priceBelowEma",
        description = description()
    )
}
