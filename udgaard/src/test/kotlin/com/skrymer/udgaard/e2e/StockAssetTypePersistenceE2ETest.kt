package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Confirms a stock's asset type survives the save → load roundtrip now that the trading
 * universe is the stocks table (ADR 0011) — the asset_type formerly lived on the dropped
 * symbols table. Uses a synthetic "TESTAT" symbol so it doesn't collide with shared fixtures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockAssetTypePersistenceE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var stockRepository: StockJooqRepository

  private val symbol = "TESTAT"

  @BeforeAll
  fun setupTestData() {
    val quote = StockQuote(symbol = symbol, date = LocalDate.of(2030, 2, 3), closePrice = 100.0, volume = 1_000_000L)
    stockRepository.save(Stock(symbol = symbol, quotes = listOf(quote), assetType = AssetType.LEVERAGED_ETF))
  }

  @Test
  fun `findAllSymbolRecords returns the persisted asset type`() {
    // When the universe is read back from the stocks table
    val record = stockRepository.findAllSymbolRecords().single { it.symbol == symbol }

    // Then the asset type stamped at save time survives the roundtrip
    assertEquals(AssetType.LEVERAGED_ETF, record.assetType)
  }
}
