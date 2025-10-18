package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class PriceUnder50EmaExitStrategy: ExitStrategy {
    override fun match(
        stock: Stock,
        entryQuote: StockQuote?,
        quote: StockQuote
    ) = quote.closePrice < quote.closePriceEMA50

    override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
        "Price closed under the 50 EMA."

    override fun description() = "Price closes under the 50EMA"
}