package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if there is a buy signal.
 * @param currentOnly If true, only accepts buy signals less than 1 day old
 */
class BuySignalCondition(private val currentOnly: Boolean = false) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return if (currentOnly) {
            quote.hasCurrentBuySignal()
        } else {
            quote.hasBuySignal()
        }
    }

    override fun description(): String = if (currentOnly) {
        "Has current buy signal (< 1 day old)"
    } else {
        "Has buy signal"
    }

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "buySignal",
        description = description()
    )
}
