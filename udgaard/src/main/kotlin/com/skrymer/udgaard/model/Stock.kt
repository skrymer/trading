package com.skrymer.udgaard.model

import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

/**
 * Represents a stock with a list of quotes.
 */
@Document(collection = "stocks")
class Stock {
    @Id
    var symbol: String? = null
    var sectorSymbol: String? = null
    private var quotes: List<StockQuote> = emptyList()

    constructor()

    constructor(symbol: String?, sectorSymbol: String?, quotes: List<StockQuote>) {
        this.symbol = symbol
        this.sectorSymbol = sectorSymbol
        this.quotes = quotes
    }

    /**
     *
     * @param entryStrategy
     * @return quotes that matches the given entry strategy.
     */
    fun getQuotesMatchingEntryStrategy(entryStrategy: EntryStrategy): List<StockQuote> {
        val filteredQuotes = quotes.filter { entryStrategy.test(it) }
        return filteredQuotes
    }

    /**
     *
     * @param starDate - the date to start the simulation from.
     * @param exitStrategy - the exit strategy being used.
     * @return
     */
    fun getQuotesMatchingExitStrategy(entryQuote: StockQuote, exitStrategy: ExitStrategy): Pair<String, List<StockQuote>> {
        val quotesToTest = quotes
            .sortedBy { it.date }
            // Find quotes that are after the entry quote date
            .filter { it -> it.date?.isAfter(entryQuote.date) == true }

        val quotesMatchingTestCriteria = ArrayList<StockQuote>()
        var exitReason = ""
        for (quote in quotesToTest) {
            val testOutcome = exitStrategy.testAndExitReason(entryQuote = entryQuote, quote = quote)
            if (!testOutcome.first) {
                quotesMatchingTestCriteria.add(quote)
            } else {
                exitReason = testOutcome.second ?: ""
                break
            }
        }

        return Pair(exitReason,  quotesMatchingTestCriteria)
    }

    /**
     *
     * @param quote
     * @return the quote after the given [quote]
     */
    fun getQuoteAfter(quote: StockQuote) =
        quotes.sortedBy { it.date }.firstOrNull { it -> it.date?.isAfter(quote.date) == true }

}
