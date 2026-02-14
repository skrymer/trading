package com.skrymer.udgaard.data.model

import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import java.time.LocalDate

/**
 * Domain model for Stock (Hibernate-independent)
 * Contains all business logic from the original Stock entity.
 *
 * INVARIANT: [quotes] must be sorted by date ascending. This is guaranteed by
 * the repository layer (ORDER BY QUOTE_DATE ASC) and preserved through mappers.
 */
data class Stock(
  val symbol: String = "",
  val sectorSymbol: String? = null,
  val ovtlyrPerformance: Double? = 0.0,
  val quotes: List<StockQuote> = emptyList(),
  val orderBlocks: List<OrderBlock> = emptyList(),
  val earnings: List<Earning> = emptyList(),
) {
  init {
    require(quotes.zipWithNext().all { (a, b) -> !a.date.isAfter(b.date) }) {
      "Stock $symbol quotes must be sorted by date ascending"
    }
  }

  /**
   * Index of the first quote with date > targetDate. O(log n).
   * Returns quotes.size if all quotes are on or before targetDate.
   */
  private fun indexAfter(targetDate: LocalDate): Int {
    var lo = 0
    var hi = quotes.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (quotes[mid].date <= targetDate) lo = mid + 1 else hi = mid
    }
    return lo
  }

  /**
   * Index of the first quote with date >= targetDate. O(log n).
   * Returns quotes.size if all quotes are before targetDate.
   */
  private fun indexOnOrAfter(targetDate: LocalDate): Int {
    var lo = 0
    var hi = quotes.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (quotes[mid].date < targetDate) lo = mid + 1 else hi = mid
    }
    return lo
  }

  /**
   * @param entryStrategy
   * @param after - quotes after or on the date (inclusive)
   * @param before - quotes before or on the date (inclusive)
   * @return quotes that matches the given entry strategy.
   */
  fun getQuotesMatchingEntryStrategy(
    entryStrategy: EntryStrategy,
    after: LocalDate?,
    before: LocalDate?,
  ): List<StockQuote> {
    val startIdx = if (after != null) indexOnOrAfter(after) else 0
    val endIdx = if (before != null) indexAfter(before) else quotes.size
    return quotes.subList(startIdx, endIdx).filter { entryStrategy.test(this, it) }
  }

  /**
   * @param entryQuote - the entry quote to start the simulation from.
   * @param exitStrategy - the exit strategy being used.
   * @return an ExitReport.
   */
  fun testExitStrategy(
    entryQuote: StockQuote,
    exitStrategy: ExitStrategy,
  ): ExitReport {
    val startIdx = indexAfter(entryQuote.date)
    val resultQuotes = ArrayList<StockQuote>()
    var exitReason = ""
    var exitPrice = 0.0

    for (i in startIdx until quotes.size) {
      val quote = quotes[i]
      val exitStrategyReport =
        exitStrategy.test(
          stock = this,
          entryQuote = entryQuote,
          quote = quote,
        )

      resultQuotes.add(quote)

      if (exitStrategyReport.match) {
        exitReason = exitStrategyReport.exitReason ?: ""
        exitPrice = exitStrategyReport.exitPrice
        break
      }
    }

    return ExitReport(exitReason, resultQuotes, exitPrice)
  }

  /**
   * @param quote
   * @return the next quote after the given [quote]. O(log n).
   */
  fun getNextQuote(quote: StockQuote): StockQuote? {
    val idx = indexAfter(quote.date)
    return if (idx < quotes.size) quotes[idx] else null
  }

  /**
   * Get the quote previous to the given [quote]. O(log n).
   */
  fun getPreviousQuote(quote: StockQuote): StockQuote? {
    val idx = indexOnOrAfter(quote.date) - 1
    return if (idx >= 0) quotes[idx] else null
  }

  /**
   * Get the quote for the given [date]. O(log n).
   */
  fun getQuoteByDate(date: LocalDate): StockQuote? {
    val idx = indexOnOrAfter(date)
    return if (idx < quotes.size && quotes[idx].date == date) quotes[idx] else null
  }

  /**
   * Count trading days between two dates by counting quotes. O(log n).
   * This matches TradingView's bar-based age calculation.
   * Counts quotes where startDate < date <= endDate.
   * Public so entry conditions can use it for OB age calculations.
   */
  fun countTradingDaysBetween(
    startDate: LocalDate,
    endDate: LocalDate,
  ): Int = (indexAfter(endDate) - indexAfter(startDate)).coerceAtLeast(0)

  fun withinOrderBlock(
    quote: StockQuote,
    tradingDaysOld: Int,
    useHighPrice: Boolean = false,
    sensitivity: OrderBlockSensitivity? = null,
  ): Boolean {
    // Check if candle overlaps with OB zone using high or body
    val candleTop = if (useHighPrice) quote.high else maxOf(quote.openPrice, quote.closePrice)
    val candleBottom = minOf(quote.openPrice, quote.closePrice)

    return orderBlocks
      .filter { it.orderBlockType == OrderBlockType.BEARISH }
      .filter { sensitivity == null || it.sensitivity == sensitivity }
      .filter { it.startsBefore(quote.date) }
      .filter { it.endsAfter(quote.date) }
      .filter { countTradingDaysBetween(it.startDate, quote.date) >= tradingDaysOld }
      .any { candleTop >= it.low && candleBottom <= it.high }
  }

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

/**
 * Exit report containing the result of testing an exit strategy
 */
data class ExitReport(
  val exitReason: String,
  val quotes: List<StockQuote>,
  val exitPrice: Double,
)
