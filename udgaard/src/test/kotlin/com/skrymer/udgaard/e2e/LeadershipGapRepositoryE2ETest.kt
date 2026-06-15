package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.repository.LeadershipGapRepository
import com.skrymer.udgaard.jooq.tables.references.STOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.sqrt
import kotlin.test.assertEquals

/**
 * Confirms the full-universe equal-weight 20-bar-return aggregate: equal-weight mean, cross-sectional
 * sample stdev, and contributing count per date, over the point-in-time STOCK universe (null/unclassified
 * asset_type held out, fail-closed — ADR 0026), with a `LAG(close, n)` trailing return. Uses a small
 * lookback (2) on unique far-future dates so the fixtures stay tiny and don't perturb the shared container.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeadershipGapRepositoryE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @Autowired
  private lateinit var repository: LeadershipGapRepository

  // Two decoupled, unique date triples so each test aggregates only its own symbols.
  private val mathStart = LocalDate.of(2032, 2, 2)
  private val filterStart = LocalDate.of(2033, 3, 3)

  @BeforeAll
  fun setupTestData() {
    // Math fixture: two STOCKs whose 2-bar returns are +20% and -20% on the last bar.
    insertSeries("EWRA", "STOCK", mathStart, listOf(100.0, 100.0, 120.0))
    insertSeries("EWRB", "STOCK", mathStart, listOf(100.0, 100.0, 80.0))

    // Filter fixture: a STOCK (counted), plus a null-asset_type name and an ETF (both excluded).
    insertSeries("EWRC", "STOCK", filterStart, listOf(100.0, 100.0, 110.0))
    insertSeries("EWRN", null, filterStart, listOf(100.0, 100.0, 90.0))
    insertSeries("EWRE", "ETF", filterStart, listOf(100.0, 100.0, 600.0))
  }

  @AfterAll
  fun cleanup() {
    // These far-future quotes would otherwise be materialised into breadth rows by any later test that
    // calls refreshBreadthDaily() in the shared container, making a future date the max breadth date and
    // poisoning currentMarketDate for downstream tests (e.g. ScannerScanE2ETest). Clean up after ourselves.
    val symbols = listOf("EWRA", "EWRB", "EWRC", "EWRN", "EWRE")
    dsl.deleteFrom(STOCK_QUOTES).where(STOCK_QUOTES.STOCK_SYMBOL.`in`(symbols)).execute()
    dsl.deleteFrom(STOCKS).where(STOCKS.SYMBOL.`in`(symbols)).execute()
  }

  @Test
  fun `computes the equal-weight mean, cross-sectional stdev, and contributing count`() {
    // When: the 2-bar equal-weight return series is built over the math fixture
    val series = repository.ewReturnByDate(mathStart, mathStart.plusDays(2), lookbackBars = 2)

    // Then: on the last bar the equal-weight mean of {+0.20, -0.20} is 0, sample stdev is sqrt(0.08), N=2;
    // earlier bars have no 2-bar-ago close and are omitted.
    val last = series.getValue(mathStart.plusDays(2))
    assertEquals(0.0, last.meanReturn, 1e-9)
    assertEquals(sqrt(0.08), last.crossSectionalStdev, 1e-6)
    assertEquals(2, last.contributingN)
    assertEquals(false, series.containsKey(mathStart))
  }

  @Test
  fun `counts only STOCK asset types, excluding null and other asset types`() {
    // When
    val series = repository.ewReturnByDate(filterStart, filterStart.plusDays(2), lookbackBars = 2)

    // Then: only the STOCK (+10%) contributes -> mean +0.10, N=1. The null-asset_type name (-10%) is
    // held out (fail-closed) and the ETF (+500%) is excluded; had either leaked, N and the mean would shift.
    val last = series.getValue(filterStart.plusDays(2))
    assertEquals(1, last.contributingN)
    assertEquals(0.10, last.meanReturn, 1e-9)
  }

  private fun insertSeries(symbol: String, assetType: String?, start: LocalDate, closes: List<Double>) {
    dsl
      .insertInto(STOCKS)
      .set(STOCKS.SYMBOL, symbol)
      .set(STOCKS.ASSET_TYPE, assetType)
      .onConflict(STOCKS.SYMBOL)
      .doNothing()
      .execute()
    closes.forEachIndexed { offset, close ->
      dsl
        .insertInto(STOCK_QUOTES)
        .set(STOCK_QUOTES.STOCK_SYMBOL, symbol)
        .set(STOCK_QUOTES.QUOTE_DATE, start.plusDays(offset.toLong()))
        .set(STOCK_QUOTES.CLOSE_PRICE, BigDecimal.valueOf(close))
        .set(STOCK_QUOTES.OPEN_PRICE, BigDecimal.valueOf(close))
        .set(STOCK_QUOTES.HIGH_PRICE, BigDecimal.valueOf(close))
        .set(STOCK_QUOTES.LOW_PRICE, BigDecimal.valueOf(close))
        .set(STOCK_QUOTES.VOLUME, 1_000_000L)
        .execute()
    }
  }
}
