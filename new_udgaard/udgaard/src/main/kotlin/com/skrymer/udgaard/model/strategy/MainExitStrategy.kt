package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class HalfAtr10EmaExitStrategy: ExitStrategy {
    override fun test(
        entryQuote: StockQuote,
        quote: StockQuote
    ) = HalfAtrExitStrategy().test(entryQuote, quote)
        .and(PriceUnder10EmaExitStrategy().test(entryQuote, quote))
        .and(TenEMACrossingUnderTwentyEMA().test(entryQuote, quote))
        .and(PriceUnderFiftyEMA().test(entryQuote, quote))

    override fun description() = "Price under 10 EMA or price under price minus half ATR"
}