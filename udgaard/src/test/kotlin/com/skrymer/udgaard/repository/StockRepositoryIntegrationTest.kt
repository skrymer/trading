package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
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
        val stock = Stock(
            symbol = "TQQQ",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
        )

        val quote1 = StockQuote(
            date = LocalDate.of(2024, 11, 25),
            openPrice = 100.0,
            closePrice = 102.0,
            high = 103.0,
            low = 99.0,
            volume = 1000000,
            closePriceEMA10 = 101.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 98.0,
            atr = 2.5
        )

        val quote2 = StockQuote(
            date = LocalDate.of(2024, 11, 26),
            openPrice = 102.0,
            closePrice = 104.0,
            high = 105.0,
            low = 101.0,
            volume = 1200000,
            closePriceEMA10 = 102.0,
            closePriceEMA20 = 101.0,
            closePriceEMA50 = 99.0,
            atr = 2.6
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
    fun `should retrieve stock with quotes using findBySymbolWithQuotes`() {
        // Given
        val stock = Stock(
            symbol = "QQQ",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
        )

        val quote1 = StockQuote(
            date = LocalDate.of(2024, 11, 27),
            openPrice = 400.0,
            closePrice = 402.0,
            high = 403.0,
            low = 399.0,
            volume = 50000000,
            closePriceEMA10 = 401.0,
            closePriceEMA20 = 400.0,
            closePriceEMA50 = 398.0,
            atr = 3.5
        )

        val quote2 = StockQuote(
            date = LocalDate.of(2024, 11, 28),
            openPrice = 402.0,
            closePrice = 405.0,
            high = 406.0,
            low = 401.0,
            volume = 55000000,
            closePriceEMA10 = 403.0,
            closePriceEMA20 = 401.0,
            closePriceEMA50 = 399.0,
            atr = 3.6
        )

        quote1.stock = stock
        quote1.symbol = "QQQ"
        quote2.stock = stock
        quote2.symbol = "QQQ"
        stock.quotes.add(quote1)
        stock.quotes.add(quote2)

        stockRepository.save(stock)
        entityManager.flush()
        entityManager.clear()

        // When - Retrieve stock with quotes eagerly loaded
        val foundStock = stockRepository.findBySymbolWithQuotes("QQQ")

        // Then
        assertNotNull(foundStock, "Stock should be found")
        assertEquals("QQQ", foundStock!!.symbol)
        assertEquals("XLK", foundStock.sectorSymbol)
        assertEquals(2, foundStock.quotes.size, "Should have 2 quotes")

        // Verify quotes are loaded and ordered by date
        val sortedQuotes = foundStock.quotes.sortedBy { it.date }
        assertEquals(LocalDate.of(2024, 11, 27), sortedQuotes[0].date)
        assertEquals(402.0, sortedQuotes[0].closePrice)
        assertEquals(LocalDate.of(2024, 11, 28), sortedQuotes[1].date)
        assertEquals(405.0, sortedQuotes[1].closePrice)
    }

    @Test
    fun `should retrieve multiple stocks with quotes`() {
        // Given - Create TQQQ
        val tqqq = Stock(
            symbol = "TQQQ",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
        )

        val tqqqQuote = StockQuote(
            date = LocalDate.of(2024, 11, 29),
            openPrice = 100.0,
            closePrice = 102.0,
            closePriceEMA10 = 101.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 98.0,
            atr = 2.5
        )
        tqqqQuote.stock = tqqq
        tqqqQuote.symbol = "TQQQ"
        tqqq.quotes.add(tqqqQuote)

        // Given - Create QQQ
        val qqq = Stock(
            symbol = "QQQ",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
        )

        val qqqQuote = StockQuote(
            date = LocalDate.of(2024, 11, 29),
            openPrice = 400.0,
            closePrice = 405.0,
            closePriceEMA10 = 403.0,
            closePriceEMA20 = 401.0,
            closePriceEMA50 = 399.0,
            atr = 3.5
        )
        qqqQuote.stock = qqq
        qqqQuote.symbol = "QQQ"
        qqq.quotes.add(qqqQuote)

        stockRepository.save(tqqq)
        stockRepository.save(qqq)
        entityManager.flush()
        entityManager.clear()

        // When - Retrieve both stocks with quotes
        val symbols = listOf("TQQQ", "QQQ")
        val foundStocks = symbols.mapNotNull { stockRepository.findBySymbolWithQuotes(it) }

        // Then
        assertEquals(2, foundStocks.size, "Should find both stocks")

        val tqqqFound = foundStocks.find { it.symbol == "TQQQ" }
        assertNotNull(tqqqFound, "TQQQ should be found")
        assertEquals(1, tqqqFound!!.quotes.size, "TQQQ should have 1 quote")
        assertEquals(102.0, tqqqFound.quotes[0].closePrice)

        val qqqFound = foundStocks.find { it.symbol == "QQQ" }
        assertNotNull(qqqFound, "QQQ should be found")
        assertEquals(1, qqqFound!!.quotes.size, "QQQ should have 1 quote")
        assertEquals(405.0, qqqFound.quotes[0].closePrice)
    }

    @Test
    fun `should handle stock with many quotes efficiently`() {
        // Given - Stock with 100 quotes
        val stock = Stock(
            symbol = "SPY",
            sectorSymbol = "SPY",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
        )

        // Create 100 daily quotes
        for (i in 1..100) {
            val quote = StockQuote(
                date = LocalDate.of(2024, 1, 1).plusDays(i.toLong()),
                openPrice = 450.0 + i,
                closePrice = 451.0 + i,
                closePriceEMA10 = 450.0 + i,
                closePriceEMA20 = 449.0 + i,
                closePriceEMA50 = 448.0 + i,
                atr = 2.0
            )
            quote.stock = stock
            quote.symbol = "SPY"
            stock.quotes.add(quote)
        }

        stockRepository.save(stock)
        entityManager.flush()
        entityManager.clear()

        // When - Retrieve with quotes
        val foundStock = stockRepository.findBySymbolWithQuotes("SPY")

        // Then
        assertNotNull(foundStock, "Stock should be found")
        assertEquals(100, foundStock!!.quotes.size, "Should have 100 quotes")

        // Verify first and last quotes
        val sortedQuotes = foundStock.quotes.sortedBy { it.date }
        assertEquals(LocalDate.of(2024, 1, 2), sortedQuotes.first().date)
        assertEquals(452.0, sortedQuotes.first().closePrice) // i=1: 451.0 + 1 = 452.0
        assertEquals(LocalDate.of(2024, 4, 10), sortedQuotes.last().date) // Jan 1 + 100 days
        assertEquals(551.0, sortedQuotes.last().closePrice) // i=100: 451.0 + 100 = 551.0
    }

    @Test
    fun `should return null when stock does not exist`() {
        // When
        val foundStock = stockRepository.findBySymbolWithQuotes("NONEXISTENT")

        // Then
        assertNull(foundStock, "Should return null for non-existent stock")
    }

    @Test
    fun `should handle stock with no quotes`() {
        // Given - Stock without quotes
        val stock = Stock(
            symbol = "EMPTY",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
        )

        stockRepository.save(stock)
        entityManager.flush()
        entityManager.clear()

        // When
        val foundStock = stockRepository.findBySymbolWithQuotes("EMPTY")

        // Then
        assertNotNull(foundStock, "Stock should be found")
        assertEquals(0, foundStock!!.quotes.size, "Should have 0 quotes")
    }

    @Test
    fun `should batch retrieve multiple stocks with single query using findAllBySymbolInWithQuotes`() {
        // Given - Create and save 3 stocks with quotes
        val stocks = listOf("AAPL", "GOOGL", "MSFT").map { symbol ->
            val stock = Stock(
                symbol = symbol,
                sectorSymbol = "XLK",
                quotes = mutableListOf(),
                orderBlocks = mutableListOf()
            )

            val quote = StockQuote(
                date = LocalDate.of(2024, 11, 29),
                closePrice = 100.0 + symbol.length, // Different price per stock
                closePriceEMA10 = 99.0,
                closePriceEMA20 = 98.0,
                closePriceEMA50 = 97.0,
                atr = 2.0
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
        val tqqq = Stock(
            symbol = "TQQQ",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
        )
        stockRepository.save(tqqq)

        val qqq = Stock(
            symbol = "QQQ",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
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
