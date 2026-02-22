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

  private fun processTradesChronologically(
    trades: List<Trade>,
    config: PositionSizingConfig,
  ): PositionSizingResult {
    val events = buildEventList(trades)
    val state = PortfolioState(config.startingCapital)
    val openPositions = mutableMapOf<Trade, OpenPosition>()

    state.equityCurve.add(PortfolioEquityPoint(date = events.first().date, portfolioValue = config.startingCapital))

    for (event in events) {
      when (event) {
        is TradeEvent.Entry -> processEntry(event, config, state, openPositions)
        is TradeEvent.Exit -> processExit(event, state, openPositions)
      }
    }

    val totalReturnPct =
      if (config.startingCapital > 0.0) {
        ((state.portfolioValue - config.startingCapital) / config.startingCapital) * 100.0
      } else {
        0.0
      }

    return PositionSizingResult(
      startingCapital = config.startingCapital,
      finalCapital = state.portfolioValue,
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
    var shares = calculateShares(state.portfolioValue, event.trade.entryQuote.atr, config)
    val entryPrice = event.trade.entryQuote.closePrice

    if (config.leverageRatio != null && shares > 0 && entryPrice > 0.0) {
      val maxNotional = state.portfolioValue * config.leverageRatio
      val availableNotional = maxNotional - state.openNotional
      if (availableNotional <= 0.0) {
        shares = 0
      } else {
        val cappedShares = floor(availableNotional / entryPrice).toInt()
        shares = minOf(shares, cappedShares)
      }
    }

    state.openNotional += shares * entryPrice
    openPositions[event.trade] = OpenPosition(shares = shares, portfolioValueAtEntry = state.portfolioValue, entryPrice = entryPrice)
  }

  private fun processExit(
    event: TradeEvent.Exit,
    state: PortfolioState,
    openPositions: MutableMap<Trade, OpenPosition>,
  ) {
    val position = openPositions.remove(event.trade) ?: return
    state.openNotional -= position.shares * position.entryPrice
    val dollarProfit = position.shares * event.trade.profit
    state.portfolioValue += dollarProfit

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

    state.peakCapital = max(state.peakCapital, state.portfolioValue)
    val drawdownDollars = state.peakCapital - state.portfolioValue
    val drawdownPct = if (state.peakCapital > 0.0) (drawdownDollars / state.peakCapital) * 100.0 else 0.0
    if (drawdownDollars > state.maxDrawdownDollars) state.maxDrawdownDollars = drawdownDollars
    if (drawdownPct > state.maxDrawdownPct) state.maxDrawdownPct = drawdownPct

    state.equityCurve.add(PortfolioEquityPoint(date = event.date, portfolioValue = state.portfolioValue))
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
    var portfolioValue: Double = startingCapital
    var peakCapital: Double = startingCapital
    var maxDrawdownDollars: Double = 0.0
    var maxDrawdownPct: Double = 0.0
    var openNotional: Double = 0.0
    val sizedTrades: MutableList<PositionSizedTrade> = mutableListOf()
    val equityCurve: MutableList<PortfolioEquityPoint> = mutableListOf()
  }

  private data class OpenPosition(
    val shares: Int,
    val portfolioValueAtEntry: Double,
    val entryPrice: Double = 0.0,
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
