package com.skrymer.udgaard.portfolio.service

import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.portfolio.integration.broker.AssetType
import com.skrymer.udgaard.portfolio.integration.broker.BrokerAccountInfo
import com.skrymer.udgaard.portfolio.integration.broker.BrokerAdapter
import com.skrymer.udgaard.portfolio.integration.broker.BrokerAdapterFactory
import com.skrymer.udgaard.portfolio.integration.broker.BrokerCredentials
import com.skrymer.udgaard.portfolio.integration.broker.BrokerDataResult
import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import com.skrymer.udgaard.portfolio.integration.broker.OpenCloseIndicator
import com.skrymer.udgaard.portfolio.integration.broker.StandardizedTrade
import com.skrymer.udgaard.portfolio.integration.broker.TradeDirection
import com.skrymer.udgaard.portfolio.integration.broker.TradeProcessor
import com.skrymer.udgaard.portfolio.model.Execution
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.Portfolio
import com.skrymer.udgaard.portfolio.model.Position
import com.skrymer.udgaard.portfolio.model.PositionSource
import com.skrymer.udgaard.portfolio.model.PositionStatus
import com.skrymer.udgaard.portfolio.repository.ExecutionJooqRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

class BrokerIntegrationServiceTest {
  private val adapterFactory: BrokerAdapterFactory = mock()
  private val tradeProcessor = TradeProcessor()
  private val portfolioService: PortfolioService = mock()
  private val positionService: PositionService = mock()
  private val portfolioStatsService: PortfolioStatsService = mock()
  private val executionRepository: ExecutionJooqRepository = mock()
  private val forexTrackingService: ForexTrackingService = mock()
  private val cashTransactionService: CashTransactionService = mock()
  private val midgaardClient: MidgaardClient = mock()
  private val adapter: BrokerAdapter = mock()

  private lateinit var service: BrokerIntegrationService

  private val portfolioId = 1L
  private val today = LocalDate.now()
  private val accountInfo = BrokerAccountInfo("U12345", "USD", "Individual")

  @BeforeEach
  fun setUp() {
    service = BrokerIntegrationService(
      adapterFactory,
      tradeProcessor,
      portfolioService,
      positionService,
      portfolioStatsService,
      executionRepository,
      forexTrackingService,
      cashTransactionService,
      midgaardClient,
    )

    whenever(adapterFactory.getAdapter(any())).thenReturn(adapter)
  }

  @Test
  fun `sync should not duplicate positions when all executions already exist`() {
    val portfolio = createPortfolio()
    whenever(portfolioService.getPortfolio(portfolioId)).thenReturn(portfolio)

    val buyTrade = createTrade("PHVS", "buy-1", 96, 29.74, TradeDirection.BUY, OpenCloseIndicator.OPEN, today.minusDays(7))
    val sellTrade = createTrade("PHVS", "sell-1", 96, 27.56, TradeDirection.SELL, OpenCloseIndicator.CLOSE, today)
    whenever(adapter.fetchTrades(any(), any(), any(), any())).thenReturn(
      BrokerDataResult(listOf(buyTrade, sellTrade), accountInfo),
    )

    // Both executions already exist (batch query returns all IDs)
    whenever(executionRepository.findExistingBrokerTradeIds(any())).thenReturn(setOf("buy-1", "sell-1"))

    val result = service.syncPortfolio(portfolioId, createCredentials())

    assertEquals(0, result.tradesAdded)
    verify(positionService, never()).findOrCreatePosition(
      any(),
      any(),
      any(),
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
      any(),
      any(),
      any(),
      anyOrNull(),
    )
  }

  @Test
  fun `sync should create position when buy exists but sell is new`() {
    val portfolio = createPortfolio()
    whenever(portfolioService.getPortfolio(portfolioId)).thenReturn(portfolio)

    val buyTrade = createTrade("PHVS", "buy-1", 96, 29.74, TradeDirection.BUY, OpenCloseIndicator.OPEN, today.minusDays(7))
    val sellTrade = createTrade("PHVS", "sell-1", 96, 27.56, TradeDirection.SELL, OpenCloseIndicator.CLOSE, today)
    whenever(adapter.fetchTrades(any(), any(), any(), any())).thenReturn(
      BrokerDataResult(listOf(buyTrade, sellTrade), accountInfo),
    )

    // Buy exists, sell does not — batch check returns only buy
    whenever(executionRepository.findExistingBrokerTradeIds(any())).thenReturn(setOf("buy-1"))
    whenever(executionRepository.findByBrokerTradeId("buy-1")).thenReturn(createExecution(1, "buy-1"))
    whenever(executionRepository.findByBrokerTradeId("sell-1")).thenReturn(null)

    val position = createPosition(10L, "PHVS", 96)
    whenever(
      positionService.findOrCreatePosition(
        any(),
        eq("PHVS"),
        any(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        any(),
        any(),
        any(),
        anyOrNull(),
      )
    ).thenReturn(position)
    whenever(positionService.addExecution(any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(createExecution(3, "sell-1"))
    whenever(positionService.getPositionById(10L)).thenReturn(position.copy(currentQuantity = 0))

    val result = service.syncPortfolio(portfolioId, createCredentials())

    assertEquals(1, result.tradesAdded)
    // Sell execution added (negative qty), buy skipped
    verify(positionService).addExecution(eq(10L), eq(-96), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())
  }

  @Test
  fun `sync fetches 3 months of trades to include long-running positions`() {
    val portfolio = createPortfolio()
    whenever(portfolioService.getPortfolio(portfolioId)).thenReturn(portfolio)

    whenever(adapter.fetchTrades(any(), any(), any(), any())).thenReturn(
      BrokerDataResult(emptyList(), accountInfo),
    )

    service.syncPortfolio(portfolioId, createCredentials())

    val expectedStart = LocalDate.now().minusMonths(3)
    verify(adapter).fetchTrades(any(), any(), eq(expectedStart), any())
  }

  @Test
  fun `sync should import new position when no executions exist`() {
    val portfolio = createPortfolio()
    whenever(portfolioService.getPortfolio(portfolioId)).thenReturn(portfolio)

    val buyTrade = createTrade("AAPL", "buy-1", 100, 150.0, TradeDirection.BUY, OpenCloseIndicator.OPEN, today.minusDays(3))
    whenever(adapter.fetchTrades(any(), any(), any(), any())).thenReturn(
      BrokerDataResult(listOf(buyTrade), accountInfo),
    )

    whenever(executionRepository.findExistingBrokerTradeIds(any())).thenReturn(emptySet())
    whenever(executionRepository.findByBrokerTradeId("buy-1")).thenReturn(null)

    val position = createPosition(20L, "AAPL", 100)
    whenever(
      positionService.findOrCreatePosition(
        any(),
        eq("AAPL"),
        any(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        any(),
        any(),
        any(),
        anyOrNull(),
      )
    ).thenReturn(position)
    whenever(positionService.addExecution(any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(createExecution(5, "buy-1"))
    whenever(positionService.getPositionById(20L)).thenReturn(position)

    val result = service.syncPortfolio(portfolioId, createCredentials())

    assertEquals(1, result.tradesAdded)
    verify(positionService).addExecution(eq(20L), eq(100), eq(150.0), any(), anyOrNull(), anyOrNull(), anyOrNull())
  }

  private fun createPortfolio() = Portfolio(
    id = portfolioId,
    userId = "user-1",
    name = "Test Portfolio",
    broker = BrokerType.IBKR,
    brokerAccountId = "U12345",
    initialBalance = 100000.0,
    currency = "USD",
    lastSyncDate = LocalDateTime.now().minusDays(1),
    createdDate = LocalDateTime.now().minusMonths(6),
  )

  private fun createCredentials() = BrokerCredentials.IBKRCredentials(
    token = "test-token",
    queryId = "test-query",
  )

  private fun createTrade(
    symbol: String,
    brokerTradeId: String,
    quantity: Int,
    price: Double,
    direction: TradeDirection,
    openClose: OpenCloseIndicator,
    tradeDate: LocalDate,
  ) = StandardizedTrade(
    brokerTradeId = brokerTradeId,
    symbol = symbol,
    tradeDate = tradeDate,
    tradeTime = null,
    quantity = quantity,
    price = price,
    direction = direction,
    openClose = openClose,
    assetType = AssetType.STOCK,
    optionDetails = null,
    linkedTradeId = null,
    relatedOrderId = null,
    commission = 1.0,
    netAmount = -(quantity * price),
  )

  private fun createExecution(id: Long, brokerTradeId: String) = Execution(
    id = id,
    positionId = 10L,
    brokerTradeId = brokerTradeId,
    linkedBrokerTradeId = null,
    quantity = 96,
    price = 29.74,
    executionDate = today.minusDays(7),
    executionTime = null,
    commission = 1.0,
    notes = null,
  )

  private fun createPosition(id: Long, symbol: String, quantity: Int) = Position(
    id = id,
    portfolioId = portfolioId,
    symbol = symbol,
    underlyingSymbol = null,
    instrumentType = InstrumentType.STOCK,
    optionType = null,
    strikePrice = null,
    expirationDate = null,
    currentQuantity = quantity,
    currentContracts = null,
    averageEntryPrice = 29.74,
    totalCost = 29.74 * quantity,
    status = PositionStatus.OPEN,
    openedDate = today.minusDays(7),
    closedDate = null,
    realizedPnl = null,
    rolledToPositionId = null,
    parentPositionId = null,
    entryStrategy = "Broker Import",
    exitStrategy = "Broker Import",
    notes = null,
    source = PositionSource.BROKER,
  )
}
