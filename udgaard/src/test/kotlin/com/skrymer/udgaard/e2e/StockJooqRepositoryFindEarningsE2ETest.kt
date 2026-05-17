package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for `StockJooqRepository.findEarnings(symbol)` — the lightweight
 * read used by the ingestion service to recover prior earnings when a Midgaard
 * earnings fetch fails (Q4 fallback in the earnings-ingestion design).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockJooqRepositoryFindEarningsE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var stockRepository: StockJooqRepository

  private val symbol = "TESTRD"
  private val otherSymbol = "TESTRD2"
  private val quote = StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 100.0, volume = 1_000_000L)

  @Test
  fun `findEarnings returns empty when no rows exist for symbol`() {
    // Given a symbol that has no earnings persisted
    val unseededSymbol = "NEVERSEEN"

    // When findEarnings is called
    val result = stockRepository.findEarnings(unseededSymbol)

    // Then the result is empty — never null, mirroring the empty-vs-unknown contract
    assertTrue(result.isEmpty())
  }

  @Test
  fun `findEarnings returns rows ordered by fiscal date ascending`() {
    // Given a symbol with earnings inserted out of order
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        earnings = listOf(
          earning(symbol, fiscalDate = LocalDate.of(2024, 9, 30)),
          earning(symbol, fiscalDate = LocalDate.of(2024, 3, 31)),
          earning(symbol, fiscalDate = LocalDate.of(2024, 6, 30)),
        ),
      ),
    )

    // When findEarnings is called
    val result = stockRepository.findEarnings(symbol)

    // Then rows are ordered by fiscal_date_ending asc
    assertEquals(
      listOf(
        LocalDate.of(2024, 3, 31),
        LocalDate.of(2024, 6, 30),
        LocalDate.of(2024, 9, 30),
      ),
      result.map { it.fiscalDateEnding },
    )
  }

  @Test
  fun `findEarnings does not return earnings for other symbols`() {
    // Given two symbols both have earnings persisted
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        earnings = listOf(earning(symbol, fiscalDate = LocalDate.of(2024, 12, 31))),
      ),
    )
    stockRepository.save(
      Stock(
        symbol = otherSymbol,
        quotes = listOf(quote),
        earnings = listOf(earning(otherSymbol, fiscalDate = LocalDate.of(2024, 12, 31))),
      ),
    )

    // When findEarnings is called for one symbol
    val result = stockRepository.findEarnings(symbol)

    // Then only that symbol's rows come back
    assertTrue(result.all { it.symbol == symbol }, "expected only $symbol rows, got: ${result.map { it.symbol }}")
  }

  private fun earning(forSymbol: String, fiscalDate: LocalDate): Earning =
    Earning(
      symbol = forSymbol,
      fiscalDateEnding = fiscalDate,
      reportedDate = fiscalDate.plusDays(20),
      reportedEPS = 1.50,
      estimatedEPS = 1.45,
      surprise = 0.05,
      surprisePercentage = 3.45,
      reportTime = "AfterMarket",
    )
}
