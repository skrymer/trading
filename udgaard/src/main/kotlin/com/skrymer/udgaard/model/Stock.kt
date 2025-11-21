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
  var ovtlyrPerformance: Double? = 0.0

  constructor()

  constructor(symbol: String?, sectorSymbol: String?, quotes: List<StockQuote>, orderBlocks: List<OrderBlock>, ovtlyrPerformance: Double? = 0.0) {
    this.symbol = symbol
    this.sectorSymbol = sectorSymbol
    this.quotes = quotes
    this.orderBlocks = orderBlocks
    this.ovtlyrPerformance = ovtlyrPerformance
  }

  /**
   *
   * @param entryStrategy
   * @param after - quotes after or on the date (inclusive)
   * @param before - quotes before or on the date (inclusive)
   * @return quotes that matches the given entry strategy.
   */
  fun getQuotesMatchingEntryStrategy(entryStrategy: EntryStrategy, after: LocalDate?, before: LocalDate?) =
    quotes
      .filter { after == null || it.date?.isAfter(after.minusDays(1)) == true }
      .filter { before == null || it.date?.isBefore(before.plusDays(1)) == true }
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
        stock = this,
        entryQuote = entryQuote,
        quote = quote
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
   * @param quote The quote to check
   * @param daysOld Minimum age of order block in days
   * @param source Filter by order block source (null = all sources)
   */
  fun withinOrderBlock(quote: StockQuote, daysOld: Int, source: OrderBlockSource? = null): Boolean {
    return orderBlocks
      .filter { source == null || it.source == source }
      .filter {
        ChronoUnit.DAYS.between(
          it.startDate,
          it.endDate ?: LocalDate.now()
        ) >= daysOld
      }
      .filter { it.orderBlockType == OrderBlockType.BEARISH }
      .filter { it.startDate.isBefore(quote.date) }
      .filter { it.endDate?.isAfter(quote.date) == true }
      .any { quote.closePrice > it.low && quote.closePrice < it.high }
  }

  /**
   * Get calculated order blocks only
   */
  fun getCalculatedOrderBlocks(): List<OrderBlock> =
    orderBlocks.filter { it.source == OrderBlockSource.CALCULATED }

  /**
   * Get Ovtlyr order blocks only
   */
  fun getOvtlyrOrderBlocks(): List<OrderBlock> =
    orderBlocks.filter { it.source == OrderBlockSource.OVTLYR }

  /**
   * Get active order blocks (not mitigated)
   */
  fun getActiveOrderBlocks(): List<OrderBlock> =
    orderBlocks.filter { it.endDate == null }

  /**
   * Get bullish order blocks for the given date
   */
  fun getBullishOrderBlocks(date: LocalDate? = null): List<OrderBlock> =
    orderBlocks.filter {
      it.orderBlockType == OrderBlockType.BULLISH &&
        (date == null || (it.startDate.isBefore(date) && (it.endDate == null || it.endDate.isAfter(date))))
    }

  /**
   * Get bearish order blocks for the given date
   */
  fun getBearishOrderBlocks(date: LocalDate? = null): List<OrderBlock> =
    orderBlocks.filter {
      it.orderBlockType == OrderBlockType.BEARISH &&
        (date == null || (it.startDate.isBefore(date) && (it.endDate == null || it.endDate.isAfter(date))))
    }

  override fun toString() = "Symbol: $symbol"
}
