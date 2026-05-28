package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PercentGainExitTest {
  private val exit = PercentGainExit()
  private val stock = Stock(symbol = "TEST")
  private val entryDate = LocalDate.of(2024, 1, 1)
  private val today = LocalDate.of(2024, 6, 15)

  @Test
  fun `triggers exit when gain meets target`() {
    // Given: entry at 100, today at 110 — exactly 10% gain (default target)
    val entry = quote(entryDate, closePrice = 100.0)
    val now = quote(today, closePrice = 110.0)

    // When
    val triggered = exit.shouldExit(stock, entry, now)

    // Then: exit fires at the boundary (>= comparison)
    assertTrue(triggered, "Exit should fire at exactly the target percentage")
  }

  @Test
  fun `does not trigger when gain is below target`() {
    // Given: entry at 100, today at 109.99 — just below 10%
    val entry = quote(entryDate, closePrice = 100.0)
    val now = quote(today, closePrice = 109.99)

    // When
    val triggered = exit.shouldExit(stock, entry, now)

    // Then: no exit
    assertFalse(triggered, "Exit should not fire below the target percentage")
  }

  @Test
  fun `does not trigger on negative position move`() {
    // Given: entry at 100, today at 90 — 10% loss
    val entry = quote(entryDate, closePrice = 100.0)
    val now = quote(today, closePrice = 90.0)

    // When
    val triggered = exit.shouldExit(stock, entry, now)

    // Then: no exit (this exit is a profit-take, not a stop-loss)
    assertFalse(triggered, "Exit should not fire on negative moves — separate stop-loss handles those")
  }

  @Test
  fun `does not trigger when entry quote is null`() {
    // Given: no entry quote (early-loop state)
    val now = quote(today, closePrice = 200.0)

    // When
    val triggered = exit.shouldExit(stock, null, now)

    // Then: no exit
    assertFalse(triggered, "Exit should not fire when entry quote is null")
  }

  @Test
  fun `does not trigger when entry close is non-positive`() {
    // Given: malformed entry with close = 0 (would cause divide-by-zero if not guarded)
    val entry = quote(entryDate, closePrice = 0.0)
    val now = quote(today, closePrice = 200.0)

    // When
    val triggered = exit.shouldExit(stock, entry, now)

    // Then: no exit — guard against bad data
    assertFalse(triggered, "Exit should not fire when entry close is non-positive")
  }

  @Test
  fun `custom target percentage honored`() {
    // Given: a 5% target exit with entry at 100, today at 105
    val custom = PercentGainExit(targetPct = 5.0)
    val entry = quote(entryDate, closePrice = 100.0)
    val now = quote(today, closePrice = 105.0)

    // When
    val triggered = custom.shouldExit(stock, entry, now)

    // Then: fires at exactly 5%
    assertTrue(triggered, "Custom 5% target should fire at exactly 5% gain")
  }

  @Test
  fun `parseConfig honors targetPct`() {
    // Given: parameter map overriding default
    val configured = exit.parseConfig(mapOf("targetPct" to 15.0)) as PercentGainExit

    // When: evaluating against a 12% gain (above default 10%, below custom 15%)
    val entry = quote(entryDate, closePrice = 100.0)
    val now = quote(today, closePrice = 112.0)
    val triggered = configured.shouldExit(stock, entry, now)

    // Then: configured target applies
    assertFalse(triggered, "Configured 15% target should not fire at 12% gain")
  }

  @Test
  fun `evaluateWithDetails surfaces gain percentage and threshold`() {
    // Given: entry at 100, today at 115 — 15% gain (above default 10% target)
    val entry = quote(entryDate, closePrice = 100.0)
    val now = quote(today, closePrice = 115.0)

    // When
    val result = exit.evaluateWithDetails(stock, entry, now)

    // Then: result includes actual gain and threshold
    assertTrue(result.passed)
    assertEquals("15.00%", result.actualValue)
    assertEquals(">= 10.0%", result.threshold)
  }

  @Test
  fun `metadata exposes stable identifiers`() {
    val metadata = exit.getMetadata()

    assertEquals("percentGain", metadata.type)
    assertEquals("ProfitTaking", metadata.category)
    assertEquals(1, metadata.parameters.size)
  }

  private fun quote(date: LocalDate, closePrice: Double): StockQuote =
    StockQuote(
      symbol = "TEST",
      date = date,
      openPrice = closePrice,
      closePrice = closePrice,
      high = closePrice + 0.5,
      low = closePrice - 0.5,
      atr = 1.0,
      volume = 100_000L,
    )
}
