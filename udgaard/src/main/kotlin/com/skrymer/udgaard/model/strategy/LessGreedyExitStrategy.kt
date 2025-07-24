package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class LessGreedyExitStrategy: ExitStrategy {
    override fun match(
        entryQuote: StockQuote?,
        quote: StockQuote,
        previousQuote: StockQuote?
    ) = quote.isGettingMoreFearful()

    override fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
        "Heatmap is getting more fearful."

    override fun description() = "Buyers  are getting more fearful"
}