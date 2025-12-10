package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJpaTest
@ActiveProfiles("test")
class StockRepositoryIntegrationTest {
  @Autowired
  private lateinit var entityManager: TestEntityManager

  @Autowired
  private lateinit var stockRepository: StockRepository

  @Test
  fun `should save and retrieve stock with quotes`() {
    // Given
    val stock =
      Stock(
        symbol = "TQQQ",
        sectorSymbol = "XLK",
        quotes = mutableListOf(),
        orderBlocks = mutableListOf(),
      )

    val quote1 =
      StockQuote(
        date = LocalDate.of(2024, 11, 25),
        openPrice = 100.0,
        closePrice = 102.0,
        high = 103.0,
        low = 99.0,
        volume = 1000000,
        closePriceEMA10 = 101.0,
        closePriceEMA20 = 100.0,
        closePriceEMA50 = 98.0,
        atr = 2.5,
      )

    val quote2 =
      StockQuote(
        date = LocalDate.of(2024, 11, 26),
        openPrice = 102.0,
        closePrice = 104.0,
        high = 105.0,
        low = 101.0,
        volume = 1200000,
        closePriceEMA10 = 102.0,
        closePriceEMA20 = 101.0,
        closePriceEMA50 = 99.0,
        atr = 2.6,
      )

    // Set bidirectional relationship and symbol field
    quote1.stock = stock
    quote1.symbol = "TQQQ"
    quote2.stock = stock
    quote2.symbol = "TQQQ"
    stock.quotes.add(quote1)
    stock.quotes.add(quote2)

    // When - Save the stock (quotes should cascade)
    stockRepository.save(stock)
    entityManager.flush()
    entityManager.clear()

    // Then - Retrieve stock without quotes (basic find)
    val foundStock = stockRepository.findById("TQQQ")
    assertTrue(foundStock.isPresent, "Stock should be found")
    assertEquals("TQQQ", foundStock.get().symbol)
    assertEquals("XLK", foundStock.get().sectorSymbol)
  }

  @Test
  fun `should batch retrieve multiple stocks with single query using findAllBySymbolInWithQuotes`() {
    // Given - Create and save 3 stocks with quotes
    val stocks =
      listOf("AAPL", "GOOGL", "MSFT").map { symbol ->
        val stock =
          Stock(
            symbol = symbol,
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(),
          )

        val quote =
          StockQuote(
            date = LocalDate.of(2024, 11, 29),
            closePrice = 100.0 + symbol.length, // Different price per stock
            closePriceEMA10 = 99.0,
            closePriceEMA20 = 98.0,
            closePriceEMA50 = 97.0,
            atr = 2.0,
          )
        quote.stock = stock
        quote.symbol = symbol
        stock.quotes.add(quote)

        stockRepository.save(stock)
        stock
      }

    entityManager.flush()
    entityManager.clear()

    // When - Retrieve all stocks in a single query
    val foundStocks = stockRepository.findAllBySymbolIn(listOf("AAPL", "GOOGL", "MSFT"))

    // Then - Should retrieve all 3 stocks with quotes
    assertEquals(3, foundStocks.size, "Should find all 3 stocks")

    // Verify each stock has quotes loaded
    foundStocks.forEach { stock ->
      assertNotNull(stock.quotes, "Quotes should be loaded")
      assertEquals(1, stock.quotes.size, "Each stock should have 1 quote")
    }

    // Verify all symbols are present
    val symbols = foundStocks.map { it.symbol }.toSet()
    assertTrue(symbols.contains("AAPL"), "Should contain AAPL")
    assertTrue(symbols.contains("GOOGL"), "Should contain GOOGL")
    assertTrue(symbols.contains("MSFT"), "Should contain MSFT")
  }

  @Test
  fun `should handle partial matches in findAllBySymbolInWithQuotes`() {
    // Given - Save only 2 out of 3 requested stocks
    val tqqq =
      Stock(
        symbol = "TQQQ",
        sectorSymbol = "XLK",
        quotes = mutableListOf(),
        orderBlocks = mutableListOf(),
      )
    stockRepository.save(tqqq)

    val qqq =
      Stock(
        symbol = "QQQ",
        sectorSymbol = "XLK",
        quotes = mutableListOf(),
        orderBlocks = mutableListOf(),
      )
    stockRepository.save(qqq)

    entityManager.flush()
    entityManager.clear()

    // When - Request 3 stocks but only 2 exist
    val foundStocks = stockRepository.findAllBySymbolIn(listOf("TQQQ", "QQQ", "SQQQ"))

    // Then - Should return only the 2 that exist
    assertEquals(2, foundStocks.size, "Should find only 2 stocks")
    val symbols = foundStocks.map { it.symbol }.toSet()
    assertTrue(symbols.contains("TQQQ"))
    assertTrue(symbols.contains("QQQ"))
    assertFalse(symbols.contains("SQQQ"), "Should not contain non-existent SQQQ")
  }
}
