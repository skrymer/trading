package com.skrymer.udgaard.backtesting.service.sizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LeverageCapTest {
  @Test
  fun `null leverageRatio leaves shares unchanged`() {
    assertEquals(100, applyLeverageCap(100, 50.0, 100_000.0, 0.0, null))
  }

  @Test
  fun `cap not binding returns raw shares`() {
    // 100 shares × $50 = $5K notional; cap = $10K × 1.0 = $10K. Well below.
    assertEquals(100, applyLeverageCap(100, 50.0, 10_000.0, 0.0, 1.0))
  }

  @Test
  fun `cap binding reduces shares to fit available notional`() {
    // 1000 shares × $50 = $50K desired; cap = $10K × 1.0 = $10K. Can only fit floor(10000/50) = 200.
    assertEquals(200, applyLeverageCap(1000, 50.0, 10_000.0, 0.0, 1.0))
  }

  @Test
  fun `already at cap returns zero`() {
    // Cap = $10K × 1.0 = $10K, already used. No capacity.
    assertEquals(0, applyLeverageCap(100, 50.0, 10_000.0, 10_000.0, 1.0))
  }

  @Test
  fun `over cap returns zero`() {
    assertEquals(0, applyLeverageCap(100, 50.0, 10_000.0, 15_000.0, 1.0))
  }

  @Test
  fun `zero shares input returns zero`() {
    assertEquals(0, applyLeverageCap(0, 50.0, 10_000.0, 0.0, 1.0))
  }

  @Test
  fun `zero entry price with cap returns unchanged shares`() {
    // Degenerate case: if price is 0 we can't divide — pass through without capping.
    assertEquals(100, applyLeverageCap(100, 0.0, 10_000.0, 0.0, 1.0))
  }

  @Test
  fun `leverage ratio over 1 allows more shares`() {
    // 1000 × $50 = $50K, cap = $10K × 5 = $50K. Fits exactly.
    assertEquals(1000, applyLeverageCap(1000, 50.0, 10_000.0, 0.0, 5.0))
  }
}
