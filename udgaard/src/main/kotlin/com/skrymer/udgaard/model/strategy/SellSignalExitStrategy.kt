package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class SellSignalExitStrategy: ExitStrategy {

    /**
     * @return true if the stock quote has a sell signal.
     */
    override fun match(
        stock: Stock,
        entryQuote: StockQuote?,
        quote: StockQuote
    ) = quote.hasSellSignal()

    override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
        "Stock has received a sell signal"

    override fun description() = "Exit if quote has a sell signal"
}