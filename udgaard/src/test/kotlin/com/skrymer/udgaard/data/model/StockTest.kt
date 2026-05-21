package com.skrymer.udgaard.data.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StockTest {
  @Test
  fun `should reject unsorted quotes`() {
    val unsortedQuotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 98.0),
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 99.5),
      )

    assertThrows(IllegalArgumentException::class.java) {
      Stock("TEST", quotes = unsortedQuotes)
    }
  }

  @Test
  fun `should accept sorted quotes`() {
    val sortedQuotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 99.5),
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 98.0),
      )

    assertDoesNotThrow {
      Stock("TEST", quotes = sortedQuotes)
    }
  }

  @Test
  fun `should accept empty quotes`() {
    assertDoesNotThrow {
      Stock("TEST")
    }
  }

  @Test
  fun `should accept single quote`() {
    assertDoesNotThrow {
      Stock("TEST", quotes = listOf(StockQuote(date = LocalDate.of(2024, 1, 1))))
    }
  }

  @Test
  fun `currentOvtlyrSignal is BUY on a date between a BUY call and the next SELL`() {
    // Given: a BUY on May 4 and a SELL on May 20 — no signal row in between
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals =
          listOf(
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY),
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 20), OvtlyrSignalType.SELL),
          ),
      )

    // When: asking on May 12, a bar with no row, mid BUY-stance
    val current = stock.currentOvtlyrSignal(LocalDate.of(2026, 5, 12))

    // Then: the current signal is BUY — the most recent call still stands
    assertEquals(OvtlyrSignalType.BUY, current)
  }

  @Test
  fun `currentOvtlyrSignal is null before the symbol's first ever signal`() {
    // Given: the first signal is May 4
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY)),
      )

    // When / Then: a date before any signal has no standing state
    assertNull(stock.currentOvtlyrSignal(LocalDate.of(2026, 5, 1)))
  }

  @Test
  fun `currentOvtlyrSignal flips to SELL on the SELL bar and stays SELL after`() {
    // Given: a BUY then a SELL
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals =
          listOf(
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY),
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 20), OvtlyrSignalType.SELL),
          ),
      )

    // When / Then: SELL on the SELL bar itself, and on a later bar
    assertEquals(OvtlyrSignalType.SELL, stock.currentOvtlyrSignal(LocalDate.of(2026, 5, 20)))
    assertEquals(OvtlyrSignalType.SELL, stock.currentOvtlyrSignal(LocalDate.of(2026, 6, 1)))
  }
}
