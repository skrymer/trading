package com.skrymer.udgaard.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MarketCapAsOfTest {
  @Test
  fun `marketCapAsOf divides raw close by the cumulative split factor, never the dividend-adjusted close`() {
    // Given: a name with a 4:1 split effective 2020-08-31. Shares are carried on the current
    // (split-adjusted) basis — 16B — as EODHD reports them. On 2020-06-30 (pre-split) the raw close
    // is $364, while the stored close_price is the split-AND-dividend adjusted $90 (deliberately not
    // 364 / 4 = 91, to simulate the dividend leg the naive construct would wrongly inherit).
    val asOf = LocalDate.of(2020, 6, 30)
    val stock =
      Stock(
        symbol = "AAPL",
        quotes =
          listOf(
            StockQuote(symbol = "AAPL", date = asOf, closePrice = 90.0, rawClose = 364.0),
          ),
        fundamentals =
          listOf(
            Fundamental(
              symbol = "AAPL",
              fiscalDateEnding = LocalDate.of(2019, 12, 31),
              filingDate = LocalDate.of(2020, 2, 1),
              sharesOutstanding = 16_000_000_000L,
            ),
          ),
        splits =
          listOf(
            Split(symbol = "AAPL", exDate = LocalDate.of(2020, 8, 31), ratio = 4.0),
          ),
      )

    // When: the point-in-time market cap is read on the pre-split date
    val cap = stock.marketCapAsOf(asOf)

    // Then: cap = (rawClose / k) × shares = (364 / 4) × 16B = 1.456e12 — NOT raw × shares (construct b,
    // 4× too big), and NOT closePrice × shares (the dividend-adjusted trap, construct d).
    assertEquals(1_456_000_000_000.0, cap!!, 1.0)
  }

  @Test
  fun `marketCapAsOf is invariant to whether a future split has happened yet`() {
    // Given the same name on the same pre-split date, viewed two ways:
    val asOf = LocalDate.of(2020, 6, 30)

    // (1) as seen BEFORE the split — shares on the pre-split basis (4B), no split recorded, raw close $364
    val preSplit =
      Stock(
        symbol = "AAPL",
        quotes = listOf(StockQuote(symbol = "AAPL", date = asOf, rawClose = 364.0)),
        fundamentals =
          listOf(
            Fundamental(
              symbol = "AAPL",
              fiscalDateEnding = LocalDate.of(2019, 12, 31),
              filingDate = LocalDate.of(2020, 2, 1),
              sharesOutstanding = 4_000_000_000L,
            ),
          ),
      )

    // (2) as seen AFTER a 4:1 split — shares back-adjusted to the current basis (16B), the split now
    // recorded, the raw close UNCHANGED at $364 (raw closes are never re-adjusted)
    val postSplit =
      preSplit.copy(
        fundamentals = preSplit.fundamentals.map { it.copy(sharesOutstanding = 16_000_000_000L) },
        splits = listOf(Split(symbol = "AAPL", exDate = LocalDate.of(2020, 8, 31), ratio = 4.0)),
      )

    // When / Then: the point-in-time cap on that date is identical either way (construct (c) is
    // split-invariant) — a naive raw × shares would read 4× larger after the split.
    assertEquals(preSplit.marketCapAsOf(asOf)!!, postSplit.marketCapAsOf(asOf)!!, 1.0)
  }

  @Test
  fun `marketCapAsOf is null until the share-bearing fundamental is public, then defined`() {
    // Given a fundamental that becomes public on 2020-02-01, with priced raw-close bars on both sides
    val stock =
      Stock(
        symbol = "AAPL",
        quotes =
          listOf(
            StockQuote(symbol = "AAPL", date = LocalDate.of(2020, 1, 15), rawClose = 100.0),
            StockQuote(symbol = "AAPL", date = LocalDate.of(2020, 2, 1), rawClose = 100.0),
          ),
        fundamentals =
          listOf(
            Fundamental(
              symbol = "AAPL",
              fiscalDateEnding = LocalDate.of(2019, 12, 31),
              filingDate = LocalDate.of(2020, 2, 1),
              sharesOutstanding = 1_000_000_000L,
            ),
          ),
      )

    // When / Then: before the filing date the shares are not yet public → no cap, despite a priced bar
    assertNull(stock.marketCapAsOf(LocalDate.of(2020, 1, 15)))
    // On the filing date the fundamental is visible → cap = 100 × 1B
    assertEquals(100_000_000_000.0, stock.marketCapAsOf(LocalDate.of(2020, 2, 1))!!, 1.0)
  }

  @Test
  fun `marketCapAsOf is null when the visible fundamental reports no shares`() {
    // Given a priced raw-close bar and a visible fundamental that omits sharesOutstanding
    val asOf = LocalDate.of(2020, 6, 30)
    val stock =
      Stock(
        symbol = "AAPL",
        quotes = listOf(StockQuote(symbol = "AAPL", date = asOf, rawClose = 100.0)),
        fundamentals =
          listOf(
            Fundamental(
              symbol = "AAPL",
              fiscalDateEnding = LocalDate.of(2019, 12, 31),
              filingDate = LocalDate.of(2020, 2, 1),
              sharesOutstanding = null,
            ),
          ),
      )

    // When / Then: with no share count the cap is undefined — the name is absent from any top-N
    assertNull(stock.marketCapAsOf(asOf))
  }

  @Test
  fun `marketCapAsOf is null when the bar carries no raw close`() {
    // Given a fully-specified share-bearing fundamental but a bar with no raw close (not yet re-ingested)
    val asOf = LocalDate.of(2020, 6, 30)
    val stock =
      Stock(
        symbol = "AAPL",
        quotes = listOf(StockQuote(symbol = "AAPL", date = asOf, closePrice = 100.0, rawClose = null)),
        fundamentals =
          listOf(
            Fundamental(
              symbol = "AAPL",
              fiscalDateEnding = LocalDate.of(2019, 12, 31),
              filingDate = LocalDate.of(2020, 2, 1),
              sharesOutstanding = 1_000_000_000L,
            ),
          ),
      )

    // When / Then: without a raw close the cap is undefined — the stored adjusted closePrice is NOT a
    // substitute (it carries the dividend bias ADR 0027 forbids), so the name is absent from any top-N
    assertNull(stock.marketCapAsOf(asOf))
  }

  @Test
  fun `marketCapAsOf is null on a date before the symbol's first bar`() {
    // Given a share-bearing fundamental but a first bar that only starts in 2021
    val stock =
      Stock(
        symbol = "AAPL",
        quotes = listOf(StockQuote(symbol = "AAPL", date = LocalDate.of(2021, 1, 4), rawClose = 100.0)),
        fundamentals =
          listOf(
            Fundamental(
              symbol = "AAPL",
              fiscalDateEnding = LocalDate.of(2019, 12, 31),
              filingDate = LocalDate.of(2020, 2, 1),
              sharesOutstanding = 1_000_000_000L,
            ),
          ),
      )

    // When / Then: no raw close exists on or before 2020-06-30 → no cap
    assertNull(stock.marketCapAsOf(LocalDate.of(2020, 6, 30)))
  }
}
