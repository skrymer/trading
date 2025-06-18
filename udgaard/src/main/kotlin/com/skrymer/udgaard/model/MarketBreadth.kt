package com.skrymer.udgaard.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate
import java.util.*

/**
 * Represents the market breadth.
 *
 * Market breadth refers to the number of stocks
 * advancing versus declining in a market or index,
 * indicating the overall health and direction of the market.
 *
 * Strong breadth shows widespread participation in a rally,
 * while weak breadth suggests fewer stocks are driving the movement.
 */
@Document("marketBreadth")
class MarketBreadth {
    @Id
    var symbol: MarketSymbol? = null
    private var quotes: List<MarketBreadthQuote> = emptyList()

    constructor()

    constructor(symbol: MarketSymbol, quotes: List<MarketBreadthQuote>) {
        this.symbol = symbol
        this.quotes = quotes
    }

    fun getQuoteForDate(date: LocalDate) =
        quotes.firstOrNull{ date == it.quoteDate }
}
