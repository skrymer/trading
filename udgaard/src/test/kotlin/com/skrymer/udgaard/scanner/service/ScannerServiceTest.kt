package com.skrymer.udgaard.scanner.service

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder
import com.skrymer.udgaard.backtesting.service.StrategyRegistry
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategyReport
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.RollScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ScanRequest
import com.skrymer.udgaard.scanner.dto.UpdateScannerTradeRequest
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.repository.ScannerTradeJooqRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class ScannerServiceTest {
  private lateinit var service: ScannerService
  private lateinit var scannerTradeRepository: ScannerTradeJooqRepository
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var stockService: StockService
  private lateinit var symbolService: SymbolService
  private lateinit var strategyRegistry: StrategyRegistry
  private lateinit var dynamicStrategyBuilder: DynamicStrategyBuilder
  private lateinit var sectorBreadthRepository: SectorBreadthRepository
  private lateinit var marketBreadthRepository: MarketBreadthRepository

  @BeforeEach
  fun setup() {
    scannerTradeRepository = mock()
    stockRepository = mock()
    stockService = mock()
    symbolService = mock()
    strategyRegistry = mock()
    dynamicStrategyBuilder = mock()
    sectorBreadthRepository = mock()
    marketBreadthRepository = mock()

    service = ScannerService(
      scannerTradeRepository,
      stockRepository,
      stockService,
      symbolService,
      strategyRegistry,
      dynamicStrategyBuilder,
      sectorBreadthRepository,
      marketBreadthRepository,
    )

    // Default stubs for breadth/context loading
    whenever(sectorBreadthRepository.findAllAsMap()).thenReturn(emptyMap())
    whenever(marketBreadthRepository.findAllAsMap()).thenReturn(emptyMap())
    whenever(stockRepository.findBySymbol(any(), anyOrNull())).thenReturn(null)
  }

  @Test
  fun `scan returns matching stocks from predefined strategy`() {
    // Given
    val entryStrategy: EntryStrategy = mock()
    whenever(strategyRegistry.createEntryStrategy("Mjolnir")).thenReturn(entryStrategy)

    val today = LocalDate.now()
    val quote = StockQuote(symbol = "AAPL", date = today, closePrice = 150.0, atr = 3.5, trend = "Uptrend")
    val stock = Stock(symbol = "AAPL", sectorSymbol = "XLK", quotes = listOf(quote))

    whenever(stockRepository.findAllSymbols()).thenReturn(listOf("AAPL"))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))
    whenever(entryStrategy.test(any<Stock>(), any<StockQuote>(), any<BacktestContext>())).thenReturn(true)

    val request = ScanRequest(entryStrategyName = "Mjolnir", exitStrategyName = "MjolnirExit")

    // When
    val response = service.scan(request)

    // Then
    assertEquals(1, response.results.size)
    assertEquals("AAPL", response.results[0].symbol)
    assertEquals("XLK", response.results[0].sectorSymbol)
    assertEquals(150.0, response.results[0].closePrice)
    assertEquals(3.5, response.results[0].atr)
    assertEquals("Mjolnir", response.entryStrategyName)
  }

  @Test
  fun `scan filters out non-matching stocks`() {
    // Given
    val entryStrategy: EntryStrategy = mock()
    whenever(strategyRegistry.createEntryStrategy("Mjolnir")).thenReturn(entryStrategy)

    val today = LocalDate.now()
    val matchingQuote = StockQuote(symbol = "AAPL", date = today, closePrice = 150.0, atr = 3.5, trend = "Uptrend")
    val nonMatchingQuote = StockQuote(symbol = "MSFT", date = today, closePrice = 300.0, atr = 5.0, trend = "Downtrend")
    val matchingStock = Stock(symbol = "AAPL", quotes = listOf(matchingQuote))
    val nonMatchingStock = Stock(symbol = "MSFT", quotes = listOf(nonMatchingQuote))

    whenever(stockRepository.findAllSymbols()).thenReturn(listOf("AAPL", "MSFT"))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(matchingStock, nonMatchingStock))

    // Only AAPL matches
    whenever(entryStrategy.test(any<Stock>(), any<StockQuote>(), any<BacktestContext>())).thenAnswer { invocation ->
      val stock = invocation.getArgument<Stock>(0)
      stock.symbol == "AAPL"
    }

    val request = ScanRequest(entryStrategyName = "Mjolnir", exitStrategyName = "MjolnirExit")

    // When
    val response = service.scan(request)

    // Then
    assertEquals(1, response.results.size)
    assertEquals("AAPL", response.results[0].symbol)
    assertEquals(2, response.totalStocksScanned)
  }

  @Test
  fun `checkExits detects triggered exit signals`() {
    // Given
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findAll()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val entryQuote = StockQuote(symbol = "AAPL", date = trade.entryDate, closePrice = 100.0)
    val latestQuote = StockQuote(symbol = "AAPL", date = LocalDate.now(), closePrice = 90.0, atr = 3.0)
    val stock = Stock(symbol = "AAPL", quotes = listOf(entryQuote, latestQuote))

    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = true, exitReason = "Stop loss hit", exitPrice = 90.0))

    // When
    val response = service.checkExits()

    // Then
    assertEquals(1, response.checksPerformed)
    assertEquals(1, response.exitsTriggered)
    assertTrue(response.results[0].exitTriggered)
    assertEquals("Stop loss hit", response.results[0].exitReason)
    assertEquals(-10.0, response.results[0].unrealizedPnlPercent)
  }

  @Test
  fun `checkExits returns no exits when strategy does not match`() {
    // Given
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findAll()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val entryQuote = StockQuote(symbol = "AAPL", date = trade.entryDate, closePrice = 100.0)
    val latestQuote = StockQuote(symbol = "AAPL", date = LocalDate.now(), closePrice = 110.0, atr = 3.0)
    val stock = Stock(symbol = "AAPL", quotes = listOf(entryQuote, latestQuote))

    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    // When
    val response = service.checkExits()

    // Then
    assertEquals(1, response.checksPerformed)
    assertEquals(0, response.exitsTriggered)
    assertFalse(response.results[0].exitTriggered)
    assertEquals(10.0, response.results[0].unrealizedPnlPercent)
  }

  @Test
  fun `addTrade creates and returns scanner trade`() {
    // Given
    val request = AddScannerTradeRequest(
      symbol = "AAPL",
      sectorSymbol = "XLK",
      instrumentType = "STOCK",
      entryPrice = 150.0,
      entryDate = "2024-01-15",
      quantity = 100,
      entryStrategyName = "Mjolnir",
      exitStrategyName = "MjolnirExit",
      notes = "Test trade",
    )

    whenever(scannerTradeRepository.save(any())).thenAnswer { invocation ->
      val trade = invocation.getArgument<ScannerTrade>(0)
      trade.copy(id = 1)
    }

    // When
    val trade = service.addTrade(request)

    // Then
    assertEquals(1, trade.id)
    assertEquals("AAPL", trade.symbol)
    assertEquals("XLK", trade.sectorSymbol)
    assertEquals(InstrumentType.STOCK, trade.instrumentType)
    assertEquals(150.0, trade.entryPrice)
    assertEquals("Mjolnir", trade.entryStrategyName)
    assertEquals("Test trade", trade.notes)
  }

  @Test
  fun `updateTrade updates notes only`() {
    // Given
    val existing = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 150.0)
    whenever(scannerTradeRepository.findById(1L)).thenReturn(existing)
    whenever(scannerTradeRepository.save(any())).thenAnswer { it.getArgument<ScannerTrade>(0) }

    val request = UpdateScannerTradeRequest(notes = "Updated notes")

    // When
    val updated = service.updateTrade(1L, request)

    // Then
    assertEquals("Updated notes", updated.notes)
    assertEquals("AAPL", updated.symbol)
    assertEquals(150.0, updated.entryPrice)
  }

  @Test
  fun `deleteTrade removes trade from repository`() {
    // When
    service.deleteTrade(1L)

    // Then
    verify(scannerTradeRepository).delete(1L)
  }

  @Test
  fun `rollTrade deletes old trade and creates new with accumulated credits`() {
    // Given
    val existingTrade = ScannerTrade(
      id = 1,
      symbol = "AAPL",
      sectorSymbol = "XLK",
      instrumentType = InstrumentType.OPTION,
      entryPrice = 5.0,
      entryDate = LocalDate.of(2024, 1, 15),
      quantity = 1,
      optionType = OptionType.PUT,
      strikePrice = 140.0,
      expirationDate = LocalDate.of(2024, 2, 16),
      multiplier = 100,
      entryStrategyName = "Mjolnir",
      exitStrategyName = "MjolnirExit",
      rolledCredits = 50.0,
      rollCount = 1,
      notes = "Original trade",
    )

    whenever(scannerTradeRepository.findById(1L)).thenReturn(existingTrade)
    whenever(scannerTradeRepository.save(any())).thenAnswer { invocation ->
      val trade = invocation.getArgument<ScannerTrade>(0)
      trade.copy(id = 2)
    }

    val request = RollScannerTradeRequest(
      closePrice = 3.0,
      newStrikePrice = 135.0,
      newExpirationDate = "2024-03-15",
      newEntryPrice = 4.5,
      newEntryDate = "2024-02-16",
      newQuantity = 1,
    )

    // When
    val newTrade = service.rollTrade(1L, request)

    // Then
    verify(scannerTradeRepository).delete(1L)

    // Roll credit: (3.0 - 5.0) * 1 * 100 = -200.0
    // New rolled credits: 50.0 + (-200.0) = -150.0
    assertEquals(-150.0, newTrade.rolledCredits)
    assertEquals(2, newTrade.rollCount)
    assertEquals(135.0, newTrade.strikePrice)
    assertEquals(LocalDate.of(2024, 3, 15), newTrade.expirationDate)
    assertEquals(4.5, newTrade.entryPrice)
    assertEquals("Mjolnir", newTrade.entryStrategyName)
    assertEquals("MjolnirExit", newTrade.exitStrategyName)
    assertEquals("Original trade", newTrade.notes)
  }

  @Test
  fun `checkExits returns empty response when no trades exist`() {
    // Given
    whenever(scannerTradeRepository.findAll()).thenReturn(emptyList())

    // When
    val response = service.checkExits()

    // Then
    assertEquals(0, response.checksPerformed)
    assertEquals(0, response.exitsTriggered)
    assertTrue(response.results.isEmpty())
  }

  private fun createScannerTrade(
    id: Long,
    symbol: String,
    entryPrice: Double,
  ): ScannerTrade =
    ScannerTrade(
      id = id,
      symbol = symbol,
      sectorSymbol = "XLK",
      instrumentType = InstrumentType.STOCK,
      entryPrice = entryPrice,
      entryDate = LocalDate.of(2024, 1, 15),
      quantity = 100,
      optionType = null,
      strikePrice = null,
      expirationDate = null,
      multiplier = 100,
      entryStrategyName = "Mjolnir",
      exitStrategyName = "MjolnirExit",
      notes = null,
    )
}
