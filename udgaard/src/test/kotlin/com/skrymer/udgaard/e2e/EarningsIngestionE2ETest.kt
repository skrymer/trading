package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.integration.LatestQuote
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardSymbolDto
import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.service.StockIngestionService
import com.skrymer.udgaard.jooq.tables.references.EARNINGS
import com.skrymer.udgaard.jooq.tables.references.ORDER_BLOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end test for the earnings-ingestion pipeline: provider fetch →
 * `StockIngestionService.refreshStock` → `StockJooqRepository.save` → Postgres.
 *
 * Verifies (1) a successful refresh populates the `earnings` table, (2) a subsequent
 * refresh replaces all prior rows, and (3) a provider failure on a follow-up refresh
 * preserves the last-known-good earnings rather than wiping them — the Q4(c) safety
 * invariant that keeps `noEarningsWithinDays`/`exitBeforeEarnings` filters meaningful
 * during transient upstream outages.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EarningsIngestionE2ETest : AbstractIntegrationTest() {
  @MockitoBean
  private lateinit var midgaardClient: MidgaardClient

  @Autowired
  private lateinit var stockIngestionService: StockIngestionService

  @Autowired
  private lateinit var dsl: DSLContext

  private val symbol = "TESTING"

  @BeforeEach
  fun setup() {
    // Clean all rows for the test symbol so each test starts from a known empty state.
    // FK order: earnings + quotes + order_blocks cascade off stocks, but we delete them
    // explicitly to avoid relying on cascade for the test fixture.
    dsl.deleteFrom(EARNINGS).where(EARNINGS.STOCK_SYMBOL.eq(symbol)).execute()
    dsl.deleteFrom(STOCK_QUOTES).where(STOCK_QUOTES.STOCK_SYMBOL.eq(symbol)).execute()
    dsl.deleteFrom(ORDER_BLOCKS).where(ORDER_BLOCKS.STOCK_SYMBOL.eq(symbol)).execute()
    dsl.deleteFrom(STOCKS).where(STOCKS.SYMBOL.eq(symbol)).execute()

    // Stub the provider's non-earnings methods that fetchAndBuildStock needs.
    whenever(midgaardClient.getDailyAdjustedTimeSeries(any()))
      .thenReturn(
        listOf(StockQuote(symbol = symbol, date = LocalDate.of(2024, 1, 2), closePrice = 100.0, volume = 1_000L)),
      )
    whenever(midgaardClient.getSymbolInfo(any()))
      .thenReturn(MidgaardSymbolDto(symbol = symbol, assetType = "STOCK", sector = null, sectorSymbol = "XLK"))
    whenever(midgaardClient.getLatestQuote(any()))
      .thenReturn(LatestQuote(symbol = symbol, price = 100.0))
  }

  @Test
  fun `refresh populates earnings rows from the provider`() {
    // Given the provider has two earnings rows for the symbol
    whenever(midgaardClient.getEarnings(symbol))
      .thenReturn(
        listOf(
          earning(LocalDate.of(2024, 3, 31), reportedEPS = 1.50),
          earning(LocalDate.of(2024, 6, 30), reportedEPS = 1.75),
        ),
      )

    // When the stock is refreshed
    val refreshed = stockIngestionService.refreshStock(symbol)

    // Then the earnings table has both rows for that symbol, ordered by fiscal date asc
    assertNotNull(refreshed)
    val rows = dsl
      .selectFrom(EARNINGS)
      .where(EARNINGS.STOCK_SYMBOL.eq(symbol))
      .orderBy(EARNINGS.FISCAL_DATE_ENDING.asc())
      .fetch()
    assertEquals(2, rows.size)
    assertEquals(LocalDate.of(2024, 3, 31), rows[0][EARNINGS.FISCAL_DATE_ENDING])
    assertEquals(1.50, rows[0][EARNINGS.REPORTED_EPS]?.toDouble())
    assertEquals(LocalDate.of(2024, 6, 30), rows[1][EARNINGS.FISCAL_DATE_ENDING])
  }

  @Test
  fun `re-refresh replaces prior earnings rows`() {
    // Given the symbol has been refreshed once with two earnings rows
    whenever(midgaardClient.getEarnings(symbol))
      .thenReturn(
        listOf(
          earning(LocalDate.of(2023, 12, 31), reportedEPS = 1.20),
          earning(LocalDate.of(2024, 3, 31), reportedEPS = 1.50),
        ),
      )
    stockIngestionService.refreshStock(symbol)

    // When a second refresh runs with a different earnings set
    whenever(midgaardClient.getEarnings(symbol))
      .thenReturn(listOf(earning(LocalDate.of(2024, 9, 30), reportedEPS = 2.00)))
    stockIngestionService.refreshStock(symbol)

    // Then only the fresh row remains
    val dates = dsl
      .select(EARNINGS.FISCAL_DATE_ENDING)
      .from(EARNINGS)
      .where(EARNINGS.STOCK_SYMBOL.eq(symbol))
      .fetch(EARNINGS.FISCAL_DATE_ENDING)
    assertEquals(listOf(LocalDate.of(2024, 9, 30)), dates)
  }

  @Test
  fun `provider failure on follow-up refresh preserves last-known earnings`() {
    // Given the symbol has earnings populated from a successful first refresh
    val initialEarnings = listOf(
      earning(LocalDate.of(2024, 3, 31), reportedEPS = 1.50),
      earning(LocalDate.of(2024, 6, 30), reportedEPS = 1.75),
    )
    whenever(midgaardClient.getEarnings(symbol)).thenReturn(initialEarnings)
    stockIngestionService.refreshStock(symbol)

    // When the provider fails on the next refresh
    whenever(midgaardClient.getEarnings(symbol)).thenReturn(null)
    val refreshed = stockIngestionService.refreshStock(symbol)

    // Then the stock refresh succeeds AND the prior earnings rows are preserved
    // (the safety invariant: a transient outage must not silently empty the filter set)
    assertNotNull(refreshed, "stock refresh must succeed despite earnings-fetch outage")
    val rows = dsl
      .select(EARNINGS.FISCAL_DATE_ENDING, EARNINGS.REPORTED_EPS)
      .from(EARNINGS)
      .where(EARNINGS.STOCK_SYMBOL.eq(symbol))
      .orderBy(EARNINGS.FISCAL_DATE_ENDING.asc())
      .fetch()
    assertEquals(2, rows.size, "expected initial earnings still present")
    assertTrue(rows.any { it[EARNINGS.FISCAL_DATE_ENDING] == LocalDate.of(2024, 3, 31) })
    assertTrue(rows.any { it[EARNINGS.FISCAL_DATE_ENDING] == LocalDate.of(2024, 6, 30) })
  }

  private fun earning(fiscalDate: LocalDate, reportedEPS: Double): Earning =
    Earning(
      symbol = symbol,
      fiscalDateEnding = fiscalDate,
      reportedDate = fiscalDate.plusDays(20),
      reportedEPS = reportedEPS,
      estimatedEPS = reportedEPS - 0.05,
      surprise = 0.05,
      surprisePercentage = 3.45,
      reportTime = "AfterMarket",
    )
}
