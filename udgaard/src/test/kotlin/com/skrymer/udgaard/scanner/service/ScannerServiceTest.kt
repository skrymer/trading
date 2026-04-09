package com.skrymer.udgaard.scanner.service

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder
import com.skrymer.udgaard.backtesting.service.StrategyRegistry
import com.skrymer.udgaard.backtesting.strategy.DetailedEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategyReport
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardLatestQuoteDto
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
import com.skrymer.udgaard.scanner.dto.ValidateEntriesRequest
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.model.TradeStatus
import com.skrymer.udgaard.scanner.repository.ScannerTradeJooqRepository
import com.skrymer.udgaard.service.SettingsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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
  private lateinit var settingsService: SettingsService
  private lateinit var midgaardClient: com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
  private lateinit var technicalIndicatorService: com.skrymer.udgaard.data.service.TechnicalIndicatorService

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
    settingsService = mock()
    midgaardClient = mock()
    technicalIndicatorService = mock()

    service = ScannerService(
      scannerTradeRepository,
      stockRepository,
      stockService,
      symbolService,
      strategyRegistry,
      dynamicStrategyBuilder,
      sectorBreadthRepository,
      marketBreadthRepository,
      settingsService,
      midgaardClient,
      technicalIndicatorService,
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
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

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
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

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
    // Given
    val existing = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 150.0)
    whenever(scannerTradeRepository.findById(1L)).thenReturn(existing)

    // When
    service.deleteTrade(1L)

    // Then
    verify(scannerTradeRepository).delete(1L)
  }

  @Test
  fun `deleteTrade allows deleting closed trades`() {
    // Given
    val closedTrade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 150.0).copy(
      status = TradeStatus.CLOSED,
      exitPrice = 160.0,
      exitDate = LocalDate.of(2024, 2, 15),
      realizedPnl = 1000.0,
    )
    whenever(scannerTradeRepository.findById(1L)).thenReturn(closedTrade)

    // When
    service.deleteTrade(1L)

    // Then
    verify(scannerTradeRepository).delete(1L)
  }

  @Test
  fun `deleteTrade throws when trade not found`() {
    // Given
    whenever(scannerTradeRepository.findById(1L)).thenReturn(null)

    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      service.deleteTrade(1L)
    }
  }

  @Test
  fun `deleteAllTrades removes all trades and returns count`() {
    // Given
    whenever(scannerTradeRepository.deleteAll()).thenReturn(5)

    // When
    val count = service.deleteAllTrades()

    // Then
    assertEquals(5, count)
    verify(scannerTradeRepository).deleteAll()
  }

  @Test
  fun `deleteAllTrades returns zero when no trades exist`() {
    // Given
    whenever(scannerTradeRepository.deleteAll()).thenReturn(0)

    // When
    val count = service.deleteAllTrades()

    // Then
    assertEquals(0, count)
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
      optionPrice = 5.0,
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

  @Test
  fun `validateEntries returns valid when entry still matches and exit not triggered`() {
    // Given
    val entryStrategy: EntryStrategy = mock()
    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createEntryStrategy("Mjolnir")).thenReturn(entryStrategy)
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val today = LocalDate.now()
    val quote = StockQuote(symbol = "AAPL", date = today, closePrice = 150.0, atr = 3.5, trend = "Uptrend")
    val stock = Stock(symbol = "AAPL", quotes = listOf(quote))

    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto("AAPL", 152.0, 150.0, 2.0, 1.33, 1000000, System.currentTimeMillis()),
    )
    whenever(entryStrategy.test(any<Stock>(), any<StockQuote>(), any<BacktestContext>())).thenReturn(true)
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    val request = ValidateEntriesRequest(listOf("AAPL"), "Mjolnir", "MjolnirExit")

    // When
    val response = service.validateEntries(request)

    // Then
    assertEquals(1, response.results.size)
    assertEquals(1, response.validCount)
    assertEquals(0, response.invalidCount)
    assertEquals(0, response.doaCount)
    with(response.results[0]) {
      assertEquals("AAPL", symbol)
      assertTrue(entryStillValid)
      assertFalse(exitWouldTrigger)
      assertEquals(152.0, currentPrice)
      assertTrue(usedLiveData)
    }
  }

  @Test
  fun `validateEntries returns invalid when entry conditions no longer met`() {
    // Given
    val entryStrategy: EntryStrategy = mock()
    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createEntryStrategy("Mjolnir")).thenReturn(entryStrategy)
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val today = LocalDate.now()
    val quote = StockQuote(symbol = "AAPL", date = today, closePrice = 150.0, atr = 3.5, trend = "Uptrend")
    val stock = Stock(symbol = "AAPL", quotes = listOf(quote))

    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto("AAPL", 140.0, 150.0, -10.0, -6.67, 1000000, System.currentTimeMillis()),
    )
    whenever(entryStrategy.test(any<Stock>(), any<StockQuote>(), any<BacktestContext>())).thenReturn(false)
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    val request = ValidateEntriesRequest(listOf("AAPL"), "Mjolnir", "MjolnirExit")

    // When
    val response = service.validateEntries(request)

    // Then
    assertEquals(1, response.results.size)
    assertEquals(0, response.validCount)
    assertEquals(1, response.invalidCount)
    assertEquals(0, response.doaCount)
    assertFalse(response.results[0].entryStillValid)
  }

  @Test
  fun `validateEntries returns DOA when exit would trigger immediately`() {
    // Given
    val entryStrategy: EntryStrategy = mock()
    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createEntryStrategy("Mjolnir")).thenReturn(entryStrategy)
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val today = LocalDate.now()
    val quote = StockQuote(symbol = "AAPL", date = today, closePrice = 150.0, atr = 3.5, trend = "Uptrend")
    val stock = Stock(symbol = "AAPL", quotes = listOf(quote))

    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto("AAPL", 145.0, 150.0, -5.0, -3.33, 1000000, System.currentTimeMillis()),
    )
    whenever(entryStrategy.test(any<Stock>(), any<StockQuote>(), any<BacktestContext>())).thenReturn(true)
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = true, exitReason = "Stop loss hit", exitPrice = 145.0))

    val request = ValidateEntriesRequest(listOf("AAPL"), "Mjolnir", "MjolnirExit")

    // When
    val response = service.validateEntries(request)

    // Then
    assertEquals(1, response.results.size)
    assertEquals(0, response.validCount)
    assertEquals(0, response.invalidCount)
    assertEquals(1, response.doaCount)
    with(response.results[0]) {
      assertTrue(entryStillValid)
      assertTrue(exitWouldTrigger)
      assertEquals("Stop loss hit", exitReason)
    }
  }

  @Test
  fun `validateEntries falls back to DB quote when live quote unavailable`() {
    // Given
    val entryStrategy: EntryStrategy = mock()
    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createEntryStrategy("Mjolnir")).thenReturn(entryStrategy)
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val today = LocalDate.now()
    val quote = StockQuote(symbol = "AAPL", date = today, closePrice = 150.0, atr = 3.5, trend = "Uptrend")
    val stock = Stock(symbol = "AAPL", quotes = listOf(quote))

    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(null)
    whenever(entryStrategy.test(any<Stock>(), any<StockQuote>(), any<BacktestContext>())).thenReturn(true)
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    val request = ValidateEntriesRequest(listOf("AAPL"), "Mjolnir", "MjolnirExit")

    // When
    val response = service.validateEntries(request)

    // Then
    assertEquals(1, response.results.size)
    with(response.results[0]) {
      assertFalse(usedLiveData)
      assertEquals(150.0, currentPrice)
      assertTrue(entryStillValid)
    }
  }

  @Test
  fun `validateEntries returns condition details for detailed entry strategy`() {
    // Given
    val entryStrategy: DetailedEntryStrategy = mock()
    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createEntryStrategy("Vcp")).thenReturn(entryStrategy)
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val today = LocalDate.now()
    val quote = StockQuote(symbol = "AAPL", date = today, closePrice = 150.0, atr = 3.5, trend = "Uptrend")
    val stock = Stock(symbol = "AAPL", quotes = listOf(quote))

    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto("AAPL", 148.0, 150.0, -2.0, -1.33, 1000000, System.currentTimeMillis()),
    )

    val details = EntrySignalDetails(
      strategyName = "Vcp",
      strategyDescription = "VCP Strategy",
      conditions = listOf(
        ConditionEvaluationResult("uptrend", "Stock in uptrend", true, null),
        ConditionEvaluationResult("priceAboveEma", "Price above 20 EMA", false, "Price 148.0 < EMA 149.5"),
      ),
      allConditionsMet = false,
    )
    whenever(entryStrategy.testWithDetails(any<Stock>(), any<StockQuote>(), any<BacktestContext>())).thenReturn(details)
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    val request = ValidateEntriesRequest(listOf("AAPL"), "Vcp", "MjolnirExit")

    // When
    val response = service.validateEntries(request)

    // Then
    assertEquals(1, response.results.size)
    with(response.results[0]) {
      assertFalse(entryStillValid)
      assertEquals("Vcp", entrySignalDetails?.strategyName)
      assertEquals(2, entrySignalDetails?.conditions?.size)
      assertTrue(entrySignalDetails?.conditions?.get(0)?.passed == true)
      assertFalse(entrySignalDetails?.conditions?.get(1)?.passed == true)
    }
  }

  @Test
  fun `validateEntries throws when entry strategy not found`() {
    // Given
    whenever(strategyRegistry.createEntryStrategy("NonExistent")).thenReturn(null)

    val request = ValidateEntriesRequest(listOf("AAPL"), "NonExistent", "MjolnirExit")

    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      service.validateEntries(request)
    }
  }

  @Test
  fun `validateEntries throws when exit strategy not found`() {
    // Given
    val entryStrategy: EntryStrategy = mock()
    whenever(strategyRegistry.createEntryStrategy("Mjolnir")).thenReturn(entryStrategy)
    whenever(strategyRegistry.createExitStrategy("NonExistent")).thenReturn(null)

    val request = ValidateEntriesRequest(listOf("AAPL"), "Mjolnir", "NonExistent")

    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      service.validateEntries(request)
    }
  }

  @Test
  fun `checkExits enriches synthetic quote with incremental EMAs`() {
    // Given: a trade with a stock whose last quote has known EMA values
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val entryQuote = StockQuote(symbol = "AAPL", date = trade.entryDate, closePrice = 100.0)
    val lastQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(1),
      closePrice = 150.0,
      high = 152.0,
      low = 148.0,
    ).apply {
      closePriceEMA5 = 149.0
      closePriceEMA10 = 147.0
      closePriceEMA20 = 145.0
      closePriceEMA50 = 140.0
      closePriceEMA100 = 135.0
      ema200 = 130.0
      atr = 3.0
      donchianUpperBand = 155.0
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(entryQuote, lastQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Live quote: price=155, high=157, low=153 (moved up from 150.0)
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto(
        "AAPL",
        155.0,
        150.0,
        5.0,
        3.33,
        2000000,
        System.currentTimeMillis(),
        high = 157.0,
        low = 153.0
      ),
    )
    whenever(technicalIndicatorService.determineTrend(any())).thenReturn("Uptrend")

    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    // When
    service.checkExits()

    // Then: verify the synthetic quote passed to exit strategy has updated indicators
    val syntheticQuote = quoteCaptor.firstValue
    assertEquals(LocalDate.now(), syntheticQuote.date)
    assertEquals(155.0, syntheticQuote.closePrice)
    assertEquals(157.0, syntheticQuote.high)
    assertEquals(153.0, syntheticQuote.low)

    // EMA5: (155 - 149) * (2/6) + 149 = 6 * 0.3333 + 149 = 151.0
    assertEquals(151.0, syntheticQuote.closePriceEMA5, 0.01)
    // EMA10: (155 - 147) * (2/11) + 147 = 8 * 0.1818 + 147 = 148.4545
    assertEquals(148.45, syntheticQuote.closePriceEMA10, 0.01)
    // EMA20: (155 - 145) * (2/21) + 145 = 10 * 0.0952 + 145 = 145.952
    assertEquals(145.95, syntheticQuote.closePriceEMA20, 0.01)

    // ATR: TR = max(157-153, |157-150|, |153-150|) = max(4, 7, 3) = 7
    // ATR = ((3.0 * 13) + 7) / 14 = 46 / 14 = 3.2857
    assertEquals(3.29, syntheticQuote.atr, 0.01)

    // Donchian: max of recent highs (152.0) and live high (157.0) = 157.0
    assertEquals(157.0, syntheticQuote.donchianUpperBand)

    assertEquals("Uptrend", syntheticQuote.trend)
    verify(technicalIndicatorService).determineTrend(any())
  }

  @Test
  fun `checkExits falls back to plain copy when live price is zero`() {
    // Given: live quote returns price = 0 (API error)
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val lastQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(1),
      closePrice = 150.0,
    ).apply {
      closePriceEMA5 = 149.0
      atr = 3.0
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(lastQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Price = 0.0 simulates API error
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto("AAPL", 0.0, 150.0, 0.0, 0.0, 0, System.currentTimeMillis()),
    )

    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    // When
    service.checkExits()

    // Then: indicators should NOT be corrupted — original values preserved
    val syntheticQuote = quoteCaptor.firstValue
    assertEquals(149.0, syntheticQuote.closePriceEMA5)
    assertEquals(3.0, syntheticQuote.atr)
    assertEquals(150.0, syntheticQuote.closePrice) // falls back to lastDbQuote close
  }

  @Test
  fun `checkExits preserves zero EMAs without corrupting them`() {
    // Given: a stock with zero EMAs (insufficient history for calculation)
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val lastQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(1),
      closePrice = 150.0,
    ).apply {
      closePriceEMA5 = 149.0
      closePriceEMA50 = 0.0 // insufficient history
      ema200 = 0.0 // insufficient history
      atr = 3.0
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(lastQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto("AAPL", 155.0, 150.0, 5.0, 3.33, 1000000, System.currentTimeMillis()),
    )
    whenever(technicalIndicatorService.determineTrend(any())).thenReturn("Uptrend")

    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    // When
    service.checkExits()

    // Then: zero EMAs stay zero (not pulled toward live price)
    val syntheticQuote = quoteCaptor.firstValue
    assertEquals(0.0, syntheticQuote.closePriceEMA50)
    assertEquals(0.0, syntheticQuote.ema200)
    // Non-zero EMA5 is updated normally
    assertEquals(151.0, syntheticQuote.closePriceEMA5, 0.01)
  }

  @Test
  fun `checkExits fetches live quotes concurrently for multiple symbols`() {
    // Given: two trades with different symbols
    val trade1 = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    val trade2 = createScannerTrade(id = 2, symbol = "MSFT", entryPrice = 200.0)
      .copy(symbol = "MSFT")
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade1, trade2))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val aaplQuote = StockQuote(symbol = "AAPL", date = LocalDate.now(), closePrice = 100.0)
    val msftQuote = StockQuote(symbol = "MSFT", date = LocalDate.now(), closePrice = 200.0)
    val aaplStock = Stock(symbol = "AAPL", quotes = listOf(aaplQuote))
    val msftStock = Stock(symbol = "MSFT", quotes = listOf(msftQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(aaplStock, msftStock))

    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto("AAPL", 110.0, 100.0, 10.0, 10.0, 1000000, System.currentTimeMillis()),
    )
    whenever(midgaardClient.getLatestQuote("MSFT")).thenReturn(
      MidgaardLatestQuoteDto("MSFT", 210.0, 200.0, 10.0, 5.0, 2000000, System.currentTimeMillis()),
    )
    whenever(technicalIndicatorService.determineTrend(any())).thenReturn("Uptrend")
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    // When
    val response = service.checkExits()

    // Then: both symbols fetched and results returned
    assertEquals(2, response.checksPerformed)
    val results = response.results.associateBy { it.symbol }
    assertEquals(110.0, results["AAPL"]?.currentPrice)
    assertEquals(210.0, results["MSFT"]?.currentPrice)
    assertTrue(results.values.all { it.usedLiveData })
  }

  @Test
  fun `getDrawdownStats uses live quotes for unrealized PnL`() {
    // Given: one open trade, no closed trades
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))
    whenever(scannerTradeRepository.findClosed()).thenReturn(emptyList())

    val dbQuote = StockQuote(symbol = "AAPL", date = LocalDate.now().minusDays(1), closePrice = 105.0)
    val stock = Stock(symbol = "AAPL", quotes = listOf(dbQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Live price is 110 (higher than DB close of 105)
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(
      MidgaardLatestQuoteDto("AAPL", 110.0, 105.0, 5.0, 4.76, 1000000, System.currentTimeMillis()),
    )
    whenever(settingsService.getPositionSizingSettings()).thenReturn(
      com.skrymer.udgaard.controller.dto
        .PositionSizingSettingsDto(portfolioValue = 100000.0),
    )

    // When
    val stats = service.getDrawdownStats()

    // Then: unrealized P&L uses live price (110 - 100) * 100 = 1000, not DB price (105 - 100) * 100 = 500
    assertEquals(1000.0, stats.totalUnrealizedPnl)
    assertEquals(101000.0, stats.currentEquity)
  }

  @Test
  fun `getDrawdownStats falls back to DB quote when live quote unavailable`() {
    // Given: one open trade, live quote fails
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))
    whenever(scannerTradeRepository.findClosed()).thenReturn(emptyList())

    val dbQuote = StockQuote(symbol = "AAPL", date = LocalDate.now().minusDays(1), closePrice = 105.0)
    val stock = Stock(symbol = "AAPL", quotes = listOf(dbQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Live quote returns null (API unavailable)
    whenever(midgaardClient.getLatestQuote("AAPL")).thenReturn(null)
    whenever(settingsService.getPositionSizingSettings()).thenReturn(
      com.skrymer.udgaard.controller.dto
        .PositionSizingSettingsDto(portfolioValue = 100000.0),
    )

    // When
    val stats = service.getDrawdownStats()

    // Then: falls back to DB price (105 - 100) * 100 = 500
    assertEquals(500.0, stats.totalUnrealizedPnl)
    assertEquals(100500.0, stats.currentEquity)
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
