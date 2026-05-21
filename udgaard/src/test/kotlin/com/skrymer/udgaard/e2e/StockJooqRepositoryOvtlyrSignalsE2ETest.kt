package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.model.OvtlyrSignal
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for Ovtlyr signal persistence on the StockJooqRepository read/write path.
 *
 * Confirms signals round-trip through save/load so the backtest path — which loads Stock
 * aggregates from this database — can evaluate the Ovtlyr signal conditions.
 *
 * Uses a synthetic symbol "TESTOVTLYR" to avoid colliding with shared fixtures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockJooqRepositoryOvtlyrSignalsE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var stockRepository: StockJooqRepository

  private val symbol = "TESTOVTLYR"
  private val quote = StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 100.0, volume = 1_000_000L)

  @Test
  fun `findBySymbol round-trips a saved Ovtlyr signal`() {
    // Given: a stock with one Ovtlyr BUY signal
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        ovtlyrSignals = listOf(OvtlyrSignal(symbol, LocalDate.of(2024, 1, 4), OvtlyrSignalType.BUY)),
      ),
    )

    // When: the stock is loaded back
    val loaded = stockRepository.findBySymbol(symbol)

    // Then: the Ovtlyr signal survives the round-trip
    assertEquals(
      listOf(OvtlyrSignal(symbol, LocalDate.of(2024, 1, 4), OvtlyrSignalType.BUY)),
      loaded?.ovtlyrSignals,
    )
  }

  @Test
  fun `findBySymbols round-trips Ovtlyr signals ordered by date`() {
    // Given: a stock with a BUY then a SELL signal
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        ovtlyrSignals =
          listOf(
            OvtlyrSignal(symbol, LocalDate.of(2024, 1, 4), OvtlyrSignalType.BUY),
            OvtlyrSignal(symbol, LocalDate.of(2024, 2, 10), OvtlyrSignalType.SELL),
          ),
      ),
    )

    // When: the stock is loaded via the bulk path the backtest engine uses
    val loaded = stockRepository.findBySymbols(listOf(symbol)).single()

    // Then: both signals come back in signal-date order
    assertEquals(
      listOf(
        OvtlyrSignal(symbol, LocalDate.of(2024, 1, 4), OvtlyrSignalType.BUY),
        OvtlyrSignal(symbol, LocalDate.of(2024, 2, 10), OvtlyrSignalType.SELL),
      ),
      loaded.ovtlyrSignals,
    )
  }

  @Test
  fun `re-save replaces all prior Ovtlyr signal rows`() {
    // Given: a stock already has Ovtlyr signals persisted
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        ovtlyrSignals =
          listOf(
            OvtlyrSignal(symbol, LocalDate.of(2023, 12, 1), OvtlyrSignalType.BUY),
            OvtlyrSignal(symbol, LocalDate.of(2024, 1, 4), OvtlyrSignalType.SELL),
          ),
      ),
    )

    // When: the stock is re-saved with a different signal set
    stockRepository.save(
      Stock(
        symbol = symbol,
        quotes = listOf(quote),
        ovtlyrSignals = listOf(OvtlyrSignal(symbol, LocalDate.of(2024, 3, 15), OvtlyrSignalType.BUY)),
      ),
    )

    // Then: only the fresh signal remains — prior rows are wiped
    assertEquals(
      listOf(OvtlyrSignal(symbol, LocalDate.of(2024, 3, 15), OvtlyrSignalType.BUY)),
      stockRepository.findBySymbol(symbol)?.ovtlyrSignals,
    )
  }

  @Test
  fun `UNIQUE constraint rejects duplicate symbol-and-date in one save`() {
    // Given: a stock whose signal list has two rows on the same date
    val sameDate = LocalDate.of(2024, 5, 1)
    val duplicates =
      listOf(
        OvtlyrSignal(symbol, sameDate, OvtlyrSignalType.BUY),
        OvtlyrSignal(symbol, sameDate, OvtlyrSignalType.SELL),
      )

    // When: save is attempted
    val ex =
      assertThrows<DuplicateKeyException> {
        stockRepository.save(Stock(symbol = symbol, quotes = listOf(quote), ovtlyrSignals = duplicates))
      }

    // Then: the named UNIQUE constraint is what fails
    val combined = generateSequence<Throwable>(ex) { it.cause }.joinToString { it.message.orEmpty() }
    assertTrue(
      combined.contains("uq_ovtlyr_signals_symbol_date"),
      "expected uq_ovtlyr_signals_symbol_date violation, got: $combined",
    )
  }

  @Test
  fun `batchSave round-trips Ovtlyr signals`() {
    // Given: a stock with an Ovtlyr signal saved via the bulk ingestion write path
    stockRepository.batchSave(
      listOf(
        Stock(
          symbol = symbol,
          quotes = listOf(quote),
          ovtlyrSignals = listOf(OvtlyrSignal(symbol, LocalDate.of(2024, 4, 8), OvtlyrSignalType.SELL)),
        ),
      ),
    )

    // When / Then: the signal survives the round-trip
    assertEquals(
      listOf(OvtlyrSignal(symbol, LocalDate.of(2024, 4, 8), OvtlyrSignalType.SELL)),
      stockRepository.findBySymbol(symbol)?.ovtlyrSignals,
    )
  }
}
