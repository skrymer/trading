package com.skrymer.udgaard.data.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SectorBreadthDailyTest {
  @Test
  fun `isInUptrend returns true when bullPercentage is above ema10`() {
    val breadth = SectorBreadthDaily(
      sectorSymbol = "XLK",
      quoteDate = LocalDate.of(2024, 1, 15),
      stocksInUptrend = 60,
      stocksInDowntrend = 40,
      totalStocks = 100,
      bullPercentage = 60.0,
      ema10 = 55.0,
    )
    assertTrue(breadth.isInUptrend())
  }

  @Test
  fun `isInUptrend returns false when bullPercentage equals ema10`() {
    val breadth = SectorBreadthDaily(
      sectorSymbol = "XLK",
      quoteDate = LocalDate.of(2024, 1, 15),
      stocksInUptrend = 55,
      stocksInDowntrend = 45,
      totalStocks = 100,
      bullPercentage = 55.0,
      ema10 = 55.0,
    )
    assertFalse(breadth.isInUptrend())
  }

  @Test
  fun `isInUptrend returns false when bullPercentage is below ema10`() {
    val breadth = SectorBreadthDaily(
      sectorSymbol = "XLK",
      quoteDate = LocalDate.of(2024, 1, 15),
      stocksInUptrend = 40,
      stocksInDowntrend = 60,
      totalStocks = 100,
      bullPercentage = 40.0,
      ema10 = 55.0,
    )
    assertFalse(breadth.isInUptrend())
  }
}
