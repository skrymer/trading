package com.skrymer.udgaard.data.model

import com.skrymer.udgaard.backtesting.model.BacktestContext
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
  val quotes: List<StockQuote> = emptyList(),
  val orderBlocks: List<OrderBlock> = emptyList(),
  val earnings: List<Earning> = emptyList(),
  val fundamentals: List<Fundamental> = emptyList(),
  val ovtlyrSignals: List<OvtlyrSignal> = emptyList(),
  val listingDate: LocalDate? = null,
  val delistingDate: LocalDate? = null,
  val assetType: AssetType? = null,
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
  internal fun indexAfter(targetDate: LocalDate): Int {
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
  internal fun indexOnOrAfter(targetDate: LocalDate): Int {
    var lo = 0
    var hi = quotes.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (quotes[mid].date < targetDate) lo = mid + 1 else hi = mid
    }
    return lo
  }

  /**
   * Get quotes in the inclusive date range [from, to]. O(log n).
   */
  fun quotesInRange(from: LocalDate, to: LocalDate): List<StockQuote> {
    val startIdx = indexOnOrAfter(from)
    val endIdx = indexAfter(to)
    return quotes.subList(startIdx, endIdx)
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
    context: BacktestContext = BacktestContext.EMPTY,
  ): List<StockQuote> {
    val startIdx = if (after != null) indexOnOrAfter(after) else 0
    val endIdx = if (before != null) indexAfter(before) else quotes.size
    return quotes.subList(startIdx, endIdx).filter { entryStrategy.test(this, it, context) }
  }

  /**
   * @param entryQuote - the entry quote to start the simulation from.
   * @param exitStrategy - the exit strategy being used.
   * @param context - backtest context with breadth data
   * @return an ExitReport.
   */
  fun testExitStrategy(
    entryQuote: StockQuote,
    exitStrategy: ExitStrategy,
    context: BacktestContext = BacktestContext.EMPTY,
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
          context = context,
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

    return orderBlocks.any { ob ->
      ob.orderBlockType == OrderBlockType.BEARISH &&
        (sensitivity == null || ob.sensitivity == sensitivity) &&
        ob.startsBefore(quote.date) &&
        ob.endsAfter(quote.date) &&
        countTradingDaysBetween(ob.triggerDate, quote.date) >= tradingDaysOld &&
        candleTop >= ob.low &&
        candleBottom <= ob.high
    }
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

  /**
   * The fundamentals public on [date] — those whose `filingDate` is on or before it — most-recent
   * filing first (by `filingDate`, then `fiscalDateEnding`). This is the point-in-time peer order the
   * TTM accessors and the quality ranker read from; the canonical definition the Midgaard L2 SQL pass
   * mirrors (ADR 0019, CONTEXT *Gross-profitability quality percentile*).
   */
  fun visibleFundamentalsAsOf(date: LocalDate): List<Fundamental> =
    fundamentals
      .filter { it.isVisibleAsOf(date) }
      .sortedWith(compareByDescending<Fundamental> { it.filingDate!! }.thenByDescending { it.fiscalDateEnding })

  /**
   * The single most-recent fundamental public on [date] — the source of the point-in-time balance-sheet
   * values (e.g. total assets), which are stock quantities read as-of, never summed.
   */
  fun latestFundamentalAsOf(date: LocalDate): Fundamental? = visibleFundamentalsAsOf(date).firstOrNull()

  /**
   * Trailing-twelve-month gross profit as of [date] — the sum of `grossProfit` over the four most-recent
   * filings visible by `filingDate` (a flow item, summed to de-seasonalise). Null (the metric is
   * undefined, so the gate/ranker treats the name as having no quality reading) when fewer than four
   * quarters are visible or any of the four omits gross profit. A negative sum is legitimate and kept.
   */
  fun grossProfitTtmAsOf(date: LocalDate): Double? = visibleFundamentalsAsOf(date).ttmSum { it.grossProfit }

  /**
   * Trailing-twelve-month operating margin as of [date] — `operatingIncome_TTM / totalRevenue_TTM`, each
   * summed over the four most-recent visible filings. Null when fewer than four quarters are visible,
   * any quarter omits operating income or revenue, or the revenue sum is zero.
   */
  fun operatingMarginTtmAsOf(date: LocalDate): Double? {
    val visible = visibleFundamentalsAsOf(date)
    val operatingIncome = visible.ttmSum { it.operatingIncome } ?: return null
    val revenue = visible.ttmSum { it.totalRevenue } ?: return null
    return if (revenue == 0.0) null else operatingIncome / revenue
  }

  /**
   * Prior-year trailing-twelve-month operating margin as of [date] — the same ratio as
   * [operatingMarginTtmAsOf] but over the fifth-to-eighth most-recent visible filings, the TTM window
   * one year earlier. Requires at least eight visible filings so this window is *disjoint* from the
   * current one (no shared quarter); the quality ranker's margin-trend leg is the signed YoY change
   * `operatingMarginTtmAsOf(date) − operatingMarginTtmPriorYearAsOf(date)`. Null when fewer than eight
   * quarters are visible, any of the prior-year four omits operating income or revenue, or that
   * revenue sum is zero.
   */
  fun operatingMarginTtmPriorYearAsOf(date: LocalDate): Double? {
    val visible = visibleFundamentalsAsOf(date)
    if (visible.size < 8) return null
    val priorYear = visible.subList(4, 8)
    val operatingIncome = priorYear.ttmSum { it.operatingIncome } ?: return null
    val revenue = priorYear.ttmSum { it.totalRevenue } ?: return null
    return if (revenue == 0.0) null else operatingIncome / revenue
  }

  /**
   * Sums [selector] over the four most-recent filings of this (already most-recent-first) list. Null
   * unless there are at least four and all four carry the selected value — the "exactly four present
   * quarters" rule the Midgaard pass enforces with `filing_rank >= 4 AND ttm_count = 4`.
   */
  private fun List<Fundamental>.ttmSum(selector: (Fundamental) -> Double?): Double? {
    if (size < 4) return null
    var sum = 0.0
    for (i in 0 until 4) {
      sum += selector(this[i]) ?: return null
    }
    return sum
  }

  /**
   * The current Ovtlyr signal as of [asOf] — the most recent stored signal on or before that
   * date. Ovtlyr signals are sparse events; this derives the standing state, so a BUY call
   * reads as BUY on every bar until the next SELL. Null before the symbol's first ever signal.
   */
  fun currentOvtlyrSignal(asOf: LocalDate): OvtlyrSignalType? =
    ovtlyrSignals
      .filter { !it.signalDate.isAfter(asOf) }
      .maxByOrNull { it.signalDate }
      ?.signal

  /**
   * The Ovtlyr signal that fired *exactly* on [date] — the call-day event, or null if no
   * Ovtlyr call fired that day. Unlike [currentOvtlyrSignal] this does not derive the standing
   * state: a bar between a BUY call and the next SELL returns null, not BUY.
   */
  fun ovtlyrSignalOn(date: LocalDate): OvtlyrSignalType? =
    ovtlyrSignals.firstOrNull { it.signalDate == date }?.signal

  override fun toString() = "Symbol: $symbol"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Stock) return false
    return symbol == other.symbol
  }

  override fun hashCode(): Int = symbol.hashCode()
}

/**
 * Exit report containing the result of testing an exit strategy
 */
data class ExitReport(
  val exitReason: String,
  val quotes: List<StockQuote>,
  val exitPrice: Double,
)
