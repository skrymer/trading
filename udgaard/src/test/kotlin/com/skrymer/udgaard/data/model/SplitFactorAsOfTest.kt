package com.skrymer.udgaard.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SplitFactorAsOfTest {
  private fun stockWithSplits(vararg splits: Split) = Stock(symbol = "AAPL", splits = splits.toList())

  @Test
  fun `splitFactorAsOf is 1 when the symbol has no splits`() {
    // Given a symbol with no recorded splits
    val stock = stockWithSplits()

    // When / Then: the cumulative split factor is the identity
    assertEquals(1.0, stock.splitFactorAsOf(LocalDate.of(2020, 1, 1)), 0.0)
  }

  @Test
  fun `splitFactorAsOf multiplies every split whose ex-date is after the date`() {
    // Given a 2:1 and a 7:1 split, both effective after 2014-01-01
    val stock =
      stockWithSplits(
        Split(symbol = "AAPL", exDate = LocalDate.of(2014, 6, 9), ratio = 7.0),
        Split(symbol = "AAPL", exDate = LocalDate.of(2020, 8, 31), ratio = 4.0),
      )

    // When / Then: k = 7 × 4 = 28 (the combined back-adjustment from 2014 to today)
    assertEquals(28.0, stock.splitFactorAsOf(LocalDate.of(2014, 1, 1)), 0.0)
  }

  @Test
  fun `splitFactorAsOf counts only splits strictly after the date, excluding one on the date itself`() {
    // Given three splits relative to the as-of date 2020-08-31: one already past, one exactly on the
    // date (the raw close already trades post-split that session), one still in the future
    val asOf = LocalDate.of(2020, 8, 31)
    val stock =
      stockWithSplits(
        Split(symbol = "AAPL", exDate = LocalDate.of(2014, 6, 9), ratio = 7.0),
        Split(symbol = "AAPL", exDate = asOf, ratio = 4.0),
        Split(symbol = "AAPL", exDate = LocalDate.of(2022, 1, 1), ratio = 3.0),
      )

    // When / Then: only the strictly-future 3:1 counts — the past 7:1 and the same-day 4:1 are excluded
    assertEquals(3.0, stock.splitFactorAsOf(asOf), 0.0)
  }
}
