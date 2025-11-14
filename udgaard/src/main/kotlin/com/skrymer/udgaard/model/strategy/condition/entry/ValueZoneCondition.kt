package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if price is within the value zone.
 * Value zone is defined as being above 20 EMA and below 20 EMA + (ATR multiplier * ATR).
 */
class ValueZoneCondition(private val atrMultiplier: Double = 2.0) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.closePrice > quote.closePriceEMA20 &&
               quote.closePrice < (quote.closePriceEMA20 + (atrMultiplier * quote.atr))
    }

    override fun description(): String = "Price within value zone (20EMA < price < 20EMA + ${atrMultiplier}ATR)"
}
