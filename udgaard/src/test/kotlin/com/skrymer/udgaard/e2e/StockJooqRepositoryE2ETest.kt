package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the StockJooqRepository.save -> findBySymbol round-trip for every
 * StockQuote field. save() inserts each quote from its mapped record, so the
 * mapper is the single source of truth for the written columns; this test fails
 * if toPojo/toDomain and the stock_quotes table drift out of sync. Each field
 * gets a distinct value so a dropped or swapped binding surfaces as a specific
 * assertion failure rather than passing by coincidence.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockJooqRepositoryE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var stockRepository: StockJooqRepository

  @Test
  fun `save persists every quote field and reads it back unchanged`() {
    // Given a quote with every field set to a distinct, non-default value
    val symbol = "TESTSMA_FULL"
    val quote = StockQuote(
      symbol = symbol,
      date = LocalDate.of(2030, 2, 1),
      closePrice = 100.1,
      openPrice = 99.2,
      high = 105.3,
      low = 95.4,
      volume = 1_234_567L,
      closePriceEMA5 = 101.5,
      closePriceEMA10 = 102.6,
      closePriceEMA20 = 103.7,
      closePriceEMA50 = 104.8,
      closePriceEMA100 = 105.9,
      ema200 = 106.1,
      atr = 3.25,
      adx = 27.5,
      trend = "Uptrend",
      donchianUpperBand = 110.2,
      sma50 = 98.5,
      sma150 = 90.25,
      sma200 = 88.0,
      high52Week = 120.0,
      low52Week = 70.0,
    )

    // When the stock is saved and reloaded through the public load path
    stockRepository.save(Stock(symbol = symbol, sectorSymbol = "XLK", quotes = listOf(quote)))
    val saved = requireNotNull(stockRepository.findBySymbol(symbol)) { "stock not found" }.quotes.single()

    // Then every field survives the round-trip
    assertEquals(LocalDate.of(2030, 2, 1), saved.date)
    assertEquals(100.1, saved.closePrice)
    assertEquals(99.2, saved.openPrice)
    assertEquals(105.3, saved.high)
    assertEquals(95.4, saved.low)
    assertEquals(1_234_567L, saved.volume)
    assertEquals(101.5, saved.closePriceEMA5)
    assertEquals(102.6, saved.closePriceEMA10)
    assertEquals(103.7, saved.closePriceEMA20)
    assertEquals(104.8, saved.closePriceEMA50)
    assertEquals(105.9, saved.closePriceEMA100)
    assertEquals(106.1, saved.ema200)
    assertEquals(3.25, saved.atr)
    assertEquals(27.5, saved.adx)
    assertEquals("Uptrend", saved.trend)
    assertEquals(110.2, saved.donchianUpperBand)
    assertEquals(98.5, saved.sma50)
    assertEquals(90.25, saved.sma150)
    assertEquals(88.0, saved.sma200)
    assertEquals(120.0, saved.high52Week)
    assertEquals(70.0, saved.low52Week)
  }

  @Test
  fun `save preserves null indicators as null`() {
    // Given a quote with no computed SMA / 52-week / ADX values (insufficient history)
    val symbol = "TESTSMA_NULL"
    val quote = StockQuote(
      symbol = symbol,
      date = LocalDate.of(2030, 3, 1),
      closePrice = 100.0,
      openPrice = 100.0,
      high = 105.0,
      low = 95.0,
      volume = 1_000_000L,
    )

    // When saved and reloaded
    stockRepository.save(Stock(symbol = symbol, sectorSymbol = "XLK", quotes = listOf(quote)))
    val saved = requireNotNull(stockRepository.findBySymbol(symbol)) { "stock not found" }.quotes.single()

    // Then the nullable indicators read back null, not 0.0
    assertNull(saved.sma50)
    assertNull(saved.sma150)
    assertNull(saved.sma200)
    assertNull(saved.high52Week)
    assertNull(saved.low52Week)
    assertNull(saved.adx)
    assertNull(saved.trend)
  }
}
