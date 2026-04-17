package com.skrymer.udgaard.backtesting.service.sizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KellySizerTest {
  private fun ctx(
    portfolioValue: Double = 100_000.0,
    entryPrice: Double = 50.0,
    atr: Double = 2.0,
  ) = SizingContext(portfolioValue, entryPrice, atr, "AAPL", "XLK")

  @Test
  fun `positive-edge trader at quarter Kelly`() {
    // W=0.52, R=1.5 -> f = 0.52 - (0.48/1.5) = 0.52 - 0.32 = 0.20
    // quarter-Kelly: 0.20 * 0.25 = 0.05 -> 5% of equity
    // allocation = 100k * 0.05 = 5k, shares = floor(5k / 50) = 100
    assertEquals(100, KellySizer(0.52, 1.5, 0.25).calculateShares(ctx()))
  }

  @Test
  fun `negative Kelly fraction returns zero`() {
    // W=0.40, R=1.0 -> f = 0.40 - 0.60 = -0.20 (clamped to 0)
    // With no edge, no bet.
    assertEquals(0, KellySizer(0.40, 1.0, 0.5).calculateShares(ctx()))
  }

  @Test
  fun `breakeven returns zero`() {
    // W=0.50, R=1.0 -> f = 0.50 - 0.50 = 0.0
    assertEquals(0, KellySizer(0.50, 1.0, 0.5).calculateShares(ctx()))
  }

  @Test
  fun `half Kelly roughly doubles quarter Kelly size`() {
    val quarter = KellySizer(0.55, 2.0, 0.25).calculateShares(ctx())
    val half = KellySizer(0.55, 2.0, 0.50).calculateShares(ctx())
    // floor rounding can cause ±1 delta between 2*quarter and half
    val delta = (half - quarter * 2)
    assertTrue(delta in 0..1, "half-Kelly ($half) should roughly double quarter-Kelly (${quarter * 2}), delta=$delta")
  }

  @Test
  fun `scale halves fractionMultiplier`() {
    val scaled = KellySizer(0.52, 1.5, 0.5).scale(0.5) as KellySizer
    assertEquals(0.25, scaled.fractionMultiplier, 1e-9)
  }

  @Test
  fun `scale does not change winRate or winLossRatio`() {
    val scaled = KellySizer(0.52, 1.5, 0.5).scale(0.5) as KellySizer
    assertEquals(0.52, scaled.winRate, 1e-9)
    assertEquals(1.5, scaled.winLossRatio, 1e-9)
  }

  @Test
  fun `scale clamps to 0 point 01 floor`() {
    val scaled = KellySizer(0.52, 1.5, 0.1).scale(0.001) as KellySizer
    assertEquals(0.01, scaled.fractionMultiplier, 1e-9)
  }

  @Test
  fun `negative edge survives scaling and still returns zero`() {
    // Even after scaling, a negative-edge sizer should never take a position.
    val scaled = KellySizer(0.40, 1.0, 0.5).scale(0.5)
    assertEquals(0, scaled.calculateShares(ctx()))
  }
}
