package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.DrawdownScaling
import com.skrymer.udgaard.backtesting.model.DrawdownThreshold
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.floor

class PositionSizingServiceTest {
  private val service = PositionSizingService()
  private val defaultConfig = PositionSizingConfig(startingCapital = 100_000.0, riskPercentage = 1.5, nAtr = 2.0)

  @Test
  fun `single winning trade should calculate shares and dollar profit correctly`() {
    // ATR = 2.0, risk = 1.5% of 100k = 1500, shares = floor(1500 / (2 * 2.0)) = 375
    // Profit per share = 5.0, dollar profit = 375 * 5.0 = 1875
    val trade = createTrade(profit = 5.0, entryPrice = 50.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 1))

    val result = service.applyPositionSizing(listOf(trade), defaultConfig)

    assertEquals(1, result.trades.size)
    assertEquals(375, result.trades[0].shares)
    assertEquals(1875.0, result.trades[0].dollarProfit, 0.01)
    assertEquals(101_875.0, result.finalCapital, 0.01)
    assertEquals(1.875, result.totalReturnPct, 0.01)
  }

  @Test
  fun `single losing trade should reduce portfolio correctly`() {
    // ATR = 2.0, shares = 375, profit = -3.0, dollar loss = 375 * -3.0 = -1125
    val trade = createTrade(profit = -3.0, entryPrice = 50.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 1))

    val result = service.applyPositionSizing(listOf(trade), defaultConfig)

    assertEquals(375, result.trades[0].shares)
    assertEquals(-1125.0, result.trades[0].dollarProfit, 0.01)
    assertEquals(98_875.0, result.finalCapital, 0.01)
    assertEquals(-1.125, result.totalReturnPct, 0.01)
  }

  @Test
  fun `multiple sequential trades should compound portfolio value`() {
    // Trade 1: portfolio=100k, ATR=2, shares=375, profit=+5 → +1875 → portfolio=101875
    // Trade 2: portfolio=101875, ATR=2, shares=floor(101875*0.015/4)=382, profit=+3 → +1146 → portfolio=103021
    val trade1 = createTrade(profit = 5.0, entryPrice = 50.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 1))
    val trade2 = createTrade(profit = 3.0, entryPrice = 60.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 10))

    val result = service.applyPositionSizing(listOf(trade1, trade2), defaultConfig)

    assertEquals(2, result.trades.size)
    assertEquals(375, result.trades[0].shares)
    assertEquals(1875.0, result.trades[0].dollarProfit, 0.01)

    // Second trade sized from updated portfolio value
    val expectedShares2 = floor(101_875.0 * 0.015 / 4.0).toInt()
    assertEquals(expectedShares2, result.trades[1].shares)
    val expectedProfit2 = expectedShares2 * 3.0
    assertEquals(expectedProfit2, result.trades[1].dollarProfit, 0.01)
    assertEquals(101_875.0 + expectedProfit2, result.finalCapital, 0.01)
  }

  @Test
  fun `concurrent trades should size independently from portfolio at entry`() {
    // Two trades enter on same day, exit at different times
    // Both should be sized from the same portfolio value (100k)
    val trade1 =
      createTrade(
        profit = 5.0,
        entryPrice = 50.0,
        atr = 2.0,
        entryDate = LocalDate.of(2024, 1, 1),
        exitDate = LocalDate.of(2024, 1, 10),
      )
    val trade2 =
      createTrade(
        profit = 3.0,
        entryPrice = 60.0,
        atr = 2.0,
        entryDate = LocalDate.of(2024, 1, 1),
        exitDate = LocalDate.of(2024, 1, 15),
      )

    val result = service.applyPositionSizing(listOf(trade1, trade2), defaultConfig)

    assertEquals(2, result.trades.size)
    // Both sized from 100k since entries are on the same day (before exits)
    assertEquals(375, result.trades[0].shares)
    assertEquals(375, result.trades[1].shares)
  }

  @Test
  fun `ATR equals zero should result in skipped trade`() {
    val trade = createTrade(profit = 5.0, entryPrice = 50.0, atr = 0.0, entryDate = LocalDate.of(2024, 1, 1))

    val result = service.applyPositionSizing(listOf(trade), defaultConfig)

    // Zero ATR means zero shares, so the trade is skipped entirely
    assertEquals(0, result.trades.size)
    assertEquals(100_000.0, result.finalCapital, 0.01)
  }

  @Test
  fun `depleted portfolio should result in zero shares`() {
    // Create a large losing trade first, then try to enter another
    // ATR=0.5, shares=floor(100000*0.015/1)=1500, profit=-100 → loss=150000 → portfolio=-50000
    val bigLoser =
      createTrade(
        profit = -100.0,
        entryPrice = 100.0,
        atr = 0.5,
        entryDate = LocalDate.of(2024, 1, 1),
        exitDate = LocalDate.of(2024, 1, 5),
      )
    val nextTrade =
      createTrade(
        profit = 5.0,
        entryPrice = 50.0,
        atr = 2.0,
        entryDate = LocalDate.of(2024, 1, 10),
        exitDate = LocalDate.of(2024, 1, 15),
      )

    val result = service.applyPositionSizing(listOf(bigLoser, nextTrade), defaultConfig)

    // Second trade should be skipped since portfolio is negative (zero shares)
    assertEquals(1, result.trades.size)
  }

  @Test
  fun `shares less than 1 should result in skipped trade`() {
    // Very high ATR relative to portfolio → shares < 1
    // ATR=100000, shares=floor(100000*0.015/200000)=0
    val trade = createTrade(profit = 5.0, entryPrice = 50.0, atr = 100_000.0, entryDate = LocalDate.of(2024, 1, 1))

    val result = service.applyPositionSizing(listOf(trade), defaultConfig)

    // Zero shares means the trade is skipped entirely
    assertEquals(0, result.trades.size)
    assertEquals(100_000.0, result.finalCapital, 0.01)
  }

  @Test
  fun `equity curve should have daily M2M points for all trading dates`() {
    val trade1 = createTrade(profit = 5.0, entryPrice = 50.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 1))
    val trade2 = createTrade(profit = 3.0, entryPrice = 60.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 10))

    val result = service.applyPositionSizing(listOf(trade1, trade2), defaultConfig)

    // Daily M2M: one point per trading date (entry + exit per trade = 4 dates)
    assertEquals(4, result.equityCurve.size)
    // Entry day for trade1: cash + M2M of open position at entry price
    assertEquals(100_000.0, result.equityCurve[0].portfolioValue, 0.01)
    // Exit day for trade1: cash-only after exit
    assertEquals(result.trades[0].dollarProfit + 100_000.0, result.equityCurve[1].portfolioValue, 0.01)
    // Last point should equal final capital
    assertEquals(result.finalCapital, result.equityCurve.last().portfolioValue, 0.01)
  }

  @Test
  fun `max drawdown should track peak to trough correctly`() {
    // Win, then lose → drawdown from peak
    val win =
      createTrade(
        profit = 10.0,
        entryPrice = 50.0,
        atr = 2.0,
        entryDate = LocalDate.of(2024, 1, 1),
        exitDate = LocalDate.of(2024, 1, 5),
      )
    val loss =
      createTrade(
        profit = -8.0,
        entryPrice = 50.0,
        atr = 2.0,
        entryDate = LocalDate.of(2024, 1, 10),
        exitDate = LocalDate.of(2024, 1, 15),
      )

    val result = service.applyPositionSizing(listOf(win, loss), defaultConfig)

    // After win: portfolio ≈ 103750 (375 shares × $10)
    // After loss: sized from 103750, shares = floor(103750*0.015/4)=389, loss = 389 * -8 = -3112
    // Drawdown = 3112 from peak of 103750 → ~3.0%
    assertTrue(result.maxDrawdownPct > 0.0)
    assertTrue(result.maxDrawdownDollars > 0.0)
    assertEquals(result.maxDrawdownDollars, result.peakCapital - (result.peakCapital - result.maxDrawdownDollars), 0.01)
  }

  @Test
  fun `empty trades should return empty result with starting capital`() {
    val result = service.applyPositionSizing(emptyList(), defaultConfig)

    assertEquals(100_000.0, result.startingCapital, 0.01)
    assertEquals(100_000.0, result.finalCapital, 0.01)
    assertEquals(0.0, result.totalReturnPct, 0.01)
    assertEquals(0.0, result.maxDrawdownPct, 0.01)
    assertTrue(result.trades.isEmpty())
    assertTrue(result.equityCurve.isEmpty())
  }

  @Test
  fun `portfolio return pct should be relative to portfolio at entry`() {
    val trade = createTrade(profit = 5.0, entryPrice = 50.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 1))

    val result = service.applyPositionSizing(listOf(trade), defaultConfig)

    // dollarProfit = 375 * 5 = 1875, portfolioAtEntry = 100000
    // returnPct = (1875 / 100000) * 100 = 1.875%
    assertEquals(1.875, result.trades[0].portfolioReturnPct, 0.01)
  }

  @Test
  fun `leverage ratio should reduce shares when new position exceeds cap`() {
    // leverageRatio=1.0 means total notional <= portfolioValue (100k)
    // Trade 1: entry $50, ATR=2, shares=375 → notional=375*50=18750 (under 100k)
    // Trade 2: entry $60, ATR=2, shares=375 → cumulative would be 18750+22500=41250 (still under)
    // Trade 3: entry $200, ATR=2, shares=375 → cumulative would be 41250+75000=116250 (over 100k!)
    //   available = 100000-41250=58750, cappedShares = floor(58750/200)=293
    val config = defaultConfig.copy(leverageRatio = 1.0)

    val trade1 = createTrade(
      profit = 0.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 1),
      exitDate = LocalDate.of(2024, 1, 20),
    )
    val trade2 = createTrade(
      profit = 0.0,
      entryPrice = 60.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 2),
      exitDate = LocalDate.of(2024, 1, 20),
    )
    val trade3 = createTrade(
      profit = 0.0,
      entryPrice = 200.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 3),
      exitDate = LocalDate.of(2024, 1, 20),
    )

    val result = service.applyPositionSizing(listOf(trade1, trade2, trade3), config)

    assertEquals(375, result.trades[0].shares)
    assertEquals(375, result.trades[1].shares)
    // Third trade should be capped: available = 100000 - (375*50 + 375*60) = 100000 - 41250 = 58750
    // cappedShares = floor(58750 / 200) = 293
    assertEquals(293, result.trades[2].shares)
  }

  @Test
  fun `leverage ratio fully exhausted should result in zero shares`() {
    // leverageRatio=1.0 with two large positions that exhaust the cap
    val config = defaultConfig.copy(leverageRatio = 1.0)

    // Trade 1: entry $200, shares=375 → notional = 75000
    val trade1 = createTrade(
      profit = 0.0,
      entryPrice = 200.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 1),
      exitDate = LocalDate.of(2024, 1, 20),
    )
    // Trade 2: entry $100, shares=375 → cumulative would be 75000+37500=112500 > 100000
    // available = 100000-75000=25000, cappedShares = floor(25000/100) = 250
    val trade2 = createTrade(
      profit = 0.0,
      entryPrice = 100.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 2),
      exitDate = LocalDate.of(2024, 1, 20),
    )
    // Trade 3: entry $50 → cumulative already at 75000+25000=100000, available=0 → 0 shares
    val trade3 = createTrade(
      profit = 0.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 3),
      exitDate = LocalDate.of(2024, 1, 20),
    )

    val result = service.applyPositionSizing(listOf(trade1, trade2, trade3), config)

    assertEquals(375, result.trades[0].shares)
    assertEquals(250, result.trades[1].shares)
    // Third trade is skipped entirely since leverage cap is fully exhausted (zero shares)
    assertEquals(2, result.trades.size)
  }

  @Test
  fun `leverage ratio should not reduce shares for sequential non-overlapping trades`() {
    // leverageRatio=1.0, but trades don't overlap → notional released before next entry
    val config = defaultConfig.copy(leverageRatio = 1.0)

    val trade1 = createTrade(
      profit = 5.0,
      entryPrice = 200.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 1),
      exitDate = LocalDate.of(2024, 1, 5),
    )
    val trade2 = createTrade(
      profit = 3.0,
      entryPrice = 200.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 10),
      exitDate = LocalDate.of(2024, 1, 15),
    )

    val result = service.applyPositionSizing(listOf(trade1, trade2), config)

    // Both should get full ATR-based shares since they don't overlap
    assertEquals(375, result.trades[0].shares)
    // Second trade sized from updated portfolio (101875), shares = floor(101875*0.015/4) = 382
    val expectedShares2 = floor(101_875.0 * 0.015 / 4.0).toInt()
    assertEquals(expectedShares2, result.trades[1].shares)
  }

  @Test
  fun `null leverage ratio should not cap shares`() {
    // Same overlapping trades as leverage test but no leverageRatio → no capping
    val trade1 = createTrade(
      profit = 0.0,
      entryPrice = 200.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 1),
      exitDate = LocalDate.of(2024, 1, 20),
    )
    val trade2 = createTrade(
      profit = 0.0,
      entryPrice = 200.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 2),
      exitDate = LocalDate.of(2024, 1, 20),
    )
    val trade3 = createTrade(
      profit = 0.0,
      entryPrice = 200.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 3),
      exitDate = LocalDate.of(2024, 1, 20),
    )

    val result = service.applyPositionSizing(listOf(trade1, trade2, trade3), defaultConfig)

    // All should get full ATR-based shares (375 each) — no capping
    assertEquals(375, result.trades[0].shares)
    assertEquals(375, result.trades[1].shares)
    assertEquals(375, result.trades[2].shares)
  }

  @Test
  fun `calculateShares companion should match service calculation`() {
    val shares = PositionSizingService.calculateShares(100_000.0, 2.0, defaultConfig)

    assertEquals(375, shares)
  }

  @Test
  fun `calculateShares with zero ATR should return zero`() {
    assertEquals(0, PositionSizingService.calculateShares(100_000.0, 0.0, defaultConfig))
  }

  @Test
  fun `calculateShares with negative portfolio should return zero`() {
    assertEquals(0, PositionSizingService.calculateShares(-1000.0, 2.0, defaultConfig))
  }

  @Test
  fun `drawdown scaling should reduce shares when in drawdown`() {
    // Trade 1: big loss → puts portfolio into drawdown
    // Trade 2: should be sized with reduced risk
    val scaling = DrawdownScaling(
      thresholds = listOf(
        DrawdownThreshold(drawdownPercent = 5.0, riskMultiplier = 0.67),
        DrawdownThreshold(drawdownPercent = 10.0, riskMultiplier = 0.33),
      ),
    )
    val config = defaultConfig.copy(drawdownScaling = scaling)

    // Trade 1: ATR=2, shares=375, profit=-20 → loss=375*20=7500 → portfolio=92500
    // Drawdown = 7500/100000 = 7.5% (above 5% threshold)
    val trade1 = createTrade(
      profit = -20.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 1),
      exitDate = LocalDate.of(2024, 1, 5),
    )
    // Trade 2: portfolio=92500, drawdown=7.5% → risk scaled by 0.67
    // Effective risk = 1.5% * 0.67 = 1.005%, shares = floor(92500 * 0.01005 / 4) = 232
    val trade2 = createTrade(
      profit = 5.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 10),
      exitDate = LocalDate.of(2024, 1, 15),
    )

    val result = service.applyPositionSizing(listOf(trade1, trade2), config)

    assertEquals(375, result.trades[0].shares) // First trade: no drawdown yet
    val expectedShares2 = floor(92_500.0 * (1.5 * 0.67 / 100.0) / 4.0).toInt()
    assertEquals(expectedShares2, result.trades[1].shares) // Scaled down
  }

  @Test
  fun `drawdown scaling should use deepest matching threshold`() {
    val scaling = DrawdownScaling(
      thresholds = listOf(
        DrawdownThreshold(drawdownPercent = 5.0, riskMultiplier = 0.67),
        DrawdownThreshold(drawdownPercent = 10.0, riskMultiplier = 0.33),
      ),
    )
    val config = defaultConfig.copy(drawdownScaling = scaling)

    // Trade 1: ATR=0.5, shares=1500, profit=-10 → loss=15000 → portfolio=85000
    // Drawdown = 15000/100000 = 15% (above 10% threshold → multiplier 0.33)
    val trade1 = createTrade(
      profit = -10.0,
      entryPrice = 50.0,
      atr = 0.5,
      entryDate = LocalDate.of(2024, 1, 1),
      exitDate = LocalDate.of(2024, 1, 5),
    )
    val trade2 = createTrade(
      profit = 5.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 10),
      exitDate = LocalDate.of(2024, 1, 15),
    )

    val result = service.applyPositionSizing(listOf(trade1, trade2), config)

    val expectedShares2 = floor(85_000.0 * (1.5 * 0.33 / 100.0) / 4.0).toInt()
    assertEquals(expectedShares2, result.trades[1].shares)
  }

  @Test
  fun `drawdown scaling should not affect trades when no drawdown`() {
    val scaling = DrawdownScaling(
      thresholds = listOf(
        DrawdownThreshold(drawdownPercent = 5.0, riskMultiplier = 0.67),
      ),
    )
    val config = defaultConfig.copy(drawdownScaling = scaling)

    val trade = createTrade(profit = 5.0, entryPrice = 50.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 1))

    val result = service.applyPositionSizing(listOf(trade), config)

    // No drawdown → full risk → same as default
    assertEquals(375, result.trades[0].shares)
  }

  @Test
  fun `null drawdown scaling should not affect sizing`() {
    // Config without drawdown scaling should behave identically to default
    val trade = createTrade(profit = 5.0, entryPrice = 50.0, atr = 2.0, entryDate = LocalDate.of(2024, 1, 1))

    val resultWithout = service.applyPositionSizing(listOf(trade), defaultConfig)
    val resultWith = service.applyPositionSizing(listOf(trade), defaultConfig.copy(drawdownScaling = null))

    assertEquals(resultWithout.trades[0].shares, resultWith.trades[0].shares)
  }

  @Test
  fun `drawdown scaling should recover to full risk after equity recovers`() {
    val scaling = DrawdownScaling(
      thresholds = listOf(
        DrawdownThreshold(drawdownPercent = 5.0, riskMultiplier = 0.67),
      ),
    )
    val config = defaultConfig.copy(drawdownScaling = scaling)

    // Trade 1: loss puts portfolio into drawdown
    // ATR=2, shares=375, profit=-20 → loss=7500 → portfolio=92500 (7.5% DD)
    val trade1 = createTrade(
      profit = -20.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 1),
      exitDate = LocalDate.of(2024, 1, 5),
    )
    // Trade 2: sized with reduced risk (in drawdown)
    val trade2 = createTrade(
      profit = 40.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 10),
      exitDate = LocalDate.of(2024, 1, 15),
    )
    // Trade 3: equity recovered above peak → full risk again
    val trade3 = createTrade(
      profit = 5.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 20),
      exitDate = LocalDate.of(2024, 1, 25),
    )

    val result = service.applyPositionSizing(listOf(trade1, trade2, trade3), config)

    assertEquals(375, result.trades[0].shares) // Full risk (no DD)
    val scaledShares = floor(92_500.0 * (1.5 * 0.67 / 100.0) / 4.0).toInt()
    assertEquals(scaledShares, result.trades[1].shares) // Scaled down (in DD)
    // After trade 2: cash = 92500 + (scaledShares * 40) → above peak → full risk
    val portfolioAfterTrade2 = 92_500.0 + scaledShares * 40.0
    assertTrue(portfolioAfterTrade2 > 100_000.0, "Portfolio should have recovered above peak")
    val fullShares = floor(portfolioAfterTrade2 * (1.5 / 100.0) / 4.0).toInt()
    assertEquals(fullShares, result.trades[2].shares) // Back to full risk
  }

  @Test
  fun `drawdown scaling with overlapping trades should use portfolio value not cash`() {
    val scaling = DrawdownScaling(
      thresholds = listOf(
        DrawdownThreshold(drawdownPercent = 5.0, riskMultiplier = 0.50),
      ),
    )
    val config = defaultConfig.copy(drawdownScaling = scaling)

    // Trade 1: open losing position (overlaps with trade 2 entry)
    // Enters at $50, goes to $45 by day 10 → unrealized loss = 375 * -5 = -1875
    // Portfolio value on day 5 (last M2M before trade 2): cash + unrealized = 100000 + (-1875) = 98125
    // DD = (100000 - 98125) / 100000 = 1.875% → below 5% threshold → full risk
    val trade1 = createTrade(
      profit = -5.0,
      entryPrice = 50.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 1),
      exitDate = LocalDate.of(2024, 1, 10),
    )
    // Trade 2: enters on day 5 while trade 1 is still open
    val trade2 = createTrade(
      profit = 3.0,
      entryPrice = 60.0,
      atr = 2.0,
      entryDate = LocalDate.of(2024, 1, 5),
      exitDate = LocalDate.of(2024, 1, 15),
    )

    val result = service.applyPositionSizing(listOf(trade1, trade2), config)

    // Trade 1: full risk (no drawdown at entry)
    assertEquals(375, result.trades[0].shares)
    // Trade 2: drawdown is ~1.875% (below 5% threshold) → should get full risk
    assertEquals(375, result.trades[1].shares)
  }

  @Test
  fun `drawdown threshold should reject negative drawdown percent`() {
    org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
      DrawdownThreshold(drawdownPercent = -1.0, riskMultiplier = 0.5)
    }
  }

  @Test
  fun `drawdown threshold should reject risk multiplier above 1`() {
    org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
      DrawdownThreshold(drawdownPercent = 5.0, riskMultiplier = 1.5)
    }
  }

  private fun createTrade(
    profit: Double,
    entryPrice: Double,
    atr: Double,
    entryDate: LocalDate,
    exitDate: LocalDate = entryDate.plusDays(5),
  ): Trade {
    val entryQuote =
      StockQuote(
        symbol = "TEST",
        date = entryDate,
        closePrice = entryPrice,
        atr = atr,
      )
    val exitQuote =
      StockQuote(
        symbol = "TEST",
        date = exitDate,
        closePrice = entryPrice + profit,
      )

    return Trade(
      stockSymbol = "TEST",
      entryQuote = entryQuote,
      quotes = listOf(exitQuote),
      exitReason = "Test exit",
      profit = profit,
      startDate = entryDate,
      sector = "Technology",
    )
  }
}
