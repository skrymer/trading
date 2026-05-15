package com.skrymer.udgaard.portfolio.model

import java.time.LocalDate

/**
 * Position statistics for a portfolio
 */
data class PositionStats(
  val totalTrades: Int,
  val openTrades: Int,
  val closedTrades: Int,
  val ytdReturn: Double,
  val annualizedReturn: Double,
  val avgWin: Double,
  val avgLoss: Double,
  val winRate: Double,
  val provenEdge: Double,
  val profitFactor: Double? = null,
  val totalProfit: Double,
  val totalProfitPercentage: Double,
  val largestWin: Double? = null,
  val largestLoss: Double? = null,
  val numberOfWins: Int = 0,
  val numberOfLosses: Int = 0,
  val totalCommissions: Double = 0.0,
  val totalRealizedFxPnl: Double? = null,
  val currentFxRate: Double? = null,
  val totalDeposits: Double = 0.0,
  val totalWithdrawals: Double = 0.0,
  val byStrategy: List<StrategyBreakdownStats> = emptyList(),
)

/**
 * Per-strategy slice of a portfolio's closed positions. Owns the breakdown math (win rate,
 * edge, profit factor) via [fromPositions] per ADR 0001 — the service only groups and maps.
 *
 * `edge` is the expected percentage return per trade: `avgWinPct·winRate − avgLossPct·lossRate`.
 * Dollar and percentage averages of losses are stored as positive magnitudes.
 */
data class StrategyBreakdownStats(
  val strategy: String,
  val trades: Int,
  val wins: Int,
  val losses: Int,
  val winRate: Double,
  val edge: Double,
  val avgWinPct: Double,
  val avgLossPct: Double,
  val avgWinDollars: Double,
  val avgLossDollars: Double,
  val profitFactor: Double?,
  val totalPnl: Double,
) {
  companion object {
    fun fromPositions(strategy: String, positions: List<Position>): StrategyBreakdownStats {
      val wins = positions.filter { (it.realizedPnl ?: 0.0) > 0.0 }
      val losses = positions.filter { (it.realizedPnl ?: 0.0) < 0.0 }
      // Derive both rates from their own counts (not `100 − winRate`) so positions with zero or
      // null realised P&L — neither a win nor a loss — don't get charged to the loss side.
      val winRate = if (positions.isNotEmpty()) (wins.size.toDouble() / positions.size) * 100.0 else 0.0
      val lossRate = if (positions.isNotEmpty()) (losses.size.toDouble() / positions.size) * 100.0 else 0.0

      val avgWinDollars = if (wins.isNotEmpty()) wins.sumOf { it.realizedPnl ?: 0.0 } / wins.size else 0.0
      val avgLossDollars =
        if (losses.isNotEmpty()) kotlin.math.abs(losses.sumOf { it.realizedPnl ?: 0.0 } / losses.size) else 0.0
      val avgWinPct = avgPnlPercentage(wins)
      val avgLossPct = kotlin.math.abs(avgPnlPercentage(losses))

      val grossProfit = wins.sumOf { it.realizedPnl ?: 0.0 }
      val grossLoss = kotlin.math.abs(losses.sumOf { it.realizedPnl ?: 0.0 })
      // Undefined when there are no losses — never zero or infinity (see CONTEXT.md: Profit factor)
      val profitFactor = if (losses.isNotEmpty() && grossLoss > 0.0) grossProfit / grossLoss else null

      return StrategyBreakdownStats(
        strategy = strategy,
        trades = positions.size,
        wins = wins.size,
        losses = losses.size,
        winRate = winRate,
        edge = (avgWinPct * winRate / 100.0) - (avgLossPct * lossRate / 100.0),
        avgWinPct = avgWinPct,
        avgLossPct = avgLossPct,
        avgWinDollars = avgWinDollars,
        avgLossDollars = avgLossDollars,
        profitFactor = profitFactor,
        totalPnl = positions.sumOf { it.realizedPnl ?: 0.0 },
      )
    }

    /**
     * Average per-position percentage return (`realizedPnl / totalCost · 100`). Positions with a
     * non-positive cost basis are excluded — they have no meaningful percentage.
     */
    private fun avgPnlPercentage(positions: List<Position>): Double {
      if (positions.isEmpty()) return 0.0
      return positions
        .filter { it.totalCost > 0 }
        .map { ((it.realizedPnl ?: 0.0) / it.totalCost) * 100.0 }
        .ifEmpty { null }
        ?.average() ?: 0.0
    }
  }
}

/**
 * Position with all its executions — the rich aggregate root for Position state transitions
 * and derived P&L. Owns both the data and the rules (per ADR 0001).
 */
data class PositionWithExecutions(
  val position: Position,
  val executions: List<Execution>,
) {
  /**
   * Realized P&L from all executions. Derived from buy / sell totals × position multiplier.
   * Stocks: multiplier = 1. Options: multiplier = 100 (or whatever the contract specifies).
   */
  val realizedPnl: Double
    get() {
      val buys = executions.filter { it.quantity > 0 }
      val sells = executions.filter { it.quantity < 0 }
      val totalBoughtShares = buys.sumOf { it.quantity }
      val totalSoldShares = sells.sumOf { kotlin.math.abs(it.quantity) }
      val matched = kotlin.math.min(totalBoughtShares, totalSoldShares)
      if (matched == 0) return 0.0
      val avgBuyPrice = buys.sumOf { it.quantity * it.price } / totalBoughtShares
      val avgSellPrice = sells.sumOf { kotlin.math.abs(it.quantity) * it.price } / totalSoldShares
      return matched * (avgSellPrice - avgBuyPrice) * position.multiplier
    }

  /**
   * Total commission cost across all executions. Null-safe: a missing commission is treated as 0.
   * Sign convention follows the broker — commissions usually arrive as negative numbers.
   */
  val totalCommissions: Double
    get() = executions.sumOf { it.commission ?: 0.0 }

  /**
   * Realized P&L expressed in the portfolio's base currency. Each execution leg is weighted by
   * its own `fxRateToBase` (rate at trade time), not by an averaged rate. Returns null when no
   * execution carries an FX rate (e.g., USD-base portfolio with USD-quoted stock).
   */
  val realizedPnlBase: Double?
    get() {
      if (executions.none { it.fxRateToBase != null }) return null
      val buys = executions.filter { it.quantity > 0 }
      val sells = executions.filter { it.quantity < 0 }
      val totalBoughtShares = buys.sumOf { it.quantity }
      val totalSoldShares = sells.sumOf { kotlin.math.abs(it.quantity) }
      val matched = kotlin.math.min(totalBoughtShares, totalSoldShares)
      // No matched legs yet → "not applicable" rather than a false 0. Mirrors the no-FX-rate
      // gate above so callers don't have to distinguish "no FX context" from "no realised legs".
      if (matched == 0) return null
      val avgBuyBase = buys.sumOf { it.quantity * it.price * (it.fxRateToBase ?: 1.0) } / totalBoughtShares
      val avgSellBase = sells.sumOf { kotlin.math.abs(it.quantity) * it.price * (it.fxRateToBase ?: 1.0) } / totalSoldShares
      return matched * (avgSellBase - avgBuyBase) * position.multiplier
    }

  /**
   * Transition the position to CLOSED at the given date. Returns a new aggregate whose underlying
   * Position has status=CLOSED, closedDate set, currentQuantity zeroed, currentContracts zeroed
   * (for options), and realizedPnl populated from the current executions. The aggregate is the
   * canonical writer for `position.realizedPnl` — services should never set it directly.
   */
  fun withClosed(closeDate: LocalDate): PositionWithExecutions =
    copy(
      position = position.copy(
        status = PositionStatus.CLOSED,
        closedDate = closeDate,
        currentQuantity = 0,
        currentContracts = if (position.instrumentType == InstrumentType.OPTION) 0 else null,
        realizedPnl = realizedPnl,
        realizedPnlBase = realizedPnlBase,
      ),
    )

  /**
   * Append an execution to the aggregate. The returned aggregate's derived properties
   * (realizedPnl, realizedPnlBase, totalCommissions) reflect the new execution list.
   */
  fun withExecutionAdded(execution: Execution): PositionWithExecutions =
    copy(executions = executions + execution)

  /**
   * Recalculate position aggregates (currentQuantity, averageEntryPrice, totalCost) from
   * executions. Uses a running average that resets when quantity hits 0 — between roll legs
   * the average reflects the current leg only, not a blend of all historical buys.
   * `currentContracts` is synced to `currentQuantity` for options.
   */
  fun recalculated(): PositionWithExecutions {
    val statsMultiplier = if (position.instrumentType == InstrumentType.OPTION) position.multiplier else 1
    val totalCost = executions.filter { it.quantity > 0 }.sumOf { it.quantity * it.price } * statsMultiplier

    var runningQty = 0
    var runningCost = 0.0
    var avgEntryPrice = 0.0
    for (exec in executions.sortedBy { it.executionDate }) {
      if (exec.quantity > 0) {
        runningCost += exec.quantity * exec.price
        runningQty += exec.quantity
        avgEntryPrice = runningCost / runningQty
      } else {
        val sellQty = kotlin.math.abs(exec.quantity)
        runningCost -= sellQty * avgEntryPrice
        runningQty -= sellQty
        if (runningQty == 0) {
          runningCost = 0.0
        }
      }
    }

    return copy(
      position = position.copy(
        currentQuantity = runningQty,
        currentContracts = if (position.instrumentType == InstrumentType.OPTION) runningQty else null,
        averageEntryPrice = avgEntryPrice,
        totalCost = totalCost,
      ),
    )
  }
}

/**
 * Result from broker import operation
 */
data class ImportResult(
  val positionsCreated: Int,
  val newPositions: Int = 0,
  val executionsCreated: Int,
  val rollsDetected: Int,
)
