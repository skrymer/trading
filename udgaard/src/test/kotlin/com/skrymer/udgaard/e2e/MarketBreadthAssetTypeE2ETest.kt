package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.jooq.tables.references.MARKET_BREADTH_DAILY
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

/**
 * Confirms market breadth treats a stock with a NULL asset_type as a STOCK — consistent with
 * the stocks-derived universe read path, which also defaults null to STOCK (ADR 0011). A null
 * arises when a symbol's asset-type lookup failed at ingestion; it must not silently vanish
 * from breadth. Uses far-future bars on a unique date so it doesn't perturb shared fixtures.
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

  @Test
  fun `market breadth counts a null asset_type stock as a STOCK`() {
    // When breadth is recomputed
    marketBreadthRepository.refreshBreadthDaily()

    // Then the null-asset_type stock is in the denominator: 1 of 2 in uptrend = 50%
    // (were it excluded, only the downtrend STOCK would count and breadth would be 0%)
    val breadth = dsl
      .select(MARKET_BREADTH_DAILY.BREADTH_PERCENT)
      .from(MARKET_BREADTH_DAILY)
      .where(MARKET_BREADTH_DAILY.QUOTE_DATE.eq(breadthDate))
      .fetchOne(MARKET_BREADTH_DAILY.BREADTH_PERCENT)

    assertEquals(0, BigDecimal("50.0000").compareTo(breadth))
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
