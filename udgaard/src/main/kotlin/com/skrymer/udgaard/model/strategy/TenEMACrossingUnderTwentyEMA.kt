package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class TenEMACrossingUnderTwentyEMA: ExitStrategy {

    /**
     * @return true if 10EMA has crossed under the 20EMA.
     */
    override fun test(
        entryQuote: StockQuote,
        quote: StockQuote
    ) = quote.closePriceEMA10 < quote.closePriceEMA20

    override fun reason(entryQuote: StockQuote, quote: StockQuote) =
        "10 EMA ${quote.closePriceEMA10} has crossed under the 20 EMA ${quote.closePriceEMA20}"

    override fun description() = "10EMA crossing under the 20EMA"


}