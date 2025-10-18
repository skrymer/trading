package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class HalfAtrExitStrategy: ExitStrategy {
    override fun match(
        stock: Stock,
        entryQuote: StockQuote?,
        quote: StockQuote
    ) = quote.closePrice < (entryQuote?.getClosePriceMinusHalfAtr() ?: 0.0)

    override fun reason(stock: Stock,entryQuote: StockQuote?, quote: StockQuote) =
        "Close price is below (entry price - 1/2 ATR)"

    override fun description() = "Half ATR exit strategy"
}