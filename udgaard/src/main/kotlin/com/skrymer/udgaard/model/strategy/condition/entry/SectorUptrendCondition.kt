package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if the sector is in an uptrend.
 * Sector is considered in uptrend when sector bull percentage is over 10 EMA.
 */
class SectorUptrendCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.sectorIsInUptrend()
    }

    override fun description(): String = "Sector in uptrend"
}
