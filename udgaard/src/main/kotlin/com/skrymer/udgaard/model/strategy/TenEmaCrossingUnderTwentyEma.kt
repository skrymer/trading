package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class TenEmaCrossingUnderTwentyEma: ExitStrategy {

    /**
     * @return true if 10EMA has crossed under the 20EMA.
     */
    override fun match(
        entryQuote: StockQuote?,
        quote: StockQuote,
        previousQuote: StockQuote?
    ) = quote.closePriceEMA10 < quote.closePriceEMA20

    override fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
        "10 EMA has crossed under the 20 EMA."

    override fun description() = "10EMA crossing under the 20EMA"


}