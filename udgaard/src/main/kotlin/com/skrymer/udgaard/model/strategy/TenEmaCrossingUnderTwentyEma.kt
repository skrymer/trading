package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class TenEmaCrossingUnderTwentyEma: ExitStrategy {

    /**
     * @return true if 10EMA has crossed under the 20EMA.
     */
    override fun match(
        stock: Stock,
        entryQuote: StockQuote?,
        quote: StockQuote
    ) = quote.closePriceEMA10 < quote.closePriceEMA20

    override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
        "10 EMA has crossed under the 20 EMA."

    override fun description() = "10EMA crossing under the 20EMA"


}