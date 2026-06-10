package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.model.Fundamental
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Integration coverage for persisting point-in-time fundamentals through `StockJooqRepository`,
 * focused on the numeric-column width: EODHD bad-print line items can exceed the old DECIMAL(19,4)
 * ceiling and must not abort the symbol's batch upsert.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockJooqRepositoryFundamentalsE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var stockRepository: StockJooqRepository

  private val symbol = "FUNDBIG"
  private val quote = StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 100.0, volume = 1_000_000L)

  @Test
  fun `findFundamentals round-trips a line item above the old DECIMAL 19 4 ceiling`() {
    // Given an EODHD bad-print value (2.5e16) above DECIMAL(19,4)'s ~1e15 cap, alongside good fields —
    // previously the overflow aborted the whole batch and the symbol saved no fundamentals at all
    val badPrint = 2.5e16
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        fundamentals = listOf(
          Fundamental(
            symbol = symbol,
            fiscalDateEnding = LocalDate.of(2018, 9, 30),
            filingDate = LocalDate.of(2018, 11, 28),
            grossProfit = 20000.0,
            costOfRevenue = badPrint,
            totalAssets = 335234000.0,
          ),
        ),
      ),
    )

    // When
    val result = stockRepository.findFundamentals(symbol).single()

    // Then the oversized value round-trips and the good fields persist alongside it
    assertEquals(badPrint, result.costOfRevenue)
    assertEquals(20000.0, result.grossProfit)
    assertEquals(335234000.0, result.totalAssets)
  }
}
