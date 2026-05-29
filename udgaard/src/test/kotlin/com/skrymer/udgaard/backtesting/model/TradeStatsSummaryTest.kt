package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TradeStatsSummaryTest {
  @Test
  fun `fromTrades computes edge as the per-trade expected percentage return`() {
    // Given two winners (+10% each) and one loser (-5%) across varied entry prices
    val trades = listOf(
      trade(entryPrice = 100.0, profit = 10.0),
      trade(entryPrice = 50.0, profit = 5.0),
      trade(entryPrice = 100.0, profit = -5.0),
    )

    // When the trade-set is summarised
    val summary = TradeStatsSummary.fromTrades(trades)

    // Then edge = (winRate * avgWin%) - (lossRate * |avgLoss%|) = (2/3 * 10) - (1/3 * 5)
    assertEquals(3, summary.trades)
    assertEquals(2.0 / 3.0, summary.winRate, 1e-9)
    assertEquals(5.0, summary.edge, 1e-9)
  }

  @Test
  fun `fromTrades computes profit factor from dollar profit, not percentage return`() {
    // Given a +$20 winner on a cheap stock and a -$20 loser on an expensive stock — equal
    // dollars but unequal percentages, so a dollar profit factor (1.0) differs from a
    // percentage-based one (20% / 10% = 2.0)
    val trades = listOf(
      trade(entryPrice = 100.0, profit = 20.0),
      trade(entryPrice = 200.0, profit = -20.0),
    )

    // When the trade-set is summarised
    val summary = TradeStatsSummary.fromTrades(trades)

    // Then profit factor uses gross dollars: 20 / |−20| = 1.0
    assertEquals(1.0, summary.profitFactor)
  }

  @Test
  fun `fromTrades reports null profit factor when there are no losing trades`() {
    // Given only winning trades
    val trades = listOf(
      trade(entryPrice = 100.0, profit = 10.0),
      trade(entryPrice = 100.0, profit = 5.0),
    )

    // When the trade-set is summarised
    val summary = TradeStatsSummary.fromTrades(trades)

    // Then profit factor is null (undefined with no gross loss), never zero or infinity
    assertEquals(null, summary.profitFactor)
  }

  private fun trade(entryPrice: Double, profit: Double): Trade = Trade(
    stockSymbol = "TEST",
    entryQuote = StockQuote(date = LocalDate.of(2020, 1, 1), closePrice = entryPrice),
    quotes = listOf(StockQuote(date = LocalDate.of(2020, 1, 6), closePrice = entryPrice + profit)),
    exitReason = "Test exit",
    profit = profit,
    startDate = LocalDate.of(2020, 1, 1),
    sector = "Technology",
  )
}
