package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class FearfulExitStrategy: ExitStrategy {
    override fun test(
        entryQuote: StockQuote,
        quote: StockQuote
    ) = quote.isGettingMoreFearful()

    override fun reason(entryQuote: StockQuote, quote: StockQuote) =
        "Heatmap is getting more fearful. Current value ${quote.heatmap} previous value ${quote.previousHeatmap}"

    override fun description() = "Buyers  are getting more fearful"
}