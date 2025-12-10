package com.skrymer.udgaard.model

import com.fasterxml.jackson.annotation.JsonManagedReference
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import jakarta.persistence.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Represents a stock with a list of quotes.
 */
@Entity
@Table(name = "stocks")
class Stock {
  @Id
  @Column(length = 20)
  var symbol: String? = null

  @Column(name = "sector_symbol", length = 50)
  var sectorSymbol: String? = null

  @OneToMany(mappedBy = "stock", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
  var quotes: MutableList<StockQuote> = mutableListOf()

  @OneToMany(mappedBy = "stock", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
  var orderBlocks: MutableList<OrderBlock> = mutableListOf()

  @OneToMany(mappedBy = "stock", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
  @JsonManagedReference
  var earnings: MutableList<Earning> = mutableListOf()

  @Column(name = "ovtlyr_performance")
  var ovtlyrPerformance: Double? = 0.0

  constructor()

  constructor(symbol: String?, sectorSymbol: String?, quotes: MutableList<StockQuote>, orderBlocks: MutableList<OrderBlock>, earnings: MutableList<Earning> = mutableListOf(), ovtlyrPerformance: Double? = 0.0) {
    this.symbol = symbol
    this.sectorSymbol = sectorSymbol
    this.quotes = quotes
    this.orderBlocks = orderBlocks
    this.earnings = earnings
    this.ovtlyrPerformance = ovtlyrPerformance
  }

  /**
   *
   * @param entryStrategy
   * @param after - quotes after or on the date (inclusive)
   * @param before - quotes before or on the date (inclusive)
   * @return quotes that matches the given entry strategy.
   */
  fun getQuotesMatchingEntryStrategy(
    entryStrategy: EntryStrategy,
    after: LocalDate?,
    before: LocalDate?,
  ) = quotes
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
    exitStrategy: ExitStrategy,
  ): ExitReport {
    val quotesToTest =
      this.quotes
        .sortedBy { it.date }
        // Find quotes that are after the entry quote date
        .filter { it -> it.date?.isAfter(entryQuote.date) == true }

    val quotes = ArrayList<StockQuote>()
    var exitReason = ""
    var exitPrice = 0.0

    for (quote in quotesToTest) {
      val exitStrategyReport =
        exitStrategy.test(
          stock = this,
          entryQuote = entryQuote,
          quote = quote,
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
  fun getNextQuote(quote: StockQuote) = quotes.sortedBy { it.date }.firstOrNull { it -> it.date?.isAfter(quote.date) == true }

  /**
   * Get the quote previous to the given [quote]
   */
  fun getPreviousQuote(quote: StockQuote) =
    quotes.sortedByDescending { it.date }.firstOrNull { it -> it.date?.isBefore(quote.date) == true }

  /**
   * get the quote for the given [date]
   */
  fun getQuoteByDate(date: LocalDate) = quotes.find { it.date?.equals(date) == true }

  /**
   * Check if given [quote] is within an order block that are older than [daysOld]
   * @param quote The quote to check
   * @param daysOld Minimum age of order block in days
   */
  fun withinOrderBlock(
    quote: StockQuote,
    daysOld: Int,
  ): Boolean =
    orderBlocks
      .filter {
        ChronoUnit.DAYS.between(
          it.startDate,
          it.endDate ?: LocalDate.now(),
        ) >= daysOld
      }.filter { it.orderBlockType == OrderBlockType.BEARISH }
      .filter { it.startDate.isBefore(quote.date) }
      .filter { it.endDate?.isAfter(quote.date) == true }
      .any { quote.closePrice > it.low && quote.closePrice < it.high }

  /**
   * Get active order blocks (not mitigated)
   */
  fun getActiveOrderBlocks(): List<OrderBlock> = orderBlocks.filter { it.endDate == null }

  /**
   * Get bullish order blocks for the given date
   */
  fun getBullishOrderBlocks(date: LocalDate? = null): List<OrderBlock> =
    orderBlocks.filter {
      val endDate = it.endDate
      it.orderBlockType == OrderBlockType.BULLISH &&
        (date == null || (it.startDate.isBefore(date) && (endDate == null || endDate.isAfter(date))))
    }

  /**
   * Get bearish order blocks for the given date
   */
  fun getBearishOrderBlocks(date: LocalDate? = null): List<OrderBlock> =
    orderBlocks.filter {
      val endDate = it.endDate
      it.orderBlockType == OrderBlockType.BEARISH &&
        (date == null || (it.startDate.isBefore(date) && (endDate == null || endDate.isAfter(date))))
    }

  /**
   * Get next earnings date after a given date
   *
   * @param afterDate Date to check from (exclusive)
   * @return Next earnings announcement, or null if none found
   */
  fun getNextEarningsDate(afterDate: LocalDate): Earning? =
    earnings
      .filter { it.reportedDate != null }
      .filter { it.reportedDate!!.isAfter(afterDate) }
      .minByOrNull { it.reportedDate!! }

  /**
   * Check if there's an earnings announcement within N days of the given date
   *
   * @param date Date to check from
   * @param days Number of days to look ahead
   * @return true if earnings are within the specified days
   */
  fun hasEarningsWithinDays(
    date: LocalDate,
    days: Int,
  ): Boolean = earnings.any { it.isWithinDaysOf(date, days) }

  /**
   * Get earnings for a specific fiscal quarter
   *
   * @param fiscalDateEnding Fiscal quarter ending date
   * @return Earning for that quarter, or null if not found
   */
  fun getEarningsByFiscalDate(fiscalDateEnding: LocalDate): Earning? = earnings.find { it.fiscalDateEnding == fiscalDateEnding }

  override fun toString() = "Symbol: $symbol"
}
