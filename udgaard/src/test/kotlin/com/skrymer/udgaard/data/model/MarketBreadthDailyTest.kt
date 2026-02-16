package com.skrymer.udgaard.data.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MarketBreadthDailyTest {
  @Test
  fun `isInUptrend returns true when breadthPercent is above ema10`() {
    val breadth = MarketBreadthDaily(
      quoteDate = LocalDate.of(2024, 1, 15),
      breadthPercent = 60.0,
      ema10 = 55.0,
    )
    assertTrue(breadth.isInUptrend())
  }

  @Test
  fun `isInUptrend returns false when breadthPercent equals ema10`() {
    val breadth = MarketBreadthDaily(
      quoteDate = LocalDate.of(2024, 1, 15),
      breadthPercent = 55.0,
      ema10 = 55.0,
    )
    assertFalse(breadth.isInUptrend())
  }

  @Test
  fun `isInUptrend returns false when breadthPercent is below ema10`() {
    val breadth = MarketBreadthDaily(
      quoteDate = LocalDate.of(2024, 1, 15),
      breadthPercent = 40.0,
      ema10 = 55.0,
    )
    assertFalse(breadth.isInUptrend())
  }
}
