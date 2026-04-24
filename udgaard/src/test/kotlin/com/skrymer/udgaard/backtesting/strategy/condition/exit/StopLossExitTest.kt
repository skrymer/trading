package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StopLossExitTest {
  private val stock = Stock()

  @Test
  fun `should exit when price drops below stop loss level`() {
    val condition = StopLossExit(atrMultiplier = 2.0)

    val entryQuote =
      StockQuote(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 100.0,
        atr = 2.0,
      )

    // Stop loss level: 100 - (2 * 2) = 96
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 95.0, // Below stop loss
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should exit when price drops below stop loss level",
    )
  }

  @Test
  fun `should not exit when price is above stop loss level`() {
    val condition = StopLossExit(atrMultiplier = 2.0)

    val entryQuote =
      StockQuote(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 100.0,
        atr = 2.0,
      )

    // Stop loss level: 100 - (2 * 2) = 96
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 97.0, // Above stop loss
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit when price is above stop loss level",
    )
  }

  @Test
  fun `should not exit when price equals stop loss level`() {
    val condition = StopLossExit(atrMultiplier = 2.0)

    val entryQuote =
      StockQuote(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 100.0,
        atr = 2.0,
      )

    // Stop loss level: 100 - (2 * 2) = 96
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 96.0, // Exactly at stop loss
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit when price equals stop loss level",
    )
  }

  @Test
  fun `should work with different ATR multipliers`() {
    val condition = StopLossExit(atrMultiplier = 1.5)

    val entryQuote =
      StockQuote(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 100.0,
        atr = 2.0,
      )

    // Stop loss level: 100 - (1.5 * 2) = 97
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 96.5, // Below stop loss
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should work with different ATR multipliers",
    )
  }

  @Test
  fun `should work with different ATR values`() {
    val condition = StopLossExit(atrMultiplier = 2.0)

    val entryQuote =
      StockQuote(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 100.0,
        atr = 3.0, // Different ATR
      )

    // Stop loss level: 100 - (2 * 3) = 94
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 93.0, // Below stop loss
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote),
      "Should work with different ATR values",
    )
  }

  @Test
  fun `should not exit when entryQuote is null`() {
    val condition = StopLossExit(atrMultiplier = 2.0)

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 50.0, // Very low price
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when entryQuote is null",
    )
  }

  @Test
  fun `should not exit when price is moving up from entry`() {
    val condition = StopLossExit(atrMultiplier = 2.0)

    val entryQuote =
      StockQuote(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 100.0,
        atr = 2.0,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 105.0, // Price went up
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit when price is moving up from entry",
    )
  }

  @Test
  fun `should provide correct exit reason`() {
    val condition = StopLossExit(atrMultiplier = 2.0)
    assertEquals("Stop loss triggered (2.0 ATR below entry)", condition.exitReason())
  }

  @Test
  fun `should provide correct description`() {
    val condition = StopLossExit(atrMultiplier = 2.0)
    assertEquals("Stop loss (2.0 ATR)", condition.description())
  }

  @Test
  fun `should use default ATR multiplier of 2`() {
    val condition = StopLossExit()
    assertEquals("Stop loss (2.0 ATR)", condition.description())
  }

  @Test
  fun `should handle small price movements correctly`() {
    val condition = StopLossExit(atrMultiplier = 2.0)

    val entryQuote =
      StockQuote(
        date = LocalDate.of(2024, 1, 1),
        closePrice = 100.0,
        atr = 2.0,
      )

    // Stop loss level: 100 - (2 * 2) = 96
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 96.01, // Just above stop loss
      )

    assertFalse(
      condition.shouldExit(stock, entryQuote, quote),
      "Should not exit when just above stop loss",
    )

    val quote2 =
      StockQuote(
        date = LocalDate.of(2024, 1, 5),
        closePrice = 95.99, // Just below stop loss
      )

    assertTrue(
      condition.shouldExit(stock, entryQuote, quote2),
      "Should exit when just below stop loss",
    )
  }

  // ===== proximity =====

  @Test
  fun `proximity is 1_0 when close has dropped to the stop level`() {
    // Given: entry at $100 with 2 ATR stop, price drops exactly to $96 (the stop).
    val condition = StopLossExit(atrMultiplier = 2.0)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 5), closePrice = 96.0)

    // When
    val proximity = condition.proximity(stock, entryQuote, quote)

    // Then: invariant — whenever the quote reaches the stop, proximity must be >= 1.0.
    assertNotNull(proximity)
    assertEquals(1.0, proximity!!.proximity, 1e-9, "at-stop should be exactly 1.0")
    assertEquals("stopLoss", proximity.conditionType)
  }

  @Test
  fun `proximity is 0_5 when close is halfway between entry and stop`() {
    // Given: entry at $100 with 2 ATR stop; price at $98 is halfway to the $96 stop.
    val condition = StopLossExit(atrMultiplier = 2.0)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 5), closePrice = 98.0)

    // When
    val proximity = condition.proximity(stock, entryQuote, quote)

    // Then
    assertNotNull(proximity)
    assertEquals(0.5, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity is 0_0 when close equals entry`() {
    // Given: no drop at all — fresh entry.
    val condition = StopLossExit(atrMultiplier = 2.0)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 5), closePrice = 100.0)

    // When
    val proximity = condition.proximity(stock, entryQuote, quote)

    // Then
    assertNotNull(proximity)
    assertEquals(0.0, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity returns null when entryQuote is null`() {
    // Given: no entry reference — proximity can't be computed.
    val condition = StopLossExit(atrMultiplier = 2.0)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 5), closePrice = 95.0)

    // When / Then
    assertNull(condition.proximity(stock, null, quote))
  }

  @Test
  fun `proximity returns null when entry atr is zero`() {
    // Given: entry quote with atr=0 — the stop-distance formula would divide by zero.
    val condition = StopLossExit(atrMultiplier = 2.0)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 0.0)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 5), closePrice = 95.0)

    // When / Then
    assertNull(condition.proximity(stock, entryQuote, quote))
  }

  @Test
  fun `proximity is clamped to 1_0 when close has dropped beyond the stop level`() {
    // Given: gap-down far past the stop. Proximity must not exceed 1.0.
    val condition = StopLossExit(atrMultiplier = 2.0)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 5), closePrice = 80.0) // well below $96 stop

    // When
    val proximity = condition.proximity(stock, entryQuote, quote)

    // Then
    assertNotNull(proximity)
    assertEquals(1.0, proximity!!.proximity, 1e-9)
  }
}
