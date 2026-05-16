package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.jooq.tables.references.EARNINGS
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for earnings persistence on the StockJooqRepository write path.
 *
 * Confirms (1) save inserts canonical earnings rows, (2) re-save replaces all prior
 * rows (delete-then-insert), and (3) the UNIQUE(stock_symbol, fiscal_date_ending)
 * invariant rejects duplicate-within-one-save attempts.
 *
 * Uses a synthetic symbol "TESTEARN" to avoid colliding with shared fixtures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockJooqRepositoryEarningsE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @Autowired
  private lateinit var stockRepository: StockJooqRepository

  private val symbol = "TESTEARN"
  private val quote = StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 100.0, volume = 1_000_000L)

  @Test
  fun `save with earnings inserts canonical rows`() {
    // Given a stock with two earnings reports on distinct fiscal dates
    val earnings = listOf(
      earning(fiscalDate = LocalDate.of(2024, 3, 31), reportedEPS = 1.50),
      earning(fiscalDate = LocalDate.of(2024, 6, 30), reportedEPS = 1.75),
    )

    // When the stock is saved
    stockRepository.save(Stock(symbol = symbol, quotes = listOf(quote), earnings = earnings))

    // Then both earnings rows are present in the DB with canonical column values
    val rows = dsl
      .selectFrom(EARNINGS)
      .where(EARNINGS.STOCK_SYMBOL.eq(symbol))
      .orderBy(EARNINGS.FISCAL_DATE_ENDING.asc())
      .fetch()
    assertEquals(2, rows.size)
    assertEquals(LocalDate.of(2024, 3, 31), rows[0][EARNINGS.FISCAL_DATE_ENDING])
    assertEquals(1.50, rows[0][EARNINGS.REPORTED_EPS]?.toDouble())
    assertEquals(LocalDate.of(2024, 6, 30), rows[1][EARNINGS.FISCAL_DATE_ENDING])
    assertEquals(1.75, rows[1][EARNINGS.REPORTED_EPS]?.toDouble())
  }

  @Test
  fun `re-save replaces all prior earnings rows`() {
    // Given a stock already has earnings persisted
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        earnings = listOf(
          earning(fiscalDate = LocalDate.of(2023, 12, 31), reportedEPS = 1.20),
          earning(fiscalDate = LocalDate.of(2024, 3, 31), reportedEPS = 1.50),
        ),
      ),
    )

    // When the stock is re-saved with a different earnings set
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        earnings = listOf(earning(fiscalDate = LocalDate.of(2024, 9, 30), reportedEPS = 2.00)),
      ),
    )

    // Then only the fresh row remains — prior rows are wiped
    val dates = dsl
      .select(EARNINGS.FISCAL_DATE_ENDING)
      .from(EARNINGS)
      .where(EARNINGS.STOCK_SYMBOL.eq(symbol))
      .fetch(EARNINGS.FISCAL_DATE_ENDING)
    assertEquals(listOf(LocalDate.of(2024, 9, 30)), dates)
  }

  @Test
  fun `UNIQUE constraint rejects duplicate symbol-and-fiscal-date in one save`() {
    // Given a stock whose earnings list contains two rows with the same fiscal date
    val sameFiscalDate = LocalDate.of(2024, 12, 31)
    val duplicateEarnings = listOf(
      earning(fiscalDate = sameFiscalDate, reportedEPS = 1.10),
      earning(fiscalDate = sameFiscalDate, reportedEPS = 1.20),
    )

    // When save is attempted
    val ex = assertThrows<DuplicateKeyException> {
      stockRepository.save(Stock(symbol = symbol, quotes = listOf(quote), earnings = duplicateEarnings))
    }

    // Then the named UNIQUE constraint is what fails (not some incidental other duplicate)
    val combined = generateSequence<Throwable>(ex) { it.cause }.joinToString { it.message.orEmpty() }
    assertTrue(
      combined.contains("uq_earnings_symbol_fiscal_date"),
      "expected uq_earnings_symbol_fiscal_date violation, got: $combined",
    )
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
