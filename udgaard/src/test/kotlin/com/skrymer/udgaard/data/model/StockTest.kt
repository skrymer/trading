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

  @Test
  fun `ovtlyrSignalOn returns BUY on the exact date the BUY signal fired`() {
    // Given: a BUY signal fired on May 4
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY)),
      )

    // When / Then: the call day itself reports the BUY event
    assertEquals(OvtlyrSignalType.BUY, stock.ovtlyrSignalOn(LocalDate.of(2026, 5, 4)))
  }

  @Test
  fun `ovtlyrSignalOn is null on a no-row date between a BUY call and the next SELL`() {
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

    // When / Then: May 12 has no call — null, even though the standing state is BUY
    assertNull(stock.ovtlyrSignalOn(LocalDate.of(2026, 5, 12)))
  }

  @Test
  fun `ovtlyrSignalOn returns SELL on the exact date the SELL signal fired`() {
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

    // When / Then: the SELL call day reports the SELL event
    assertEquals(OvtlyrSignalType.SELL, stock.ovtlyrSignalOn(LocalDate.of(2026, 5, 20)))
  }

  @Test
  fun `ovtlyrSignalOn is null when the stock has no Ovtlyr signals`() {
    // Given: a stock with no Ovtlyr coverage
    val stock = Stock(symbol = "AAPL")

    // When / Then: no event on any date
    assertNull(stock.ovtlyrSignalOn(LocalDate.of(2026, 5, 4)))
  }

  private fun fundamental(
    fiscal: String,
    filing: String,
    grossProfit: Double? = null,
    totalAssets: Double? = null,
    operatingIncome: Double? = null,
    totalRevenue: Double? = null,
  ) = Fundamental(
    symbol = "AAPL",
    fiscalDateEnding = LocalDate.parse(fiscal),
    filingDate = LocalDate.parse(filing),
    grossProfit = grossProfit,
    totalAssets = totalAssets,
    operatingIncome = operatingIncome,
    totalRevenue = totalRevenue,
  )

  // Four quarters filed before 2024-03-01, plus a fifth filed after it (2024-05-01) to prove the as-of gate.
  private fun fiveQuarterStock() =
    Stock(
      symbol = "AAPL",
      fundamentals =
        listOf(
          fundamental("2023-03-31", "2023-05-01", grossProfit = 10.0, totalAssets = 1000.0, operatingIncome = 5.0, totalRevenue = 50.0),
          fundamental("2023-06-30", "2023-08-01", grossProfit = 11.0, totalAssets = 1100.0, operatingIncome = 6.0, totalRevenue = 50.0),
          fundamental("2023-09-30", "2023-11-01", grossProfit = 12.0, totalAssets = 1200.0, operatingIncome = 7.0, totalRevenue = 50.0),
          fundamental("2023-12-31", "2024-02-01", grossProfit = 13.0, totalAssets = 1300.0, operatingIncome = 8.0, totalRevenue = 50.0),
          fundamental("2024-03-31", "2024-05-01", grossProfit = 99.0, totalAssets = 9999.0, operatingIncome = 99.0, totalRevenue = 50.0),
        ),
    )

  @Test
  fun `grossProfitTtmAsOf sums the four most-recent filings visible by filing date`() {
    // Given five quarters, the latest filed after the as-of date
    // When as of 2024-03-01 (the 2024-05-01 filing is not yet public)
    val ttm = fiveQuarterStock().grossProfitTtmAsOf(LocalDate.of(2024, 3, 1))

    // Then only the four visible quarters sum (10+11+12+13), the future filing excluded
    assertEquals(46.0, ttm)
  }

  @Test
  fun `latestFundamentalAsOf ignores a filing whose filing date is after the as-of date`() {
    // When as of 2024-03-01
    val latest = fiveQuarterStock().latestFundamentalAsOf(LocalDate.of(2024, 3, 1))

    // Then the most-recent visible filing is the 2023-12-31 quarter, not the future 2024-03-31 one
    assertEquals(LocalDate.of(2023, 12, 31), latest?.fiscalDateEnding)
    assertEquals(1300.0, latest?.totalAssets)
  }

  @Test
  fun `grossProfitTtmAsOf is null when fewer than four quarters are visible`() {
    // Given a stock with only three filings before the as-of date
    val stock =
      Stock(
        symbol = "AAPL",
        fundamentals =
          listOf(
            fundamental("2023-06-30", "2023-08-01", grossProfit = 11.0),
            fundamental("2023-09-30", "2023-11-01", grossProfit = 12.0),
            fundamental("2023-12-31", "2024-02-01", grossProfit = 13.0),
          ),
      )

    // When / Then: no trailing-twelve-month window, fails closed
    assertNull(stock.grossProfitTtmAsOf(LocalDate.of(2024, 3, 1)))
  }

  @Test
  fun `grossProfitTtmAsOf is null when a quarter inside the window omits gross profit`() {
    // Given four visible quarters, one missing gross profit
    val stock =
      Stock(
        symbol = "AAPL",
        fundamentals =
          listOf(
            fundamental("2023-03-31", "2023-05-01", grossProfit = 10.0),
            fundamental("2023-06-30", "2023-08-01", grossProfit = null),
            fundamental("2023-09-30", "2023-11-01", grossProfit = 12.0),
            fundamental("2023-12-31", "2024-02-01", grossProfit = 13.0),
          ),
      )

    // When / Then: the incomplete window fails closed
    assertNull(stock.grossProfitTtmAsOf(LocalDate.of(2024, 3, 1)))
  }

  @Test
  fun `grossProfitTtmAsOf keeps a negative trailing sum`() {
    // Given four loss-making quarters
    val stock =
      Stock(
        symbol = "AAPL",
        fundamentals =
          listOf(
            fundamental("2023-03-31", "2023-05-01", grossProfit = -10.0),
            fundamental("2023-06-30", "2023-08-01", grossProfit = -10.0),
            fundamental("2023-09-30", "2023-11-01", grossProfit = -10.0),
            fundamental("2023-12-31", "2024-02-01", grossProfit = -10.0),
          ),
      )

    // When / Then: the negative reading is a real, low-quality value — kept, not nulled
    assertEquals(-40.0, stock.grossProfitTtmAsOf(LocalDate.of(2024, 3, 1)))
  }

  @Test
  fun `operatingMarginTtmAsOf divides trailing operating income by trailing revenue`() {
    // When as of 2024-03-01 (visible operating income 5+6+7+8 = 26 over revenue 50*4 = 200)
    val margin = fiveQuarterStock().operatingMarginTtmAsOf(LocalDate.of(2024, 3, 1))

    // Then 26 / 200 = 0.13
    assertEquals(0.13, margin!!, 1e-9)
  }

  @Test
  fun `isVisibleAsOf is false for a null filing date and for a filing date after the trading date`() {
    val notFiled = Fundamental(symbol = "AAPL", fiscalDateEnding = LocalDate.of(2024, 3, 31), filingDate = null)
    val futureFiling = Fundamental(symbol = "AAPL", fiscalDateEnding = LocalDate.of(2024, 3, 31), filingDate = LocalDate.of(2024, 5, 1))
    val pastFiling = Fundamental(symbol = "AAPL", fiscalDateEnding = LocalDate.of(2024, 3, 31), filingDate = LocalDate.of(2024, 2, 1))

    // A record with no filing date is never visible; one filed after the date is not yet public; one filed on/before is
    assertEquals(false, notFiled.isVisibleAsOf(LocalDate.of(2024, 3, 1)))
    assertEquals(false, futureFiling.isVisibleAsOf(LocalDate.of(2024, 3, 1)))
    assertEquals(true, pastFiling.isVisibleAsOf(LocalDate.of(2024, 3, 1)))
  }
}
