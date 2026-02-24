package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.config.StockRefreshProperties
import com.skrymer.udgaard.data.factory.StockFactory
import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

class StockIngestionRetryTest {
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var stockProvider: StockProvider
  private lateinit var stockFactory: StockFactory
  private lateinit var orderBlockCalculator: OrderBlockCalculator
  private lateinit var symbolService: SymbolService
  private lateinit var sectorBreadthService: SectorBreadthService
  private lateinit var marketBreadthService: MarketBreadthService
  private lateinit var marketBreadthRepository: MarketBreadthRepository

  private val retryProperties = StockRefreshProperties.RetryProperties(
    maxAttempts = 3,
    initialDelayMs = 10, // Short delays for tests
    multiplier = 2.0,
    maxDelayMs = 100,
  )

  private val refreshProperties = StockRefreshProperties(
    retry = retryProperties,
  )

  private lateinit var service: StockIngestionService

  @BeforeEach
  fun setup() {
    stockRepository = mock()
    stockProvider = mock()
    stockFactory = mock()
    orderBlockCalculator = mock()
    symbolService = mock()
    sectorBreadthService = mock()
    marketBreadthService = mock()
    marketBreadthRepository = mock()

    service = StockIngestionService(
      stockRepository = stockRepository,
      stockProvider = stockProvider,
      stockFactory = stockFactory,
      orderBlockCalculator = orderBlockCalculator,
      symbolService = symbolService,
      sectorBreadthService = sectorBreadthService,
      marketBreadthService = marketBreadthService,
      marketBreadthRepository = marketBreadthRepository,
      refreshProperties = refreshProperties,
    )
  }

  private fun stubSuccessfulFetch() {
    // Suspend functions: use stub { onBlocking {} }
    stockProvider.stub {
      onBlocking { getDailyAdjustedTimeSeries(any(), any(), any()) } doReturn emptyList()
    }
    // Non-suspend functions — use Mockito.doReturn to avoid null-matching issues
    Mockito
      .doReturn(emptyList<OrderBlock>())
      .`when`(orderBlockCalculator)
      .calculateOrderBlocks(any(), any(), any(), any(), any())
    Mockito
      .doReturn(emptyList<StockQuote>())
      .`when`(stockFactory)
      .enrichQuotes(any(), any())
    Mockito
      .doReturn(Stock(symbol = "AAPL"))
      .`when`(stockFactory)
      .createStock(any(), any(), any(), any())
  }

  @Test
  fun `fetchStockWithRetry succeeds on first attempt`() = runBlocking {
    // Given: all providers return valid data
    stubSuccessfulFetch()

    // When
    val result = service.fetchStockWithRetry("AAPL", saveToDb = false)

    // Then
    assertNotNull(result)
    assertEquals("AAPL", result!!.symbol)
  }

  @Test
  fun `fetchStockWithRetry retries on null and succeeds on second attempt`() = runBlocking {
    // Given: all mocks set up for success, then override stockProvider
    stubSuccessfulFetch()

    // Override: first call returns null (triggering retry), second call returns data
    Mockito.reset(stockProvider)
    stockProvider.stub {
      onBlocking { getDailyAdjustedTimeSeries(any(), any(), any()) }
        .doReturn(null)
        .doReturn(emptyList())
    }

    // When
    val result = service.fetchStockWithRetry("AAPL", saveToDb = false)

    // Then: succeeds on second attempt
    assertNotNull(result)
    assertEquals("AAPL", result!!.symbol)
  }

  @Test
  fun `fetchStockWithRetry exhausts all attempts and returns null`() = runBlocking {
    // Given: stockProvider always returns null (other mocks don't matter)
    stockProvider.stub {
      onBlocking { getDailyAdjustedTimeSeries(any(), any(), any()) } doReturn null
    }

    // When
    val result = service.fetchStockWithRetry("AAPL", saveToDb = false)

    // Then
    assertNull(result)
  }

  @Test
  fun `calculateBackoff produces correct exponential delays with cap`() {
    // Given: default retry config (initialDelayMs=10, multiplier=2.0, maxDelayMs=100)

    // When / Then
    // attempt 0: 10 * 2^0 = 10
    assertEquals(10L, service.calculateBackoff(0, retryProperties))
    // attempt 1: 10 * 2^1 = 20
    assertEquals(20L, service.calculateBackoff(1, retryProperties))
    // attempt 2: 10 * 2^2 = 40
    assertEquals(40L, service.calculateBackoff(2, retryProperties))
    // attempt 3: 10 * 2^3 = 80
    assertEquals(80L, service.calculateBackoff(3, retryProperties))
    // attempt 4: 10 * 2^4 = 160 → capped at 100
    assertEquals(100L, service.calculateBackoff(4, retryProperties))
    // attempt 10: 10 * 2^10 = 10240 → capped at 100
    assertEquals(100L, service.calculateBackoff(10, retryProperties))
  }
}
