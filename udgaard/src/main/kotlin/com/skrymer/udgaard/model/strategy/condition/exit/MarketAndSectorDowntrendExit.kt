package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition that triggers when BOTH market and sector breadth reverse to downtrend.
 * This provides defense against broad market weakness.
 */
class MarketAndSectorDowntrendExit : ExitCondition {
    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        return !quote.sectorIsInUptrend && !quote.marketIsInUptrend
    }

    override fun exitReason(): String = "Market and sector breadth turned bearish"

    override fun description(): String = "Market & sector downtrend"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "marketAndSectorDowntrend",
        description = description()
    )
}
