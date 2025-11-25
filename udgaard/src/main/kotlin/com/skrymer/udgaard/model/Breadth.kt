package com.skrymer.udgaard.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

/**
 * Represents breadth data for either the entire market or a specific sector.
 *
 * Breadth refers to the number of stocks advancing versus declining,
 * indicating the overall health and direction.
 *
 * Strong breadth shows widespread participation in a rally,
 * while weak breadth suggests fewer stocks are driving the movement.
 */
@Document("breadth")
class Breadth {
    @Id
    var id: String? = null

    var symbol: BreadthSymbol? = null
    var quotes: List<BreadthQuote> = emptyList()

    constructor()

    constructor(symbol: BreadthSymbol, quotes: List<BreadthQuote>) {
        this.id = symbol.toIdentifier()
        this.symbol = symbol
        this.quotes = quotes
    }

    val inUptrend: Boolean
        get() = quotes.sortedBy { it.quoteDate }.lastOrNull()?.isInUptrend() ?: false

    val heatmap: Double
        get() = quotes.sortedBy { it.quoteDate }.lastOrNull()?.heatmap ?: 0.0

    val previousHeatmap: Double
        get() = quotes.sortedBy { it.quoteDate }.lastOrNull()?.previousHeatmap ?: 0.0

    val donkeyChannelScore: Int
        get() = quotes.lastOrNull()?.donkeyChannelScore ?: 0

    val name: String
        get() = symbol?.toDescription() ?: ""

    fun getDonkeyScoreByDate(date: LocalDate): Int =
        quotes.firstOrNull { it.quoteDate?.equals(date) == true }?.donkeyChannelScore ?: 0

    fun getQuoteForDate(date: LocalDate?): BreadthQuote? =
        quotes.firstOrNull { date == it.quoteDate }

    fun getPreviousQuote(quote: BreadthQuote?): BreadthQuote? {
        return if (quote == null) {
            null
        } else {
            quotes
                .sortedByDescending { it.quoteDate }
                .firstOrNull { it.quoteDate?.isBefore(quote.quoteDate) == true }
        }
    }

    fun getPreviousQuotes(date: LocalDate?, lookBack: Int): List<BreadthQuote> {
        val quote = getQuoteForDate(date)
        if (quote == null) {
            return emptyList()
        }

        val quotesSortedByDateAsc = quotes.sortedBy { it.quoteDate }
        val quoteIndex = quotesSortedByDateAsc.indexOf(quote)
        return if (quoteIndex < lookBack) {
            quotesSortedByDateAsc.subList(0, quoteIndex)
        } else {
            quotesSortedByDateAsc.subList(quoteIndex - lookBack, quoteIndex)
        }
    }
}
