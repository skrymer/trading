package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.ConditionMetadata
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that filters stocks by minimum price.
 *
 * This helps avoid penny stocks and ensure sufficient liquidity
 * by only entering positions when the stock price is above a
 * specified dollar threshold.
 *
 * @param minimumPrice Minimum close price in dollars (default: 10.0)
 */
class MinimumPriceCondition(
    private val minimumPrice: Double = 10.0
) : TradingCondition {

    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.closePrice >= minimumPrice
    }

    override fun description(): String = "Price >= $${"%.2f".format(minimumPrice)}"

    override fun getMetadata() = ConditionMetadata(
        type = "minimumPrice",
        description = description()
    )
}
