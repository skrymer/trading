package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class PriceUnder50EmaExitStrategy: ExitStrategy {
    override fun match(
        entryQuote: StockQuote?,
        quote: StockQuote,
        previousQuote: StockQuote?
    ) = quote.closePrice < quote.closePriceEMA50

    override fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
        "Price closed under the 50 EMA."

    override fun description() = "Price closes under the 50EMA"
}