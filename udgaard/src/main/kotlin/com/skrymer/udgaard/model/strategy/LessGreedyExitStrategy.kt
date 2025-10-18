package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class LessGreedyExitStrategy: ExitStrategy {
    override fun match(
        stock: Stock,
        entryQuote: StockQuote?,
        quote: StockQuote
    ) = quote.isGettingMoreFearful()

    override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
        "Heatmap is getting more fearful."

    override fun description() = "Buyers  are getting more fearful"
}