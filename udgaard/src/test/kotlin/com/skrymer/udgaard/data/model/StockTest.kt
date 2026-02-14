package com.skrymer.udgaard.data.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
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
}
