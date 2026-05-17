package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardSymbolDto
import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class StockIngestionServiceTest {
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var stockProvider: StockProvider
  private lateinit var technicalIndicatorService: TechnicalIndicatorService
  private lateinit var orderBlockCalculator: OrderBlockCalculator
  private lateinit var midgaardClient: MidgaardClient
  private lateinit var sectorBreadthService: SectorBreadthService
  private lateinit var marketBreadthService: MarketBreadthService
  private lateinit var marketBreadthRepository: MarketBreadthRepository

  private lateinit var service: StockIngestionService

  @BeforeEach
  fun setup() {
    stockRepository = mock()
    stockProvider = mock()
    technicalIndicatorService = mock()
    orderBlockCalculator = mock()
    midgaardClient = mock()
    sectorBreadthService = mock()
    marketBreadthService = mock()
    marketBreadthRepository = mock()

    service = StockIngestionService(
      stockRepository = stockRepository,
      stockProvider = stockProvider,
      technicalIndicatorService = technicalIndicatorService,
      orderBlockCalculator = orderBlockCalculator,
      midgaardClient = midgaardClient,
      sectorBreadthService = sectorBreadthService,
      marketBreadthService = marketBreadthService,
      marketBreadthRepository = marketBreadthRepository,
    )
  }

  private fun stubSuccessfulFetch() {
    whenever(stockProvider.getDailyAdjustedTimeSeries(any()))
      .thenReturn(emptyList())
    whenever(technicalIndicatorService.enrichWithIndicators(any(), any()))
      .thenReturn(emptyList())
    Mockito
      .doReturn(emptyList<OrderBlock>())
      .`when`(orderBlockCalculator)
      .calculateOrderBlocks(any(), any(), any(), any(), any())
    whenever(midgaardClient.getSymbolInfo(any()))
      .thenReturn(MidgaardSymbolDto(symbol = "AAPL", assetType = "STOCK", sector = null, sectorSymbol = "XLK"))
  }

  @Test
  fun `fetchAndBuildStock succeeds and returns stock`() {
    // Given
    stubSuccessfulFetch()

    // When
    val result = service.fetchAndBuildStock("AAPL")

    // Then
    assertNotNull(result)
    assertEquals("AAPL", result!!.symbol)
    assertEquals("XLK", result.sectorSymbol)
  }

  @Test
  fun `fetchAndBuildStock returns null when provider returns null`() {
    // Given
    whenever(stockProvider.getDailyAdjustedTimeSeries(any()))
      .thenReturn(null)

    // When
    val result = service.fetchAndBuildStock("AAPL")

    // Then
    assertNull(result)
  }

  @Test
  fun `refreshStock fetches and persists the stock`() {
    // Given a successful fetch path
    stubSuccessfulFetch()

    // When refreshStock runs
    val result = service.refreshStock("AAPL")

    // Then the resulting stock is persisted via the repository's save (which itself does
    // delete-then-insert per table inside its transaction — no separate delete step needed)
    assertNotNull(result)
    verify(stockRepository).save(any())
  }

  @Test
  fun `fetchAndBuildStock populates earnings from provider when fetch succeeds`() {
    // Given the provider returns a non-empty earnings history for the symbol
    stubSuccessfulFetch()
    val fresh = listOf(earning("AAPL", LocalDate.of(2024, 3, 31)))
    whenever(stockProvider.getEarnings("AAPL")).thenReturn(fresh)

    // When the stock is built
    val result = service.fetchAndBuildStock("AAPL")

    // Then earnings on the result come from the provider, and no DB fallback was needed
    assertNotNull(result)
    assertEquals(fresh, result!!.earnings)
    verify(stockRepository, never()).findEarnings(any())
  }

  @Test
  fun `fetchAndBuildStock falls back to stored earnings when provider returns null`() {
    // Given the provider returns null (outage / parse error)
    stubSuccessfulFetch()
    whenever(stockProvider.getEarnings("AAPL")).thenReturn(null)
    val stored = listOf(earning("AAPL", LocalDate.of(2023, 12, 31)))
    whenever(stockRepository.findEarnings("AAPL")).thenReturn(stored)

    // When the stock is built
    val result = service.fetchAndBuildStock("AAPL")

    // Then the stock is still built with the last-known earnings — preserves the safety
    // filter on noEarningsWithinDays / exitBeforeEarnings during transient upstream failures
    assertNotNull(result)
    assertEquals(stored, result!!.earnings)
    verify(stockRepository).findEarnings(eq("AAPL"))
  }

  @Test
  fun `fetchAndBuildStock falls back to stored earnings when provider throws`() {
    // Given the provider throws (e.g. socket timeout escapes the client's try-catch)
    stubSuccessfulFetch()
    whenever(stockProvider.getEarnings("AAPL")).thenThrow(RuntimeException("boom"))
    val stored = listOf(earning("AAPL", LocalDate.of(2023, 12, 31)))
    whenever(stockRepository.findEarnings("AAPL")).thenReturn(stored)

    // When the stock is built
    val result = service.fetchAndBuildStock("AAPL")

    // Then the build still succeeds with the fallback earnings
    assertNotNull(result)
    assertEquals(stored, result!!.earnings)
    verify(stockRepository).findEarnings(eq("AAPL"))
  }

  private fun earning(symbol: String, fiscalDate: LocalDate): Earning =
    Earning(
      symbol = symbol,
      fiscalDateEnding = fiscalDate,
      reportedDate = fiscalDate.plusDays(20),
      reportedEPS = 1.50,
      estimatedEPS = 1.45,
      surprise = 0.05,
      surprisePercentage = 3.45,
      reportTime = "AfterMarket",
    )
}
