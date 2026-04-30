package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.jooq.tables.references.STOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the volume=0 quote filter applied at the
 * StockJooqRepository load boundary. Confirms upstream-provider synthetic
 * filler bars (zero-volume rows that repeat the prior close) are excluded
 * from both findBySymbol and findBySymbols. See VCP_STRATEGY_V2.md §3.9.
 *
 * Uses a synthetic symbol "TESTVF" with hand-crafted bars so it doesn't
 * collide with any shared fixture loaded by other E2E tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockJooqRepositoryVolumeFilterE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @Autowired
  private lateinit var stockRepository: StockJooqRepository

  private val symbol = "TESTVF"
  private val realBar1 = LocalDate.of(2030, 1, 2)
  private val syntheticBar = LocalDate.of(2030, 1, 3)
  private val realBar2 = LocalDate.of(2030, 1, 6)
  private val realBar3 = LocalDate.of(2030, 1, 7)
  private val cutoff = LocalDate.of(2030, 1, 5)

  @BeforeAll
  fun setupTestData() {
    dsl
      .insertInto(STOCKS)
      .set(STOCKS.SYMBOL, symbol)
      .set(STOCKS.SECTOR, "XLK")
      .onConflict(STOCKS.SYMBOL)
      .doNothing()
      .execute()

    insertQuote(realBar1, close = 100.0, volume = 1_000_000L)
    insertQuote(syntheticBar, close = 100.0, volume = 0L) // synthetic-filler bar
    insertQuote(realBar2, close = 102.0, volume = 1_200_000L)
    insertQuote(realBar3, close = 103.0, volume = 1_500_000L)
  }

  @Test
  fun `findBySymbol excludes volume=0 bars`() {
    // When
    val stock = requireNotNull(stockRepository.findBySymbol(symbol)) { "stock not found" }

    // Then: synthetic bar is filtered out, real bars are loaded
    val dates = stock.quotes.map { it.date }
    assertTrue(syntheticBar !in dates, "Volume=0 bar should be filtered out")
    assertTrue(realBar1 in dates && realBar2 in dates && realBar3 in dates, "Real bars must load")
    assertEquals(3, stock.quotes.size)
  }

  @Test
  fun `findBySymbols excludes volume=0 bars in batch load`() {
    // When
    val stocks = stockRepository.findBySymbols(listOf(symbol))

    // Then
    assertEquals(1, stocks.size)
    val loaded = stocks.single()
    assertTrue(syntheticBar !in loaded.quotes.map { it.date }, "Volume=0 bar should be filtered out")
    assertEquals(3, loaded.quotes.size)
  }

  @Test
  fun `findBySymbol respects quotesAfter alongside the volume filter`() {
    // When: cutoff between syntheticBar and realBar2 — only realBar2 + realBar3 should load
    val stock = requireNotNull(stockRepository.findBySymbol(symbol, quotesAfter = cutoff)) { "stock not found" }

    // Then: pre-fix the cutoff was silently dropped by the jOOQ chain bug
    // (and()-return value discarded), so all bars would have loaded. Now
    // cutoff is honoured AND volume=0 bars excluded.
    val dates = stock.quotes.map { it.date }
    assertTrue(dates.all { !it.isBefore(cutoff) }, "All quotes must respect the after-cutoff")
    assertEquals(2, stock.quotes.size)
    assertTrue(realBar2 in dates && realBar3 in dates)
  }

  private fun insertQuote(date: LocalDate, close: Double, volume: Long) {
    dsl
      .insertInto(STOCK_QUOTES)
      .set(STOCK_QUOTES.STOCK_SYMBOL, symbol)
      .set(STOCK_QUOTES.QUOTE_DATE, date)
      .set(STOCK_QUOTES.CLOSE_PRICE, BigDecimal.valueOf(close))
      .set(STOCK_QUOTES.OPEN_PRICE, BigDecimal.valueOf(close))
      .set(STOCK_QUOTES.HIGH_PRICE, BigDecimal.valueOf(close))
      .set(STOCK_QUOTES.LOW_PRICE, BigDecimal.valueOf(close))
      .set(STOCK_QUOTES.VOLUME, volume)
      .execute()
  }
}
