package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.model.Fundamental
import com.skrymer.udgaard.data.model.Split
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Integration coverage for the point-in-time market-cap primitive's persistence (ADR 0027): a quote's
 * raw close, a fundamental's split-adjusted shares, and a symbol's splits must all round-trip through
 * `StockJooqRepository` so a reloaded [Stock] computes the same cap the in-memory accessor does.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketCapPrimitiveE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var stockRepository: StockJooqRepository

  private val asOf = LocalDate.of(2020, 6, 30)

  // A name with a pre-split raw close ($364, stored adjusted $90), 16B current-basis shares filed
  // 2020-02-01, and a 4:1 split effective 2020-08-31 → cap as of 2020-06-30 = (364 / 4) × 16B = 1.456e12.
  private fun appleLikeStock(symbol: String) =
    Stock(
      symbol = symbol,
      quotes = listOf(StockQuote(symbol = symbol, date = asOf, closePrice = 90.0, rawClose = 364.0, volume = 1_000_000L)),
      fundamentals = listOf(
        Fundamental(
          symbol = symbol,
          fiscalDateEnding = LocalDate.of(2019, 12, 31),
          filingDate = LocalDate.of(2020, 2, 1),
          sharesOutstanding = 16_000_000_000L,
        ),
      ),
      splits = listOf(Split(symbol = symbol, exDate = LocalDate.of(2020, 8, 31), ratio = 4.0)),
    )

  @Test
  fun `save round-trips raw close, shares and splits so a reloaded stock computes the same cap`() {
    // Given a saved stock carrying all three cap inputs
    stockRepository.save(appleLikeStock("CAPSAVE"))

    // When reloaded from the database
    val reloaded = stockRepository.findBySymbol("CAPSAVE")!!

    // Then each input round-trips
    assertEquals(364.0, reloaded.quotes.single().rawClose)
    assertEquals(16_000_000_000L, reloaded.fundamentals.single().sharesOutstanding)
    assertEquals(listOf(Split("CAPSAVE", LocalDate.of(2020, 8, 31), 4.0)), reloaded.splits)
    // And the end-to-end cap matches the in-memory construct (c)
    assertEquals(1_456_000_000_000.0, reloaded.marketCapAsOf(asOf)!!, 1.0)
  }

  @Test
  fun `batchInsert persists fundamentals shares and splits for the bulk-refresh path`() {
    // Given the bulk-refresh path (the realistic re-ingest route) persisting the same stock
    stockRepository.batchInsert(listOf(appleLikeStock("CAPBATCH")))

    // When reloaded
    val reloaded = stockRepository.findBySymbol("CAPBATCH")!!

    // Then the batch path persists fundamentals (shares) and splits too — not only the single save path —
    // so the cap is defined after a bulk refresh
    assertEquals(16_000_000_000L, reloaded.fundamentals.single().sharesOutstanding)
    assertEquals(listOf(Split("CAPBATCH", LocalDate.of(2020, 8, 31), 4.0)), reloaded.splits)
    assertEquals(1_456_000_000_000.0, reloaded.marketCapAsOf(asOf)!!, 1.0)
  }

  @Test
  fun `a bulk re-ingest of the same symbol does not violate the fundamentals or splits unique constraints`() {
    // Given a symbol already ingested via the bulk-refresh route (batchDelete then batchInsert)
    stockRepository.batchDelete(listOf("CAPREINGEST"))
    stockRepository.batchInsert(listOf(appleLikeStock("CAPREINGEST")))

    // When the same symbol is re-ingested the same way (the realistic re-refresh) — the cascade on the
    // batchDelete must clear the prior fundamentals + splits so the re-insert doesn't collide with
    // uq_fundamentals_symbol_fiscal_date / uq_stock_splits_symbol_ex_date
    stockRepository.batchDelete(listOf("CAPREINGEST"))
    stockRepository.batchInsert(listOf(appleLikeStock("CAPREINGEST")))

    // Then exactly one fundamental + one split survive and the cap is still defined
    val reloaded = stockRepository.findBySymbol("CAPREINGEST")!!
    assertEquals(1, reloaded.fundamentals.size)
    assertEquals(1, reloaded.splits.size)
    assertEquals(1_456_000_000_000.0, reloaded.marketCapAsOf(asOf)!!, 1.0)
  }
}
