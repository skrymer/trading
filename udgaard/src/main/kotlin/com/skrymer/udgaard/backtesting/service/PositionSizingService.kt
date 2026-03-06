package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.PortfolioEquityPoint
import com.skrymer.udgaard.backtesting.model.PositionSizedTrade
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.PositionSizingResult
import com.skrymer.udgaard.backtesting.model.Trade
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.max

@Service
class PositionSizingService {
  fun applyPositionSizing(
    trades: List<Trade>,
    config: PositionSizingConfig,
  ): PositionSizingResult {
    if (trades.isEmpty()) {
      return emptyResult(config)
    }

    return processTradesChronologically(trades, config)
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
  ): PositionSizingResult {
    // Build per-trade quote lookup: date -> closePrice
    val tradeQuoteMap = buildTradeQuoteMaps(trades)

    // Build entry/exit events
    val events = buildEventList(trades)

    // Collect all unique trading dates from all trades (entry dates + quote dates)
    val tradingDates = collectTradingDates(trades)

    val state = PortfolioState(config.startingCapital)
    val openPositions = mutableMapOf<Trade, OpenPosition>()

    // Index into events list for efficient processing
    var eventIndex = 0

    for (date in tradingDates) {
      // Process all events for this date (entries before exits)
      while (eventIndex < events.size && events[eventIndex].date == date) {
        when (val event = events[eventIndex]) {
          is TradeEvent.Entry -> processEntry(event, config, state, openPositions)
          is TradeEvent.Exit -> processExit(event, state, openPositions)
        }
        eventIndex++
      }

      // Daily mark-to-market: compute portfolio value from cash + unrealized P/L of open positions
      // Note: cash tracks starting capital + realized P/L (not reduced by purchases),
      // so open position value must be unrealized P/L only (not full market value).
      if (openPositions.isNotEmpty()) {
        val unrealizedPnl = computeUnrealizedPnl(openPositions, tradeQuoteMap, date)
        val dailyPortfolioValue = state.cash + unrealizedPnl
        updateDrawdown(state, dailyPortfolioValue)
        state.equityCurve.add(PortfolioEquityPoint(date = date, portfolioValue = dailyPortfolioValue))
      } else if (state.equityCurve.isEmpty() || state.equityCurve.last().date != date) {
        // No open positions -- record cash-only equity point on event days
        val hasEventsToday = eventIndex > 0 && events[eventIndex - 1].date == date
        if (hasEventsToday || state.equityCurve.isEmpty()) {
          updateDrawdown(state, state.cash)
          state.equityCurve.add(PortfolioEquityPoint(date = date, portfolioValue = state.cash))
        }
      }
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

  private fun processEntry(
    event: TradeEvent.Entry,
    config: PositionSizingConfig,
    state: PortfolioState,
    openPositions: MutableMap<Trade, OpenPosition>,
  ) {
    // Use cash for portfolio value calculation (consistent with available capital)
    var shares = calculateShares(state.cash, event.trade.entryQuote.atr, config)
    val entryPrice = event.trade.entryQuote.closePrice

    if (config.leverageRatio != null && shares > 0 && entryPrice > 0.0) {
      val maxNotional = state.cash * config.leverageRatio
      val availableNotional = maxNotional - state.openNotional
      if (availableNotional <= 0.0) {
        shares = 0
      } else {
        val cappedShares = floor(availableNotional / entryPrice).toInt()
        shares = minOf(shares, cappedShares)
      }
    }

    state.openNotional += shares * entryPrice
    openPositions[event.trade] = OpenPosition(
      shares = shares,
      portfolioValueAtEntry = state.cash,
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

    val exitDate =
      event.trade.quotes
        .maxByOrNull { it.date }
        ?.date
        ?: event.date

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
      val exitDate = trade.quotes.maxByOrNull { it.date }?.date ?: entryDate

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
    /**
     * Calculate position size for a single trade given current portfolio value and config.
     * Reusable by Monte Carlo techniques for sequential position sizing.
     */
    fun calculateShares(
      portfolioValue: Double,
      atr: Double,
      config: PositionSizingConfig,
    ): Int =
      if (atr <= 0.0 || portfolioValue <= 0.0) {
        0
      } else {
        floor(portfolioValue * (config.riskPercentage / 100.0) / (config.nAtr * atr)).toInt()
      }
  }
}
