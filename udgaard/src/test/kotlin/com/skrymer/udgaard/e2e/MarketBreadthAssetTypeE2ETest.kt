package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.jooq.tables.references.MARKET_BREADTH_DAILY
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
import kotlin.test.assertEquals

/**
 * Confirms market breadth holds a stock with a NULL/unclassified asset_type OUT of the measurement
 * universe (fail-closed, ADR 0026) — consistent with the stocks-derived universe read path, which
 * also excludes null. A null arises from an asset-type lookup that failed at ingestion (e.g. enum
 * drift); it must not silently land in the frozen breadth series. Uses far-future bars on a unique
 * date so it doesn't perturb shared fixtures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketBreadthAssetTypeE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @Autowired
  private lateinit var marketBreadthRepository: MarketBreadthRepository

  private val breadthDate = LocalDate.of(2031, 3, 3)

  @BeforeAll
  fun setupTestData() {
    // One null-asset_type stock in an uptrend, one STOCK-typed stock in a downtrend.
    insertStock("BRNULL", assetType = null)
    insertStock("BRSTOCK", assetType = "STOCK")
    insertQuote("BRNULL", trend = "Uptrend")
    insertQuote("BRSTOCK", trend = "Downtrend")
  }

  @AfterAll
  fun cleanup() {
    // refreshBreadthDaily() materialises a breadth row at the far-future breadthDate; without removing
    // it (and the BRNULL/BRSTOCK quotes that seed it), it becomes the max breadth date and would poison
    // currentMarketDate for any later test in the shared container (e.g. ScannerScanE2ETest).
    dsl.deleteFrom(MARKET_BREADTH_DAILY).where(MARKET_BREADTH_DAILY.QUOTE_DATE.eq(breadthDate)).execute()
    dsl.deleteFrom(STOCK_QUOTES).where(STOCK_QUOTES.STOCK_SYMBOL.`in`("BRNULL", "BRSTOCK")).execute()
    dsl.deleteFrom(STOCKS).where(STOCKS.SYMBOL.`in`("BRNULL", "BRSTOCK")).execute()
  }

  @Test
  fun `market breadth excludes a null asset_type stock from the universe`() {
    // When breadth is recomputed
    marketBreadthRepository.refreshBreadthDaily()

    // Then only the STOCK-typed name is in the denominator: the uptrend null-asset_type name is held
    // out, leaving just the downtrend STOCK = 0 of 1 in uptrend = 0%. (Were null still counted as a
    // STOCK, the uptrend null name would lift breadth to 50%.)
    val breadth = dsl
      .select(MARKET_BREADTH_DAILY.BREADTH_PERCENT)
      .from(MARKET_BREADTH_DAILY)
      .where(MARKET_BREADTH_DAILY.QUOTE_DATE.eq(breadthDate))
      .fetchOne(MARKET_BREADTH_DAILY.BREADTH_PERCENT)

    assertEquals(0, BigDecimal("0.0000").compareTo(breadth))
  }

  private fun insertStock(symbol: String, assetType: String?) {
    dsl
      .insertInto(STOCKS)
      .set(STOCKS.SYMBOL, symbol)
      .set(STOCKS.ASSET_TYPE, assetType)
      .onConflict(STOCKS.SYMBOL)
      .doNothing()
      .execute()
  }

  private fun insertQuote(symbol: String, trend: String) {
    dsl
      .insertInto(STOCK_QUOTES)
      .set(STOCK_QUOTES.STOCK_SYMBOL, symbol)
      .set(STOCK_QUOTES.QUOTE_DATE, breadthDate)
      .set(STOCK_QUOTES.CLOSE_PRICE, BigDecimal.valueOf(100.0))
      .set(STOCK_QUOTES.OPEN_PRICE, BigDecimal.valueOf(100.0))
      .set(STOCK_QUOTES.HIGH_PRICE, BigDecimal.valueOf(100.0))
      .set(STOCK_QUOTES.LOW_PRICE, BigDecimal.valueOf(100.0))
      .set(STOCK_QUOTES.VOLUME, 1_000_000L)
      .set(STOCK_QUOTES.TREND, trend)
      .execute()
  }
}
