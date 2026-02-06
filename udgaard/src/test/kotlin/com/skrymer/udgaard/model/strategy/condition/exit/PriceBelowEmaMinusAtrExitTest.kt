package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PriceBelowEmaMinusAtrExitTest {
  private val stock = StockDomain()

  @Test
  fun `should exit when red candle low breaches 5EMA minus 0_5 ATR`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // 5EMA = 100, ATR = 4, threshold = 100 - (0.5 * 4) = 98
    // Red candle (close < open) with low below threshold
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 97.0, // Below threshold
        closePrice = 98.5, // Red candle (close < open)
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should exit when red candle low drops below 5EMA - 0.5ATR",
    )
  }

  @Test
  fun `should not exit when green candle even if low breaches threshold`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold = 100 - (0.5 * 4) = 98
    // Green candle (close >= open) - should NOT exit even if low breached
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 98.0,
        low = 97.0, // Below threshold
        closePrice = 100.0, // Green candle (close > open)
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit on green candle even if low breached threshold",
    )
  }

  @Test
  fun `should not exit when doji even if low breaches threshold`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold = 100 - (0.5 * 4) = 98
    // Doji (close == open) - should NOT exit
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 99.0,
        low = 97.0, // Below threshold
        closePrice = 99.0, // Doji (close == open)
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit on doji even if low breached threshold",
    )
  }

  @Test
  fun `should not exit when red candle but low above threshold`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold = 100 - (0.5 * 4) = 98
    // Red candle but low didn't breach threshold
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 98.5, // Above threshold
        closePrice = 99.0, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit when red candle but low stays above threshold",
    )
  }

  @Test
  fun `should not exit when low equals threshold exactly`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold = 100 - (0.5 * 4) = 98
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 98.0, // Exactly at threshold
        closePrice = 99.0, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit when low equals threshold exactly",
    )
  }

  @Test
  fun `should work with different EMA periods`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 10, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold using 10EMA = 100 - (0.5 * 4) = 98
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 97.0, // Below threshold
        closePrice = 98.0, // Red candle
        closePriceEMA10 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should work with 10EMA period",
    )
  }

  @Test
  fun `should work with different ATR multipliers`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 1.0)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold = 100 - (1.0 * 4) = 96
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 98.0,
        low = 95.0, // Below threshold
        closePrice = 96.0, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should work with 1.0 ATR multiplier",
    )
  }

  @Test
  fun `should work with larger ATR multiplier for looser stop`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 2.0)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold = 100 - (2.0 * 4) = 92
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 94.0,
        low = 93.0, // Above threshold (looser stop)
        closePrice = 93.5, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit with looser stop (2.0 ATR multiplier)",
    )

    val quote2 =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 6),
        openPrice = 92.0,
        low = 91.0, // Below threshold
        closePrice = 91.5, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote2),
      "Should exit when low drops below looser threshold on red candle",
    )
  }

  @Test
  fun `should work with 20EMA period`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 20, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold using 20EMA = 100 - (0.5 * 4) = 98
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 97.0,
        closePrice = 98.0, // Red candle
        closePriceEMA20 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should work with 20EMA period",
    )
  }

  @Test
  fun `should work with 50EMA period`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 50, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold using 50EMA = 100 - (0.5 * 4) = 98
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 97.0,
        closePrice = 98.0, // Red candle
        closePriceEMA50 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should work with 50EMA period",
    )
  }

  @Test
  fun `should work with 100EMA period`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 100, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold using 100EMA = 100 - (0.5 * 4) = 98
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 97.0,
        closePrice = 98.0, // Red candle
        closePriceEMA100 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should work with 100EMA period",
    )
  }

  @Test
  fun `should handle varying ATR values`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // With ATR = 10, threshold = 100 - (0.5 * 10) = 95
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 96.0,
        low = 94.0, // Below threshold
        closePrice = 95.0, // Red candle
        closePriceEMA5 = 100.0,
        atr = 10.0, // Higher ATR = wider buffer
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should handle varying ATR values correctly",
    )
  }

  @Test
  fun `should handle small ATR values`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // With small ATR = 1, threshold = 100 - (0.5 * 1) = 99.5
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 99.4, // Below threshold
        closePrice = 99.5, // Red candle
        closePriceEMA5 = 100.0,
        atr = 1.0, // Smaller ATR = tighter stop
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should handle small ATR values (tighter stop)",
    )
  }

  @Test
  fun `should not exit when price is well above EMA on red candle`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 100.0,
      )

    // Threshold = 98, but price well above
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 111.0,
        low = 109.0, // Well above threshold
        closePrice = 110.0, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit when low is well above threshold even on red candle",
    )
  }

  @Test
  fun `should provide correct exit reason with default parameters`() {
    val condition = PriceBelowEmaMinusAtrExit()
    assertEquals("Red candle with low below 5EMA - 0.5ATR", condition.exitReason())
  }

  @Test
  fun `should provide correct exit reason with custom parameters`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 10, atrMultiplier = 1.0)
    assertEquals("Red candle with low below 10EMA - 1.0ATR", condition.exitReason())
  }

  @Test
  fun `should provide correct description with default parameters`() {
    val condition = PriceBelowEmaMinusAtrExit()
    assertEquals("Red candle with low below 5EMA - 0.5ATR", condition.description())
  }

  @Test
  fun `should provide correct description with custom parameters`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 20, atrMultiplier = 2.0)
    assertEquals("Red candle with low below 20EMA - 2.0ATR", condition.description())
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = PriceBelowEmaMinusAtrExit()
    val metadata = condition.getMetadata()

    assertEquals("priceBelowEmaMinusAtr", metadata.type)
    assertEquals("Red Candle Low Below EMA Minus ATR", metadata.displayName)
    assertEquals("StopLoss", metadata.category)
    assertEquals(2, metadata.parameters.size)

    val emaPeriodParam = metadata.parameters.find { it.name == "emaPeriod" }
    assertNotNull(emaPeriodParam)
    assertEquals("number", emaPeriodParam?.type)
    assertEquals(5, emaPeriodParam?.defaultValue)

    val atrMultiplierParam = metadata.parameters.find { it.name == "atrMultiplier" }
    assertNotNull(atrMultiplierParam)
    assertEquals("number", atrMultiplierParam?.type)
    assertEquals(0.5, atrMultiplierParam?.defaultValue)
    assertEquals(0.1, atrMultiplierParam?.min)
    assertEquals(5.0, atrMultiplierParam?.max)
  }

  @Test
  fun `should handle edge case with low just below threshold on red candle`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold = 100 - (0.5 * 4) = 98
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 99.0,
        low = 97.99, // Just below threshold
        closePrice = 98.5, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should exit when low is just below threshold on red candle",
    )
  }

  @Test
  fun `should handle edge case with low just above threshold on red candle`() {
    val condition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Threshold = 100 - (0.5 * 4) = 98
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 100.0,
        low = 98.01, // Just above threshold
        closePrice = 99.0, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit when low is just above threshold",
    )
  }

  @Test
  fun `should create looser stop with higher ATR multiplier`() {
    val tightCondition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 0.5)
    val looseCondition = PriceBelowEmaMinusAtrExit(emaPeriod = 5, atrMultiplier = 2.0)

    val entryQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 105.0,
      )

    // Low at 93: triggers tight (0.5 ATR) but not loose (2.0 ATR)
    // Tight threshold = 100 - (0.5 * 4) = 98
    // Loose threshold = 100 - (2.0 * 4) = 92
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 5),
        openPrice = 94.0,
        low = 93.0,
        closePrice = 93.5, // Red candle
        closePriceEMA5 = 100.0,
        atr = 4.0,
      )

    assertTrue(
      tightCondition.shouldExit(stock, entryQuote, quote),
      "Tight stop should exit at 93",
    )

    assertFalse(
      looseCondition.shouldExit(stock, entryQuote, quote),
      "Loose stop should not exit at 93",
    )
  }
}
