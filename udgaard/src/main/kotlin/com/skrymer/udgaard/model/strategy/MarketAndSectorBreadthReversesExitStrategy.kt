package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class MarketAndSectorBreadthReversesExitStrategy: ExitStrategy {

    /**
     * @return true if both the sector and market breadth are in a downtrend.
     */
    override fun match(
        stock: Stock,
        entryQuote: StockQuote?,
        quote: StockQuote
    ) = !quote.sectorIsInUptrend && !quote.marketIsInUptrend

    override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
        "Market and sector breadth has turned bearish"

    override fun description(): String {
        TODO("Not yet implemented")
    }
}