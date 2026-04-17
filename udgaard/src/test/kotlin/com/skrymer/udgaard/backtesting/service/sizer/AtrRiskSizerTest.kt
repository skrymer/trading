package com.skrymer.udgaard.backtesting.service.sizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AtrRiskSizerTest {
  private fun ctx(
    portfolioValue: Double = 100_000.0,
    atr: Double = 2.0,
    entryPrice: Double = 50.0,
  ) = SizingContext(portfolioValue, entryPrice, atr, "AAPL", "XLK")

  @Test
  fun `baseline 1 point 5 percent risk and 2 ATR`() {
    // risk = 100k * 0.015 = 1500, shares = floor(1500 / (2 * 2.0)) = 375
    assertEquals(375, AtrRiskSizer(1.5, 2.0).calculateShares(ctx()))
  }

  @Test
  fun `zero ATR returns zero shares`() {
    assertEquals(0, AtrRiskSizer(1.5, 2.0).calculateShares(ctx(atr = 0.0)))
  }

  @Test
  fun `negative portfolio value returns zero shares`() {
    assertEquals(0, AtrRiskSizer(1.5, 2.0).calculateShares(ctx(portfolioValue = -1000.0)))
  }

  @Test
  fun `scale halves riskPercentage`() {
    val scaled = AtrRiskSizer(1.5, 2.0).scale(0.5) as AtrRiskSizer
    assertEquals(0.75, scaled.riskPercentage, 1e-9)
    assertEquals(2.0, scaled.nAtr, 1e-9)
  }

  @Test
  fun `scale clamps to 0 point 1 percent floor`() {
    // 1.5 * 0.01 = 0.015, below the 0.1 floor -> should clamp to 0.1
    val scaled = AtrRiskSizer(1.5, 2.0).scale(0.01) as AtrRiskSizer
    assertEquals(0.1, scaled.riskPercentage, 1e-9)
  }

  @Test
  fun `share count rounds down`() {
    // risk = 100k * 0.015 = 1500, shares = floor(1500 / (2 * 3.0)) = 250 (exactly)
    // shares = floor(1500 / (2 * 3.1)) = 241.93 -> 241
    assertEquals(241, AtrRiskSizer(1.5, 2.0).calculateShares(ctx(atr = 3.1)))
  }
}
