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
)

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
