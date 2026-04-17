package com.skrymer.udgaard.backtesting.service.sizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PercentEquitySizerTest {
  private fun ctx(
    portfolioValue: Double = 100_000.0,
    entryPrice: Double = 50.0,
    atr: Double = 2.0,
  ) = SizingContext(portfolioValue, entryPrice, atr, "AAPL", "XLK")

  @Test
  fun `10 percent of 100k at 50 per share equals 200 shares`() {
    // notional = 100k * 0.10 = 10k, shares = floor(10k / 50) = 200
    assertEquals(200, PercentEquitySizer(10.0).calculateShares(ctx()))
  }

  @Test
  fun `zero entry price returns zero`() {
    assertEquals(0, PercentEquitySizer(10.0).calculateShares(ctx(entryPrice = 0.0)))
  }

  @Test
  fun `ignores ATR entirely`() {
    val sizerA = PercentEquitySizer(10.0).calculateShares(ctx(atr = 0.5))
    val sizerB = PercentEquitySizer(10.0).calculateShares(ctx(atr = 20.0))
    // Same share count regardless of ATR
    assertEquals(sizerA, sizerB)
  }

  @Test
  fun `scale halves percent`() {
    val scaled = PercentEquitySizer(10.0).scale(0.5) as PercentEquitySizer
    assertEquals(5.0, scaled.percent, 1e-9)
  }

  @Test
  fun `scale clamps to 0 point 5 floor`() {
    val scaled = PercentEquitySizer(5.0).scale(0.01) as PercentEquitySizer
    assertEquals(0.5, scaled.percent, 1e-9)
  }
}
