package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class HalfAtrExitStrategy: ExitStrategy {
    override fun test(
        entryQuote: StockQuote,
        quote: StockQuote
    ) = quote.closePrice < entryQuote.getClosePriceMinusHalfAtr()

    override fun reason(entryQuote: StockQuote, quote: StockQuote) =
        "Close price ${quote.closePrice} is below the 1/2 ATR price at entry ${entryQuote.getClosePriceMinusHalfAtr()}"

    override fun description() = "Half ATR exit strategy"
}