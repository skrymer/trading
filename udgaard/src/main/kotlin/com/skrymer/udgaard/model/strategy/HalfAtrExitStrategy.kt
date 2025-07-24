package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.format
import com.skrymer.udgaard.model.StockQuote

class HalfAtrExitStrategy: ExitStrategy {
    override fun match(
        entryQuote: StockQuote?,
        quote: StockQuote,
        previousQuote: StockQuote?
    ) = quote.closePrice < (entryQuote?.getClosePriceMinusHalfAtr() ?: 0.0)

    override fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
        "Close price is below (entry price - 1/2 ATR)"

    override fun description() = "Half ATR exit strategy"
}