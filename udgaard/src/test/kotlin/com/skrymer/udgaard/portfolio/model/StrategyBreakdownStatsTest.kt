package com.skrymer.udgaard.portfolio.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StrategyBreakdownStatsTest {
  @Test
  fun `fromPositions computes per-strategy edge for a mix of winning and losing positions`() {
    // Given: 2 winners (+20%, +10%) and 2 losers (-5%, -5%) for one strategy
    val positions = listOf(
      closedPosition(realizedPnl = 600.0, totalCost = 3000.0),
      closedPosition(realizedPnl = 400.0, totalCost = 4000.0),
      closedPosition(realizedPnl = -150.0, totalCost = 3000.0),
      closedPosition(realizedPnl = -100.0, totalCost = 2000.0),
    )

    // When
    val stats = StrategyBreakdownStats.fromPositions("TestEntryStrategy", positions)

    // Then: edge = avgWinPct·winRate − avgLossPct·lossRate = 15.0·0.5 − 5.0·0.5 = 5.0
    assertEquals("TestEntryStrategy", stats.strategy)
    assertEquals(4, stats.trades)
    assertEquals(50.0, stats.winRate)
    assertEquals(5.0, stats.edge, 1e-9)
    assertEquals(750.0, stats.totalPnl, 1e-9)
  }

  @Test
  fun `fromPositions reports a null profit factor when a strategy has no losing positions`() {
    // Given: two winners, no losers (+20%, +10%)
    val positions = listOf(
      closedPosition(realizedPnl = 600.0, totalCost = 3000.0),
      closedPosition(realizedPnl = 400.0, totalCost = 4000.0),
    )

    // When
    val stats = StrategyBreakdownStats.fromPositions("TestEntryStrategy", positions)

    // Then: profit factor is undefined with no losses; edge is the win side alone (15.0 · 1.0)
    assertEquals(100.0, stats.winRate)
    assertEquals(0, stats.losses)
    assertNull(stats.profitFactor, "no losses → profit factor undefined, not zero or infinity")
    assertEquals(15.0, stats.edge, 1e-9)
    assertEquals(0.0, stats.avgLossDollars, 1e-9)
  }

  @Test
  fun `fromPositions reports a negative edge and zero profit factor when every position lost`() {
    // Given: two losers, no winners (-5%, -10%)
    val positions = listOf(
      closedPosition(realizedPnl = -150.0, totalCost = 3000.0),
      closedPosition(realizedPnl = -300.0, totalCost = 3000.0),
    )

    // When
    val stats = StrategyBreakdownStats.fromPositions("TestEntryStrategy", positions)

    // Then: edge = 0·0 − 7.5·1.0 = −7.5; profit factor is gross profit (0) over gross loss → 0.0
    assertEquals(0.0, stats.winRate)
    assertEquals(-7.5, stats.edge, 1e-9)
    assertEquals(0.0, stats.profitFactor!!, 1e-9)
    assertEquals(0.0, stats.avgWinDollars, 1e-9)
    assertEquals(225.0, stats.avgLossDollars, 1e-9)
  }

  @Test
  fun `fromPositions excludes positions with no cost basis from the percentage math only`() {
    // Given: two winners — one with a real cost basis (+10%), one with zero cost basis whose
    // percentage return is meaningless
    val positions = listOf(
      closedPosition(realizedPnl = 400.0, totalCost = 4000.0),
      closedPosition(realizedPnl = 1000.0, totalCost = 0.0),
    )

    // When
    val stats = StrategyBreakdownStats.fromPositions("TestEntryStrategy", positions)

    // Then: the zero-cost position is excluded from avgWinPct/edge, but still counts toward
    // trade count, dollar averages, and total P&L
    assertEquals(2, stats.trades)
    assertEquals(10.0, stats.avgWinPct, 1e-9)
    assertEquals(10.0, stats.edge, 1e-9)
    assertEquals(700.0, stats.avgWinDollars, 1e-9)
    assertEquals(1400.0, stats.totalPnl, 1e-9)
  }

  @Test
  fun `fromPositions treats zero or null realized PnL positions as neither win nor loss`() {
    // Given: 1 winner (+20%), 1 loser (−10%), 1 scratch trade ($0), 1 unrealised (null pnl).
    // The non-wins/non-losses must not inflate `lossRate` and drag `edge` down — they count
    // in `trades` and `totalPnl` only.
    val positions = listOf(
      closedPosition(realizedPnl = 300.0, totalCost = 1500.0),
      closedPosition(realizedPnl = -150.0, totalCost = 1500.0),
      closedPosition(realizedPnl = 0.0, totalCost = 1500.0),
      closedPosition(realizedPnl = null, totalCost = 1500.0),
    )

    // When
    val stats = StrategyBreakdownStats.fromPositions("TestEntryStrategy", positions)

    // Then: trades counts all 4, wins/losses count only the real ±, and edge uses the true
    // lossRate (1/4), not the leftover-of-winRate (3/4). edge = 20·0.25 − 10·0.25 = 2.5.
    assertEquals(4, stats.trades)
    assertEquals(1, stats.wins)
    assertEquals(1, stats.losses)
    assertEquals(25.0, stats.winRate, 1e-9)
    assertEquals(2.5, stats.edge, 1e-9)
    assertEquals(150.0, stats.totalPnl, 1e-9)
  }

  private fun closedPosition(realizedPnl: Double?, totalCost: Double): Position =
    Position(
      id = 1L,
      portfolioId = 1L,
      symbol = "AAPL",
      underlyingSymbol = null,
      instrumentType = InstrumentType.STOCK,
      optionType = null,
      strikePrice = null,
      expirationDate = null,
      multiplier = 1,
      currentQuantity = 0,
      currentContracts = null,
      averageEntryPrice = 0.0,
      totalCost = totalCost,
      status = PositionStatus.CLOSED,
      openedDate = LocalDate.of(2026, 1, 5),
      closedDate = LocalDate.of(2026, 1, 20),
      realizedPnl = realizedPnl,
      rolledToPositionId = null,
      parentPositionId = null,
      entryStrategy = "TestEntryStrategy",
      exitStrategy = null,
      notes = null,
    )
}
