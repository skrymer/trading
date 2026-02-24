package com.skrymer.udgaard.data.integration.massive.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MassiveAggregatesResponseTest {
  @Nested
  inner class ToStockQuotes {
    @Test
    fun `converts aggregate bars to stock quotes with correct fields`() {
      val response = MassiveAggregatesResponse(
        ticker = "AAPL",
        status = "OK",
        results = listOf(
          AggregateBar(
            open = 150.0,
            high = 155.0,
            low = 149.0,
            close = 154.0,
            volume = 80_000_000L,
            timestamp = 1704067200000L, // 2024-01-01 00:00:00 UTC = 2023-12-31 ET
          ),
        ),
      )

      val quotes = response.toStockQuotes("AAPL")

      assertEquals(1, quotes.size)
      val quote = quotes[0]
      assertEquals("AAPL", quote.symbol)
      assertEquals(150.0, quote.openPrice)
      assertEquals(155.0, quote.high)
      assertEquals(149.0, quote.low)
      assertEquals(154.0, quote.closePrice)
      assertEquals(80_000_000L, quote.volume)
    }

    @Test
    fun `converts timestamp to Eastern Time date`() {
      // 2024-01-02 05:00:00 UTC = 2024-01-02 00:00:00 ET
      val response = MassiveAggregatesResponse(
        ticker = "AAPL",
        status = "OK",
        results = listOf(
          AggregateBar(timestamp = 1704171600000L),
        ),
      )

      val quotes = response.toStockQuotes("AAPL")

      assertEquals(LocalDate.of(2024, 1, 2), quotes[0].date)
    }

    @Test
    fun `filters out quotes before minDate`() {
      // 1451624400000 = 2016-01-01 05:00:00 UTC = 2016-01-01 00:00:00 EST
      // 1451538000000 = 2015-12-31 05:00:00 UTC = 2015-12-31 00:00:00 EST
      val response = MassiveAggregatesResponse(
        ticker = "AAPL",
        status = "OK",
        results = listOf(
          AggregateBar(timestamp = 1451624400000L),
          AggregateBar(timestamp = 1451538000000L),
        ),
      )

      val quotes = response.toStockQuotes("AAPL", minDate = LocalDate.of(2016, 1, 1))

      assertEquals(1, quotes.size)
      assertEquals(LocalDate.of(2016, 1, 1), quotes[0].date)
    }

    @Test
    fun `sorts quotes by date ascending`() {
      val response = MassiveAggregatesResponse(
        ticker = "AAPL",
        status = "OK",
        results = listOf(
          AggregateBar(timestamp = 1704258000000L), // 2024-01-03
          AggregateBar(timestamp = 1704171600000L), // 2024-01-02
          AggregateBar(timestamp = 1704344400000L), // 2024-01-04
        ),
      )

      val quotes = response.toStockQuotes("AAPL")

      assertEquals(LocalDate.of(2024, 1, 2), quotes[0].date)
      assertEquals(LocalDate.of(2024, 1, 3), quotes[1].date)
      assertEquals(LocalDate.of(2024, 1, 4), quotes[2].date)
    }

    @Test
    fun `returns empty list when results is null`() {
      val response = MassiveAggregatesResponse(
        ticker = "AAPL",
        status = "OK",
        results = null,
      )

      val quotes = response.toStockQuotes("AAPL")

      assertTrue(quotes.isEmpty())
    }

    @Test
    fun `returns empty list when results is empty`() {
      val response = MassiveAggregatesResponse(
        ticker = "AAPL",
        status = "OK",
        results = emptyList(),
      )

      val quotes = response.toStockQuotes("AAPL")

      assertTrue(quotes.isEmpty())
    }
  }

  @Nested
  inner class DtoHelpers {
    @Test
    fun `hasError returns true when status is ERROR`() {
      val dto = MassiveAggregatesResponse(status = "ERROR")
      assertTrue(dto.hasError())
    }

    @Test
    fun `hasError returns true when error present`() {
      val dto = MassiveAggregatesResponse(error = "Not found")
      assertTrue(dto.hasError())
    }

    @Test
    fun `hasError returns true when message present`() {
      val dto = MassiveAggregatesResponse(message = "Rate limited")
      assertTrue(dto.hasError())
    }

    @Test
    fun `hasError returns false for valid response`() {
      val dto = MassiveAggregatesResponse(
        status = "OK",
        ticker = "AAPL",
        results = emptyList(),
      )
      assertFalse(dto.hasError())
    }

    @Test
    fun `isValid returns true when ticker and results present`() {
      val dto = MassiveAggregatesResponse(
        status = "OK",
        ticker = "AAPL",
        results = emptyList(),
      )
      assertTrue(dto.isValid())
    }

    @Test
    fun `isValid returns false when ticker null`() {
      val dto = MassiveAggregatesResponse(
        status = "OK",
        results = emptyList(),
      )
      assertFalse(dto.isValid())
    }

    @Test
    fun `isValid returns false when results null`() {
      val dto = MassiveAggregatesResponse(
        status = "OK",
        ticker = "AAPL",
      )
      assertFalse(dto.isValid())
    }

    @Test
    fun `getErrorDescription returns error when present`() {
      val dto = MassiveAggregatesResponse(error = "Not found")
      assertEquals("Not found", dto.getErrorDescription())
    }

    @Test
    fun `getErrorDescription returns message when error null`() {
      val dto = MassiveAggregatesResponse(message = "Rate limited")
      assertEquals("Rate limited", dto.getErrorDescription())
    }

    @Test
    fun `getErrorDescription returns Unknown error when both null`() {
      val dto = MassiveAggregatesResponse()
      assertEquals("Unknown error", dto.getErrorDescription())
    }
  }
}
