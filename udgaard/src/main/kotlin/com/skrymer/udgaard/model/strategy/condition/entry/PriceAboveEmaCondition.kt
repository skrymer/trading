package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if the price is above a specific EMA.
 */
class PriceAboveEmaCondition(private val emaPeriod: Int) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return when (emaPeriod) {
            5 -> quote.closePrice > quote.closePriceEMA5
            10 -> quote.closePrice > quote.closePriceEMA10
            20 -> quote.closePrice > quote.closePriceEMA20
            50 -> quote.closePrice > quote.closePriceEMA50
            else -> false
        }
    }

    override fun description(): String = "Price > ${emaPeriod}EMA"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "priceAboveEma",
        description = description()
    )
}
