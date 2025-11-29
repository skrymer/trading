package com.skrymer.udgaard.model

import jakarta.persistence.*
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
@Entity
@Table(name = "breadth")
class Breadth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "symbol_type", length = 20)
    var symbolType: String? = null // "MARKET" or "SECTOR"

    @Column(name = "symbol_value", length = 50)
    var symbolValue: String? = null // e.g., "FULLSTOCK" for market or sector name

    @OneToMany(mappedBy = "breadth", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var quotes: MutableList<BreadthQuote> = mutableListOf()

    @Transient
    var symbol: BreadthSymbol? = null
        get() = BreadthSymbol.fromString(symbolValue)
        set(value) {
            field = value
            symbolValue = value?.toIdentifier()
            symbolType = when (value) {
                is BreadthSymbol.Market -> "MARKET"
                is BreadthSymbol.Sector -> "SECTOR"
                null -> null
            }
        }

    constructor()

    constructor(symbol: BreadthSymbol, quotes: MutableList<BreadthQuote>) {
        this.symbol = symbol
        this.quotes = quotes
        // Set parent reference on each quote for bidirectional relationship
        quotes.forEach { it.breadth = this }
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
