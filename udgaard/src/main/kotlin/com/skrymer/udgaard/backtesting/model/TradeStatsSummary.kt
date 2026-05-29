package com.skrymer.udgaard.backtesting.model

import kotlin.math.abs

/**
 * A month-agnostic summary of a set of closed trades. Stores additive raw fields so callers
 * can re-aggregate summaries across an arbitrary union (e.g. a contiguous range of months)
 * and recompute the canonical CONTEXT.md trade-set metrics — Edge, Win rate, Profit factor —
 * without averaging non-linear metrics across subsets. Per ADR-0006.
 */
data class TradeStatsSummary(
  val trades: Int,
  val winners: Int,
  // Sum of profitPercentage over winners / losers. sumLossPercent is <= 0.
  val sumWinPercent: Double,
  val sumLossPercent: Double,
  // Sum of profit (dollars) over winners / losers. grossLossProfit is <= 0. Dollar-domain so
  // Profit factor matches BacktestReport.profitFactor; on an un-sized run profit is per-share.
  val grossWinProfit: Double,
  val grossLossProfit: Double,
) {
  val losers: Int get() = trades - winners

  val winRate: Double get() = if (trades == 0) 0.0 else winners.toDouble() / trades

  private val avgWinPercent: Double get() = if (winners == 0) 0.0 else sumWinPercent / winners

  private val avgLossPercent: Double get() = if (losers == 0) 0.0 else abs(sumLossPercent / losers)

  /** (winRate * avgWin%) - (lossRate * |avgLoss%|) — same formula as BacktestReport.edge. */
  val edge: Double get() = (winRate * avgWinPercent) - ((1.0 - winRate) * avgLossPercent)

  /** Gross profit / |gross loss| in dollars; null when there are no losers (per CONTEXT.md). */
  val profitFactor: Double?
    get() {
      if (losers == 0) return null
      val grossLoss = abs(grossLossProfit)
      return if (grossLoss == 0.0) 0.0 else grossWinProfit / grossLoss
    }

  companion object {
    fun fromTrades(trades: List<Trade>): TradeStatsSummary {
      val (winners, losers) = trades.partition { it.profit > 0 }
      return TradeStatsSummary(
        trades = trades.size,
        winners = winners.size,
        sumWinPercent = winners.sumOf { it.profitPercentage },
        sumLossPercent = losers.sumOf { it.profitPercentage },
        grossWinProfit = winners.sumOf { it.profit },
        grossLossProfit = losers.sumOf { it.profit },
      )
    }
  }
}
