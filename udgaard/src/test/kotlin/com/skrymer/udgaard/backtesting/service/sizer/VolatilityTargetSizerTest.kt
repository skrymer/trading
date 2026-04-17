package com.skrymer.udgaard.backtesting.service.sizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VolatilityTargetSizerTest {
  private fun ctx(
    portfolioValue: Double = 100_000.0,
    entryPrice: Double = 50.0,
    atr: Double = 2.0,
  ) = SizingContext(portfolioValue, entryPrice, atr, "AAPL", "XLK")

  @Test
  fun `target 0 point 5 percent vol at 1x ATR`() {
    // dollarVolPerShare = 1.0 * 2.0 = 2.0
    // targetDollarVol = 100k * 0.005 = 500
    // shares = floor(500 / 2.0) = 250
    assertEquals(250, VolatilityTargetSizer(0.5, 1.0).calculateShares(ctx()))
  }

  @Test
  fun `higher ATR means fewer shares (equal-vol-contribution)`() {
    val lowAtr = VolatilityTargetSizer(0.5, 1.0).calculateShares(ctx(atr = 1.0))
    val highAtr = VolatilityTargetSizer(0.5, 1.0).calculateShares(ctx(atr = 5.0))
    // 5x ATR -> 1/5 shares
    assertEquals(lowAtr / 5, highAtr)
  }

  @Test
  fun `zero ATR returns zero`() {
    assertEquals(0, VolatilityTargetSizer(0.5, 1.0).calculateShares(ctx(atr = 0.0)))
  }

  @Test
  fun `scale halves targetVolPct`() {
    val scaled = VolatilityTargetSizer(0.5, 1.0).scale(0.5) as VolatilityTargetSizer
    assertEquals(0.25, scaled.targetVolPct, 1e-9)
  }

  @Test
  fun `scale clamps to 0 point 05 floor`() {
    val scaled = VolatilityTargetSizer(0.5, 1.0).scale(0.001) as VolatilityTargetSizer
    assertEquals(0.05, scaled.targetVolPct, 1e-9)
  }
}
