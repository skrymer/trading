package com.skrymer.udgaard.model

import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate
import java.util.*

/**
 * Represents a stock with a list of quotes.
 */
@Document(collection = "stocks")
class Stock {
    @Id
    var symbol: String? = null
        private set
    var sectorSymbol: String? = null
        private set
    private var quotes: MutableList<StockQuote?>? = null

    constructor()

    constructor(symbol: String?, sectorSymbol: String?, quotes: MutableList<StockQuote?>) {
        this.symbol = symbol
        this.sectorSymbol = sectorSymbol
        this.quotes = quotes
    }

    fun getQuotes(): MutableList<StockQuote?> {
        return quotes!!
    }

    /**
     *
     * @param entryStrategy
     * @return quotes that matches the given entry strategy.
     */
    fun getQuotesMatching(entryStrategy: EntryStrategy?): MutableList<StockQuote?> {
        return quotes!!.stream().filter(entryStrategy).toList()
    }

    /**
     *
     * @param starDate - the date to start the simulation from.
     * @param exitStrategy - the exit strategy being used.
     * @return
     */
    fun testExitStrategy(starDate: LocalDate?, exitStrategy: ExitStrategy): MutableList<StockQuote?> {
        val quotesToTest = quotes!!.stream()
            .sorted { a: StockQuote?, b: StockQuote? -> a!!.getDate().compareTo(b!!.getDate()) }
            .filter { it: StockQuote? -> it!!.getDate().isAfter(starDate) }
            .toList()

        val quotesMatchingTestCriteria: MutableList<StockQuote?> = ArrayList<StockQuote?>()

        for (quote in quotesToTest) {
            if (!exitStrategy.test(quote)) {
                quotesMatchingTestCriteria.add(quote)
            } else {
                break
            }
        }

        return quotesMatchingTestCriteria
    }

    /**
     *
     * @param quote
     * @return the quote after the given [quote]
     */
    fun getQuoteAfter(quote: StockQuote): Optional<StockQuote?> {
        return quotes!!.stream() // Sort dates asc    
            .sorted { a: StockQuote?, b: StockQuote? -> a!!.getDate().compareTo(b!!.getDate()) }
            .filter { it: StockQuote? -> it!!.getDate().isAfter(quote.getDate()) }
            .findFirst()
    }
}
