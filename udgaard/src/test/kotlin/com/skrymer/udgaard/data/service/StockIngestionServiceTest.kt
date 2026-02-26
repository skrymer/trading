package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardSymbolDto
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
  fun `refreshStock deletes existing and saves to database`() {
    // Given
    stubSuccessfulFetch()
    whenever(stockRepository.findBySymbol("AAPL")).thenReturn(null)

    // When
    val result = service.refreshStock("AAPL")

    // Then
    assertNotNull(result)
    verify(stockRepository).save(any())
  }
}
