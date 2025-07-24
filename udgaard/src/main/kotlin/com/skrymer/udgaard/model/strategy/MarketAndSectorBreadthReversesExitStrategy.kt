package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class MarketAndSectorBreadthReversesExitStrategy: ExitStrategy {

    /**
     * @return true if both the sector and market breadth are in a downtrend.
     */
    override fun match(
        entryQuote: StockQuote?,
        quote: StockQuote,
        previousQuote: StockQuote?
    ) = !quote.sectorIsInUptrend && !quote.marketIsInUptrend

    override fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
        "Market and sector breadth has turned bearish"

    override fun description(): String {
        TODO("Not yet implemented")
    }
}