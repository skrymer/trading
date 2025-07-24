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
    var quotes: List<MarketBreadthQuote> = emptyList()

    constructor(symbol: MarketSymbol, quotes: List<MarketBreadthQuote>) {
        this.symbol = symbol
        this.quotes = quotes
    }

    val inUptrend: Boolean
        get() = quotes.last().isInUptrend()

    fun getQuoteForDate(date: LocalDate?) =
        quotes.firstOrNull{ date == it.quoteDate }

    fun getPreviousQuotes(date: LocalDate?, lookBack: Int): List<MarketBreadthQuote> {
        val quote = getQuoteForDate(date)
        if(quote == null){
            return emptyList()
        }

        val quotesSortedByDateAsc = quotes.sortedBy { it.quoteDate }
        val quoteIndex = quotesSortedByDateAsc.indexOf(quote)
        return if(quoteIndex < lookBack){
            quotesSortedByDateAsc.subList(0, quoteIndex)
        } else {
            quotesSortedByDateAsc.subList(quoteIndex - lookBack, quoteIndex)
        }
    }
}
