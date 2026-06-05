package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.PortfolioEquityPoint
import com.skrymer.udgaard.backtesting.model.PositionSizedTrade
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.PositionSizingResult
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.service.sizer.PositionSizer
import com.skrymer.udgaard.backtesting.service.sizer.SizingContext
import com.skrymer.udgaard.backtesting.service.sizer.applyLeverageCap
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.max

@Service
class PositionSizingService {
  /**
   * @param tradingCalendar full set of trading days (e.g. SPY quote dates) used to build the daily
   *   spine when crediting idle cash; empty falls back to trade-activity dates (legacy curve).
   * @param riskFreeRateProvider the single `rf_step(t)` source of truth (ADR 0016); also fed to
   *   the Sharpe/Sortino rf so the idle leg nets to zero excess.
   * @param creditIdleCash when true (and a calendar is supplied), idle cash accrues interest daily.
   */
  fun applyPositionSizing(
    trades: List<Trade>,
    config: PositionSizingConfig,
    tradingCalendar: List<LocalDate> = emptyList(),
    riskFreeRateProvider: RiskFreeRateProvider = ZERO_RATE_PROVIDER,
    creditIdleCash: Boolean = false,
  ): PositionSizingResult {
    if (trades.isEmpty()) {
      return emptyResult(config)
    }

    return processTradesChronologically(trades, config, tradingCalendar, riskFreeRateProvider, creditIdleCash)
  }

  /**
   * Day-by-day processing with daily mark-to-market for accurate drawdown calculation.
   *
   * Instead of only tracking portfolio value at trade exits, this iterates through
   * every trading day where positions are open and computes daily portfolio value as:
   *   cash + sum(open position market values at that day's close price)
   *
   * This captures intra-trade drawdowns that exit-based tracking misses.
   */
  private fun processTradesChronologically(
    trades: List<Trade>,
    config: PositionSizingConfig,
    tradingCalendar: List<LocalDate>,
    riskFreeRateProvider: RiskFreeRateProvider,
    creditIdleCash: Boolean,
  ): PositionSizingResult {
    // Build per-trade quote lookup: date -> closePrice
    val tradeQuoteMap = buildTradeQuoteMaps(trades)

    // Build entry/exit events
    val events = buildEventList(trades)

    // Collect all unique trading dates from all trades (entry dates + quote dates)
    val tradingDates = collectTradingDates(trades)

    // When crediting idle cash, iterate a full daily spine (calendar days within the activity span)
    // so interest accrues on every calendar day and the equity curve has a point per day (ADR 0016).
    // The span bounds keep accrual OOS-only — no interest across stitched IS gaps (F7).
    val useSpine = creditIdleCash && tradingCalendar.isNotEmpty()
    val processingDates = if (useSpine) spineDates(tradingCalendar, tradingDates) else tradingDates

    val state = PortfolioState(config.startingCapital)
    val openPositions = mutableMapOf<Trade, OpenPosition>()

    // Index into events list for efficient processing
    var eventIndex = 0
    // Idle balance carried from the previous spine day, and that day's date — the basis for ACT/360 accrual.
    var idleCarried = 0.0
    var prevDate: LocalDate? = null

    for (date in processingDates) {
      // Credit interest for the elapsed period on the idle balance held since the previous day (F-day-count).
      if (useSpine && prevDate != null) {
        state.cash += idleCarried * riskFreeRateProvider.stepRate(prevDate, date)
      }

      // Process all events for this date (entries before exits)
      while (eventIndex < events.size && events[eventIndex].date == date) {
        when (val event = events[eventIndex]) {
          is TradeEvent.Entry -> processEntry(event, config, state, openPositions)
          is TradeEvent.Exit -> processExit(event, state, openPositions)
        }
        eventIndex++
      }

      // Daily mark-to-market: record the equity point for this date. On the full spine this also
      // returns the idle balance carried into tomorrow's interest accrual; off-spine it returns null.
      idleCarried = recordDailyEquity(
        date = date,
        useSpine = useSpine,
        state = state,
        openPositions = openPositions,
        tradeQuoteMap = tradeQuoteMap,
        events = events,
        eventIndex = eventIndex,
      ) ?: idleCarried
      if (useSpine) prevDate = date
    }

    val finalValue = state.cash
    val totalReturnPct =
      if (config.startingCapital > 0.0) {
        ((finalValue - config.startingCapital) / config.startingCapital) * 100.0
      } else {
        0.0
      }

    return PositionSizingResult(
      startingCapital = config.startingCapital,
      finalCapital = finalValue,
      totalReturnPct = totalReturnPct,
      maxDrawdownPct = state.maxDrawdownPct,
      maxDrawdownDollars = state.maxDrawdownDollars,
      peakCapital = state.peakCapital,
      trades = state.sizedTrades,
      equityCurve = state.equityCurve,
    )
  }

  /**
   * Records this date's equity point (daily mark-to-market) onto [state]. Cash tracks starting
   * capital + realized P/L (not reduced by purchases), so an open position contributes only its
   * unrealized P/L, never full market value.
   *
   * On the full spine a point is recorded every calendar day and the new idle balance (uninvested
   * cash, the basis for tomorrow's interest accrual) is returned. Off-spine a point is recorded only
   * on days with open positions or events and null is returned (no idle basis to carry).
   */
  @Suppress("LongParameterList")
  private fun recordDailyEquity(
    date: LocalDate,
    useSpine: Boolean,
    state: PortfolioState,
    openPositions: Map<Trade, OpenPosition>,
    tradeQuoteMap: Map<Trade, Map<LocalDate, Double>>,
    events: List<TradeEvent>,
    eventIndex: Int,
  ): Double? {
    if (useSpine) {
      // Full spine: record a point every calendar day.
      val unrealizedPnl =
        if (openPositions.isNotEmpty()) computeUnrealizedPnl(openPositions, tradeQuoteMap, date) else 0.0
      val dailyPortfolioValue = state.cash + unrealizedPnl
      state.lastPortfolioValue = dailyPortfolioValue
      updateDrawdown(state, dailyPortfolioValue)
      state.equityCurve.add(PortfolioEquityPoint(date = date, portfolioValue = dailyPortfolioValue))
      // Idle = uninvested cash (cost-basis subtrahend); clamp negatives (a levered margin borrow) to zero.
      return max(0.0, state.cash - state.openNotional)
    }
    if (openPositions.isNotEmpty()) {
      val unrealizedPnl = computeUnrealizedPnl(openPositions, tradeQuoteMap, date)
      val dailyPortfolioValue = state.cash + unrealizedPnl
      state.lastPortfolioValue = dailyPortfolioValue
      updateDrawdown(state, dailyPortfolioValue)
      state.equityCurve.add(PortfolioEquityPoint(date = date, portfolioValue = dailyPortfolioValue))
    } else if (state.equityCurve.isEmpty() || state.equityCurve.last().date != date) {
      // No open positions -- record cash-only equity point on event days
      val hasEventsToday = eventIndex > 0 && events[eventIndex - 1].date == date
      if (hasEventsToday || state.equityCurve.isEmpty()) {
        state.lastPortfolioValue = state.cash
        updateDrawdown(state, state.cash)
        state.equityCurve.add(PortfolioEquityPoint(date = date, portfolioValue = state.cash))
      }
    }
    return null
  }

  private fun processEntry(
    event: TradeEvent.Entry,
    config: PositionSizingConfig,
    state: PortfolioState,
    openPositions: MutableMap<Trade, OpenPosition>,
  ) {
    // When no positions are open, portfolio value is just cash
    if (openPositions.isEmpty()) {
      state.lastPortfolioValue = state.cash
    }
    val portfolioValue = state.lastPortfolioValue
    val entryPrice = event.trade.entryQuote.closePrice
    val sizer = scaledSizer(config, state.peakCapital, portfolioValue)
    val ctx = SizingContext(
      portfolioValue = portfolioValue,
      entryPrice = entryPrice,
      atr = event.trade.entryQuote.atr,
      symbol = event.trade.stockSymbol,
      sector = event.trade.sector,
    )
    val rawShares = sizer.calculateShares(ctx)
    val shares = applyLeverageCap(rawShares, entryPrice, portfolioValue, state.openNotional, config.leverageRatio)

    if (shares <= 0) return

    state.openNotional += shares * entryPrice
    openPositions[event.trade] = OpenPosition(
      shares = shares,
      portfolioValueAtEntry = portfolioValue,
      entryPrice = entryPrice,
    )
  }

  private fun processExit(
    event: TradeEvent.Exit,
    state: PortfolioState,
    openPositions: MutableMap<Trade, OpenPosition>,
  ) {
    val position = openPositions.remove(event.trade) ?: return
    state.openNotional -= position.shares * position.entryPrice
    val dollarProfit = position.shares * event.trade.profit
    state.cash += dollarProfit

    val portfolioReturnPct =
      if (position.portfolioValueAtEntry > 0.0) {
        (dollarProfit / position.portfolioValueAtEntry) * 100.0
      } else {
        0.0
      }

    val exitDate = event.trade.quotes
      .lastOrNull()
      ?.date ?: event.date

    state.sizedTrades.add(
      PositionSizedTrade(
        symbol = event.trade.stockSymbol,
        entryDate = event.trade.entryQuote.date,
        exitDate = exitDate,
        shares = position.shares,
        entryPrice = event.trade.entryQuote.closePrice,
        exitPrice = event.trade.entryQuote.closePrice + event.trade.profit,
        dollarProfit = dollarProfit,
        portfolioValueAtEntry = position.portfolioValueAtEntry,
        portfolioReturnPct = portfolioReturnPct,
      ),
    )

    // Peak/drawdown tracking is handled by the daily M2M block which correctly
    // computes portfolio value as cash + unrealized P/L of all remaining open positions.
    // Do NOT track drawdown here from cash alone — it would compare against a peak
    // that includes unrealized gains, creating a phantom drawdown.
  }

  private fun computeUnrealizedPnl(
    openPositions: Map<Trade, OpenPosition>,
    tradeQuoteMap: Map<Trade, Map<LocalDate, Double>>,
    date: LocalDate,
  ): Double =
    openPositions.entries.sumOf { (trade, pos) ->
      val quoteMap = tradeQuoteMap[trade] ?: emptyMap()
      val closePrice = quoteMap[date] ?: pos.lastKnownPrice
      pos.lastKnownPrice = closePrice
      pos.shares * (closePrice - pos.entryPrice)
    }

  private fun updateDrawdown(
    state: PortfolioState,
    portfolioValue: Double,
  ) {
    state.peakCapital = max(state.peakCapital, portfolioValue)
    val drawdownDollars = state.peakCapital - portfolioValue
    val drawdownPct = if (state.peakCapital > 0.0) (drawdownDollars / state.peakCapital) * 100.0 else 0.0
    if (drawdownDollars > state.maxDrawdownDollars) state.maxDrawdownDollars = drawdownDollars
    if (drawdownPct > state.maxDrawdownPct) state.maxDrawdownPct = drawdownPct
  }

  /**
   * Build a map of Trade -> (date -> closePrice) for fast M2M lookup.
   */
  private fun buildTradeQuoteMaps(trades: List<Trade>): Map<Trade, Map<LocalDate, Double>> =
    trades.associateWith { trade ->
      val map = mutableMapOf<LocalDate, Double>()
      // Include entry quote
      map[trade.entryQuote.date] = trade.entryQuote.closePrice
      // Include all trade quotes
      trade.quotes.forEach { q -> map[q.date] = q.closePrice }
      map
    }

  /**
   * The daily spine: every calendar trading day within the trades' activity span [first, last],
   * unioned with the trade-activity dates so no entry/exit day is ever missing. Restricting to the
   * activity span is what keeps idle accrual out-of-sample only — a window's spine never extends
   * across the in-sample gap to the next window (ADR 0005 / F7).
   */
  private fun spineDates(
    tradingCalendar: List<LocalDate>,
    tradingDates: List<LocalDate>,
  ): List<LocalDate> {
    val first = tradingDates.first()
    val last = tradingDates.last()
    val dates = sortedSetOf<LocalDate>()
    dates.addAll(tradingDates)
    tradingCalendar.filterTo(dates) { !it.isBefore(first) && !it.isAfter(last) }
    return dates.toList()
  }

  /**
   * Collect all unique trading dates across all trades, sorted.
   */
  private fun collectTradingDates(trades: List<Trade>): List<LocalDate> {
    val dates = sortedSetOf<LocalDate>()
    for (trade in trades) {
      dates.add(trade.entryQuote.date)
      trade.quotes.forEach { dates.add(it.date) }
    }
    return dates.toList()
  }

  private fun buildEventList(trades: List<Trade>): List<TradeEvent> {
    val events = mutableListOf<TradeEvent>()

    for (trade in trades) {
      val entryDate = trade.entryQuote.date
      val exitDate = trade.quotes.lastOrNull()?.date ?: entryDate

      events.add(TradeEvent.Entry(date = entryDate, trade = trade))
      events.add(TradeEvent.Exit(date = exitDate, trade = trade))
    }

    // Sort: by date, then entries before exits on the same day
    events.sortWith(compareBy<TradeEvent> { it.date }.thenBy { if (it is TradeEvent.Entry) 0 else 1 })

    return events
  }

  private fun emptyResult(config: PositionSizingConfig): PositionSizingResult =
    PositionSizingResult(
      startingCapital = config.startingCapital,
      finalCapital = config.startingCapital,
      totalReturnPct = 0.0,
      maxDrawdownPct = 0.0,
      maxDrawdownDollars = 0.0,
      peakCapital = config.startingCapital,
      trades = emptyList(),
      equityCurve = emptyList(),
    )

  private class PortfolioState(
    startingCapital: Double,
  ) {
    var cash: Double = startingCapital
    var peakCapital: Double = startingCapital
    var lastPortfolioValue: Double = startingCapital
    var maxDrawdownDollars: Double = 0.0
    var maxDrawdownPct: Double = 0.0
    var openNotional: Double = 0.0
    val sizedTrades: MutableList<PositionSizedTrade> = mutableListOf()
    val equityCurve: MutableList<PortfolioEquityPoint> = mutableListOf()
  }

  private class OpenPosition(
    val shares: Int,
    val portfolioValueAtEntry: Double,
    val entryPrice: Double = 0.0,
    var lastKnownPrice: Double = entryPrice,
  )

  private sealed class TradeEvent {
    abstract val date: LocalDate
    abstract val trade: Trade

    data class Entry(
      override val date: LocalDate,
      override val trade: Trade,
    ) : TradeEvent()

    data class Exit(
      override val date: LocalDate,
      override val trade: Trade,
    ) : TradeEvent()
  }

  companion object {
    /** A rate provider that always returns 0 — the default when idle-cash crediting is off (raw rf ≡ 0). */
    private val ZERO_RATE_PROVIDER = RiskFreeRateProvider(emptyMap(), expensePct = 0.0)

    /**
     * Build the effective sizer for a trade entry given the config and current portfolio state.
     * Applies drawdown scaling if configured: the matched threshold's multiplier is passed to
     * [PositionSizer.scale], and each sizer decides how to interpret it (which parameter scales).
     *
     * If no drawdown scaling applies, returns the base sizer derived from `config.sizer`.
     */
    fun scaledSizer(
      config: PositionSizingConfig,
      peakValue: Double,
      currentValue: Double,
    ): PositionSizer {
      val multiplier = drawdownMultiplier(config, peakValue, currentValue)
        ?: return config.baseSizer
      return config.baseSizer.scale(multiplier)
    }

    private fun drawdownMultiplier(
      config: PositionSizingConfig,
      peakValue: Double,
      currentValue: Double,
    ): Double? {
      val scaling = config.drawdownScaling ?: return null
      if (peakValue <= 0.0) return null
      val ddPct = ((peakValue - currentValue) / peakValue) * 100.0
      return scaling.findMatch(ddPct)?.riskMultiplier
    }
  }
}
