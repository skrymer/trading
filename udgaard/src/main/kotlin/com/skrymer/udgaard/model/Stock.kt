package com.skrymer.udgaard.model

import com.skrymer.udgaard.isBetween
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Represents a stock with a list of quotes.
 */
@Document(collection = "stocks")
class Stock {
  @Id
  var symbol: String? = null
  var sectorSymbol: String? = null
  var quotes: List<StockQuote> = emptyList()
  var orderBlocks: List<OrderBlock> = emptyList()

  constructor()

  constructor(symbol: String?, sectorSymbol: String?, quotes: List<StockQuote>, orderBlocks: List<OrderBlock>) {
    this.symbol = symbol
    this.sectorSymbol = sectorSymbol
    this.quotes = quotes
    this.orderBlocks = orderBlocks
  }

  /**
   *
   * @param entryStrategy
   * @param after - quotes after the date
   * @param before - quotes before the date
   * @return quotes that matches the given entry strategy.
   */
  fun getQuotesMatchingEntryStrategy(entryStrategy: EntryStrategy, after: LocalDate?, before: LocalDate?) =
    quotes
      .filter { after == null || it.date?.isAfter(after) == true }
      .filter { before == null || it.date?.isBefore(before) == true }
      .filter { entryStrategy.test(this, it) }

  /**
   *
   * @param entryQuote - the entry quote to start the simulation from.
   * @param exitStrategy - the exit strategy being used.
   * @return an ExitReport.
   */
  fun testExitStrategy(
    entryQuote: StockQuote,
    exitStrategy: ExitStrategy
  ): ExitReport {
    val quotesToTest = this.quotes
      .sortedBy { it.date }
      // Find quotes that are after the entry quote date
      .filter { it -> it.date?.isAfter(entryQuote.date) == true }

    val quotes = ArrayList<StockQuote>()
    var exitReason = ""
    var exitPrice = 0.0
    for (quote in quotesToTest) {
      val exitStrategyReport = exitStrategy.test(
        entryQuote = entryQuote,
        quote = quote,
        previousQuote = getPreviousQuote(quote)
      )

      quotes.add(quote)

      if (exitStrategyReport.match) {
        exitReason = exitStrategyReport.exitReason ?: ""
        exitPrice = exitStrategyReport.exitPrice
        break
      }
    }

    return ExitReport(exitReason, quotes, exitPrice)
  }

  /**
   *
   * @param quote
   * @return the next quote after the given [quote]
   */
  fun getNextQuote(quote: StockQuote) =
    quotes.sortedBy { it.date }.firstOrNull { it -> it.date?.isAfter(quote.date) == true }

  /**
   * Get the quote previous to the given [quote]
   */
  fun getPreviousQuote(quote: StockQuote) =
    quotes.sortedByDescending { it.date }.firstOrNull { it -> it.date?.isBefore(quote.date) == true }

  /**
   * get the quote for the given [date]
   */
  fun getQuoteByDate(date: LocalDate) =
    quotes.find { it.date?.equals(date) == true }

  /**
   * Check if given [quote] is within an order block that are older than [daysOld]
   */
  fun withinOrderBlock(quote: StockQuote, daysOld: Int) =
    orderBlocks
      .filter { ChronoUnit.DAYS.between(it.startDate, it.endDate ?: LocalDate.now()) >= daysOld }
      .find {
        quote.date?.isBetween(it.startDate, it.endDate ?: LocalDate.now(), true) == true
      } != null

  override fun toString() = "Symbol: $symbol"
}
