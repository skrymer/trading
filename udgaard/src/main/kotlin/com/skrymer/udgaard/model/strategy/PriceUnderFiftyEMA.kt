package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class PriceUnderFiftyEMA: ExitStrategy {
    override fun test(
        entryQuote: StockQuote,
        quote: StockQuote
    ) = quote.closePrice < quote.closePriceEMA50

    override fun reason(entryQuote: StockQuote, quote: StockQuote) =
        "Price closed under the 50 EMA. Close price ${quote.closePrice} 50 EMA ${quote.closePriceEMA50}"

    override fun description() = "Price closes under the 50EMA"
}