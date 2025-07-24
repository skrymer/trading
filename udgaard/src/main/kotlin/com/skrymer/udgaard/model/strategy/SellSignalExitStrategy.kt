package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class SellSignalExitStrategy: ExitStrategy {

    /**
     * @return true if the stock quote has a sell signal.
     */
    override fun match(
        entryQuote: StockQuote?,
        quote: StockQuote,
        previousQuote: StockQuote?
    ) = quote.hasSellSignal()

    override fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
        "Stock has received a sell signal"

    override fun description() = "Exit if quote has a sell signal"
}