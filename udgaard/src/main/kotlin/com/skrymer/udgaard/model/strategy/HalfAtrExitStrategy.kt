package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.format
import com.skrymer.udgaard.model.StockQuote

class HalfAtrExitStrategy: ExitStrategy {
    override fun test(
        entryQuote: StockQuote?,
        quote: StockQuote
    ) = quote.closePrice < (entryQuote?.getClosePriceMinusHalfAtr() ?: 0.0)

    override fun reason(entryQuote: StockQuote?, quote: StockQuote) =
        "Close price ${quote.closePrice.format(2)} is below the (entry price - 1/2 ATR) ${entryQuote?.getClosePriceMinusHalfAtr()?.format(2)}"

    override fun description() = "Half ATR exit strategy"
}