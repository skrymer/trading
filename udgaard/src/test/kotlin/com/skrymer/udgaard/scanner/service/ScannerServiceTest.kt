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
import com.skrymer.udgaard.data.integration.LatestQuote
import com.skrymer.udgaard.data.integration.StockProvider
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
import org.mockito.kotlin.never
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
  private lateinit var stockProvider: StockProvider
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
    stockProvider = mock()
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
      stockProvider,
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
  fun `getTrades enriches each open trade with tradingDaysHeld from its stock quote series`() {
    // Given: a trade entered at day E and a stock with stored quotes on E through E+4
    // (entry bar + 4 post-entry bars). countTradingDaysBetween counts quotes in the
    // half-open interval (entry, today], so exactly 4 post-entry bars should be counted.
    val entryDate = LocalDate.of(2024, 1, 2)
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 150.0).copy(entryDate = entryDate)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))
    val stock = Stock(
      symbol = "AAPL",
      quotes = (0..4).map { i ->
        StockQuote(symbol = "AAPL", date = entryDate.plusDays(i.toLong()), closePrice = 150.0)
      },
    )
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // When
    val result = service.getTrades()

    // Then
    assertEquals(1, result.size)
    assertEquals(4, result[0].tradingDaysHeld, "should count 4 stored quotes after entry (exclusive start, inclusive end)")
  }

  @Test
  fun `getTrades returns null tradingDaysHeld when the stock is missing from the repository`() {
    // Given: a trade references a symbol with no stored data (unknown or stale). Null is
    // expected so the frontend can render "-", distinguishing data-missing from a
    // legitimate 0 trading days elapsed.
    val trade = createScannerTrade(id = 1, symbol = "GHOST", entryPrice = 10.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(emptyList())

    // When
    val result = service.getTrades()

    // Then
    assertEquals(1, result.size)
    assertEquals(null, result[0].tradingDaysHeld)
  }

  @Test
  fun `getTrades counts only stored quotes strictly after entryDate`() {
    // Given: a trade with a quote series that has a gap right after entry (e.g., sparse
    // ingestion or a stock that came online after entry). Only the 2 actually-stored
    // post-entry bars should be counted — not the calendar distance from entry.
    val entryDate = LocalDate.of(2024, 1, 2)
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 150.0).copy(entryDate = entryDate)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))
    val stock = Stock(
      symbol = "AAPL",
      quotes = listOf(3, 4).map { offset ->
        StockQuote(symbol = "AAPL", date = entryDate.plusDays(offset.toLong()), closePrice = 150.0)
      },
    )
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // When
    val result = service.getTrades()

    // Then
    assertEquals(2, result[0].tradingDaysHeld, "only the 2 post-entry stored quotes count")
  }

  @Test
  fun `addTrade uses the stored date when the live quote describes the same bar`() {
    // Given: a trade request whose client-supplied entryDate is one day off (e.g., a
    // timezone artifact from a browser in a forward timezone), but the live quote and the
    // latest stored bar agree on the real market date. The live quote's indicators were
    // built from that stored bar, so using the stored date guarantees the entryQuote
    // lookup in evaluateTradeExit succeeds.
    val latestStored = LocalDate.of(2026, 4, 1)
    val request = addTradeRequest(symbol = "NFLX", entryDate = "2026-04-02")
    whenever(stockRepository.getLatestQuoteDate("NFLX")).thenReturn(latestStored)
    whenever(stockProvider.getLatestQuote("NFLX"))
      .thenReturn(LatestQuote(symbol = "NFLX", price = 93.24, previousClose = 92.58, date = latestStored))
    whenever(scannerTradeRepository.save(any<ScannerTrade>())).thenAnswer { it.arguments[0] }

    // When
    val saved = service.addTrade(request)

    // Then: the trade is stored against the real market date, not the client's timezone-
    // influenced one.
    assertEquals(latestStored, saved.entryDate, "should anchor to the stored bar, ignoring the client's wrong date")
  }

  @Test
  fun `addTrade uses the live quote date when it is ahead of the latest stored bar`() {
    // Given: the live quote describes today's bar but EOD ingestion hasn't run yet (stored
    // stops at yesterday). The user's real entry is today, so the recorded entry should be
    // today even though no stored quote will match until ingestion catches up.
    val today = LocalDate.of(2026, 4, 2)
    val yesterday = today.minusDays(1)
    val request = addTradeRequest(symbol = "AAPL", entryDate = today.toString())
    whenever(stockRepository.getLatestQuoteDate("AAPL")).thenReturn(yesterday)
    whenever(stockProvider.getLatestQuote("AAPL"))
      .thenReturn(LatestQuote(symbol = "AAPL", price = 150.5, previousClose = 149.0, date = today))
    whenever(scannerTradeRepository.save(any<ScannerTrade>())).thenAnswer { it.arguments[0] }

    // When
    val saved = service.addTrade(request)

    // Then
    assertEquals(today, saved.entryDate, "should use live date when ingestion hasn't caught up")
  }

  @Test
  fun `addTrade falls back to latest stored when live quote is unavailable`() {
    // Given: the live-quote provider errored or returned null (outage), but we have
    // stored history. The latest stored bar is the safer choice than a possibly-wrong
    // client timezone date.
    val latestStored = LocalDate.of(2026, 4, 1)
    val request = addTradeRequest(symbol = "AAPL", entryDate = "2026-04-03")
    whenever(stockRepository.getLatestQuoteDate("AAPL")).thenReturn(latestStored)
    whenever(stockProvider.getLatestQuote("AAPL")).thenReturn(null)
    whenever(scannerTradeRepository.save(any<ScannerTrade>())).thenAnswer { it.arguments[0] }

    // When
    val saved = service.addTrade(request)

    // Then
    assertEquals(latestStored, saved.entryDate, "should anchor to latest stored when live unavailable")
  }

  @Test
  fun `addTrade uses the live quote date when the symbol has no stored history`() {
    // Given: a freshly-added symbol with no ingested quotes yet. Live quote is available
    // and carries today's date.
    val today = LocalDate.of(2026, 4, 2)
    val request = addTradeRequest(symbol = "NEW", entryDate = today.toString())
    whenever(stockRepository.getLatestQuoteDate("NEW")).thenReturn(null)
    whenever(stockProvider.getLatestQuote("NEW"))
      .thenReturn(LatestQuote(symbol = "NEW", price = 10.0, date = today))
    whenever(scannerTradeRepository.save(any<ScannerTrade>())).thenAnswer { it.arguments[0] }

    // When
    val saved = service.addTrade(request)

    // Then
    assertEquals(today, saved.entryDate)
  }

  @Test
  fun `addTrade falls back to the client date when both live quote and stored history are unavailable`() {
    // Given: worst-case degradation — provider unavailable AND no stored history. The
    // client's date is the only signal left. Pins the terminal branch of resolveEntryDate
    // so a future reshuffle of branch order can't silently change this policy.
    val clientDate = "2026-04-03"
    val request = addTradeRequest(symbol = "GHOST", entryDate = clientDate)
    whenever(stockRepository.getLatestQuoteDate("GHOST")).thenReturn(null)
    whenever(stockProvider.getLatestQuote("GHOST")).thenReturn(null)
    whenever(scannerTradeRepository.save(any<ScannerTrade>())).thenAnswer { it.arguments[0] }

    // When
    val saved = service.addTrade(request)

    // Then
    assertEquals(LocalDate.parse(clientDate), saved.entryDate)
  }

  private fun addTradeRequest(symbol: String, entryDate: String) = AddScannerTradeRequest(
    symbol = symbol,
    sectorSymbol = "XLK",
    instrumentType = "STOCK",
    entryPrice = 150.0,
    entryDate = entryDate,
    quantity = 10,
    entryStrategyName = "Vcp",
    exitStrategyName = "VcpExitStrategy",
    notes = null,
  )

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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote("AAPL", 152.0, volume = 1000000, high = 152.0, low = 150.0)),
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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote("AAPL", 140.0, volume = 1000000)),
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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote("AAPL", 145.0, volume = 1000000)),
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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(emptyMap())
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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote("AAPL", 148.0, volume = 1000000)),
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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote("AAPL", 155.0, volume = 2000000, high = 157.0, low = 153.0)),
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
  fun `checkExits uses stored quote unchanged when live bar matches the last stored bar`() {
    // When the live quote describes the same bar already stored — price matches the last
    // stored close AND previousClose matches the prior stored close — the service must
    // return the stored quote unchanged instead of advancing indicators by one step.
    // Without this guard, running checkExits after EOD (live quote reports today's close,
    // DB already has it) produces phantom forward-stepped EMAs that can fire false exits.
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 95.9)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val previousDayQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(2),
      closePrice = 92.58,
    )
    val lastQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(1),
      closePrice = 93.24,
      high = 93.85,
      low = 92.77,
    ).apply {
      closePriceEMA10 = 98.14
      closePriceEMA20 = 97.92
      closePriceEMA50 = 95.33
      atr = 3.29
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(previousDayQuote, lastQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Live quote: price and previousClose both match the stored bars — same underlying bar.
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf(
        "AAPL" to LatestQuote(
          symbol = "AAPL",
          price = 93.24,
          previousClose = 92.58,
          volume = 0,
          high = 93.85,
          low = 92.77,
        )
      ),
    )

    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    // When
    service.checkExits()

    // Then: strategy sees the stored quote — stored date, stored EMAs, no forward projection.
    val receivedQuote = quoteCaptor.firstValue
    assertEquals(lastQuote.date, receivedQuote.date, "should not be replaced with LocalDate.now()")
    assertEquals(98.14, receivedQuote.closePriceEMA10, 0.001, "EMA10 must equal stored value, not advanced")
    assertEquals(97.92, receivedQuote.closePriceEMA20, 0.001, "EMA20 must equal stored value, not advanced")
    assertEquals(95.33, receivedQuote.closePriceEMA50, 0.001)
    assertEquals(3.29, receivedQuote.atr, 0.001, "ATR must not be Wilder-advanced on a phantom bar")
    // determineTrend is only invoked when a synthetic quote is constructed; on the early-return
    // path it must not be called.
    verify(technicalIndicatorService, never()).determineTrend(any())
  }

  @Test
  fun `checkExits appends the synthetic quote so today counts as a trading day for exit predicates`() {
    // Exit predicates that depend on trading-days-since-entry call
    // stock.countTradingDaysBetween(entryDate, latestQuote.date). If the synthetic quote
    // representing today's live bar isn't treated as a real entry in stock.quotes, the
    // count stops at the last stored bar (yesterday) — any such predicate will therefore
    // fire one bar late (only after today's EOD ingestion). Live exit-checking during the
    // session is the primary use case, so this is unacceptable.
    //
    // This test pins the contract: the Stock passed to ExitStrategy.test has the synthetic
    // quote at its tail, and Stock.countTradingDaysBetween from the stored entry date up
    // to today reflects that extra bar.
    val today = LocalDate.now()
    val entryDate = today.minusDays(20)
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0).copy(entryDate = entryDate)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    // Entry bar + 14 post-entry stored bars. Dates don't need to be consecutive trading
    // days — the test only asserts the count relationship between stored bars and the
    // appended synthetic.
    fun bar(date: LocalDate) = StockQuote(symbol = "AAPL", date = date, closePrice = 100.0).apply {
      atr = 2.0
      closePriceEMA5 = 100.0
      closePriceEMA10 = 100.0
      closePriceEMA20 = 100.0
      closePriceEMA50 = 100.0
    }
    val storedQuotes = (0..14).map { offset -> bar(entryDate.plusDays(offset.toLong())) }
    val stock = Stock(symbol = "AAPL", quotes = storedQuotes)
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Live quote describes a bar later than the last stored — a price move triggers synthesis.
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote(symbol = "AAPL", price = 100.5, previousClose = 100.0, high = 101.0, low = 100.0)),
    )
    whenever(technicalIndicatorService.determineTrend(any())).thenReturn("Uptrend")

    val stockCaptor = argumentCaptor<Stock>()
    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(stockCaptor.capture(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    service.checkExits()

    val receivedStock = stockCaptor.firstValue
    val receivedLatest = quoteCaptor.firstValue

    assertEquals(
      storedQuotes.size + 1,
      receivedStock.quotes.size,
      "synthetic must be appended to stock.quotes for exit evaluation",
    )
    assertEquals(today, receivedStock.quotes.last().date, "appended bar is today")
    assertEquals(today, receivedLatest.date, "latestQuote passed to exit predicate is the synthetic")

    // Core contract: 14 stored post-entry bars + 1 synthetic = 15 trading days since entry.
    // Without the append, this would be 14 and any since-entry day count would be one low.
    assertEquals(
      15,
      receivedStock.countTradingDaysBetween(entryDate, today),
      "today must be counted so since-entry day counts land on the correct bar",
    )
  }

  @Test
  fun `checkExits stamps the synthetic quote with the live quote's market date, not server wall-clock`() {
    // Given: a live quote that carries an authoritative market date from the provider.
    // Older code stamped the synthetic with LocalDate.now() (server wall-clock), which
    // can diverge from the US market day by +/-1 around midnight or in non-US timezones.
    // The synthetic must instead anchor to liveQuote.date so downstream since-entry
    // counts align with the real market calendar regardless of server TZ.
    val marketDate = LocalDate.of(2026, 4, 23)
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val lastQuote = StockQuote(
      symbol = "AAPL",
      date = marketDate.minusDays(1),
      closePrice = 150.0,
      high = 151.0,
      low = 149.0,
    ).apply {
      closePriceEMA5 = 149.0
      closePriceEMA10 = 147.0
      atr = 2.5
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(lastQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Live quote carries a market-clock date different from LocalDate.now().
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote(symbol = "AAPL", price = 155.0, previousClose = 150.0, high = 157.0, low = 153.0, date = marketDate)),
    )
    whenever(technicalIndicatorService.determineTrend(any())).thenReturn("Uptrend")

    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    // When
    service.checkExits()

    // Then: the synthetic quote's date is the provider's market date, NOT LocalDate.now().
    assertEquals(marketDate, quoteCaptor.firstValue.date, "synthetic must anchor to live quote's date, not server wall-clock")
  }

  @Test
  fun `checkExits does not append when synthetic quote date equals the last stored bar`() {
    // Given: the live quote describes the same bar already stored (price and previousClose
    // both match). The same-bar guard in createSyntheticQuote returns lastDbQuote as-is,
    // so syntheticQuote.date == lastDbQuote.date. The strict-isAfter guard in
    // evaluateTradeExit must therefore NOT append — appending would introduce a duplicate
    // date and break the strictly-ascending tail invariant.
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val previousDayQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(2),
      closePrice = 99.0,
    )
    val lastQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(1),
      closePrice = 100.0,
    ).apply {
      atr = 2.0
      closePriceEMA10 = 100.0
      closePriceEMA20 = 100.0
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(previousDayQuote, lastQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Live quote matches the last stored bar exactly (same-bar guard fires inside
    // createSyntheticQuote, returning lastDbQuote).
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote(symbol = "AAPL", price = 100.0, previousClose = 99.0)),
    )

    val stockCaptor = argumentCaptor<Stock>()
    whenever(exitStrategy.test(stockCaptor.capture(), anyOrNull(), any<StockQuote>(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    // When
    service.checkExits()

    // Then
    assertEquals(
      stock.quotes.size,
      stockCaptor.firstValue.quotes.size,
      "no append when synthetic date equals stored date — preserves strictly-ascending tail",
    )
  }

  @Test
  fun `checkExits projects synthetic quote when live price matches but previousClose differs`() {
    // Both conditions must hold for the same-bar guard to fire. If only the live price
    // happens to equal yesterday's stored close but previousClose is different, the live
    // bar is genuinely new data — the guard must NOT fire.
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val previousDayQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(2),
      closePrice = 148.0,
    )
    val lastQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(1),
      closePrice = 150.0,
      high = 151.0,
      low = 149.0,
    ).apply {
      closePriceEMA5 = 149.0
      closePriceEMA10 = 147.0
      atr = 3.0
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(previousDayQuote, lastQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // price matches lastQuote (150.0) but previousClose (140.0) differs from the
    // prior stored close (148.0) — bar is genuinely new.
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf(
        "AAPL" to LatestQuote(
          symbol = "AAPL",
          price = 150.0,
          previousClose = 140.0,
          volume = 1000,
          high = 151.0,
          low = 149.0,
        ),
      ),
    )
    whenever(technicalIndicatorService.determineTrend(any())).thenReturn("Uptrend")

    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    service.checkExits()

    // Strategy receives a synthetic quote (date advanced, trend re-evaluated).
    val receivedQuote = quoteCaptor.firstValue
    assertEquals(LocalDate.now(), receivedQuote.date, "should advance to today — guard must not fire")
    verify(technicalIndicatorService).determineTrend(any())
  }

  @Test
  fun `checkExits uses stored quote unchanged when stock has only one stored bar and live price matches`() {
    // previousDbQuote == null (first bar ever in the DB). The guard accepts this — if the
    // live quote's price matches the single stored close, there's no new bar to project.
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val onlyQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(1),
      closePrice = 93.24,
    ).apply {
      closePriceEMA10 = 98.14
      closePriceEMA20 = 97.92
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(onlyQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote(symbol = "AAPL", price = 93.24, previousClose = 50.0)),
    )

    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    service.checkExits()

    assertEquals(onlyQuote.date, quoteCaptor.firstValue.date, "should use stored date")
    assertEquals(98.14, quoteCaptor.firstValue.closePriceEMA10, 0.001)
    verify(technicalIndicatorService, never()).determineTrend(any())
  }

  @Test
  fun `checkExits projects synthetic quote when price diff exactly equals the tolerance`() {
    // Guard uses strict `<` on both price and previousClose diffs. A diff exactly at
    // QUOTE_MATCH_TOLERANCE (1e-4) must therefore fall through to projection. Pins the
    // boundary so an accidental `<=` change would trip this test.
    val trade = createScannerTrade(id = 1, symbol = "AAPL", entryPrice = 100.0)
    whenever(scannerTradeRepository.findOpen()).thenReturn(listOf(trade))

    val exitStrategy: ExitStrategy = mock()
    whenever(strategyRegistry.createExitStrategy("MjolnirExit")).thenReturn(exitStrategy)

    val previousDayQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(2),
      closePrice = 148.0,
    )
    val lastQuote = StockQuote(
      symbol = "AAPL",
      date = LocalDate.now().minusDays(1),
      closePrice = 150.0,
      high = 151.0,
      low = 149.0,
    ).apply {
      closePriceEMA10 = 147.0
      atr = 3.0
    }
    val stock = Stock(symbol = "AAPL", quotes = listOf(previousDayQuote, lastQuote))
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenReturn(listOf(stock))

    // Live price is exactly 1e-4 above the stored close — diff equals the tolerance.
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf(
        "AAPL" to LatestQuote(
          symbol = "AAPL",
          price = 150.0 + 1e-4,
          previousClose = 148.0,
          high = 151.0,
          low = 149.0,
        ),
      ),
    )
    whenever(technicalIndicatorService.determineTrend(any())).thenReturn("Uptrend")

    val quoteCaptor = argumentCaptor<StockQuote>()
    whenever(exitStrategy.test(any<Stock>(), anyOrNull(), quoteCaptor.capture(), any<BacktestContext>()))
      .thenReturn(ExitStrategyReport(match = false))

    service.checkExits()

    assertEquals(LocalDate.now(), quoteCaptor.firstValue.date, "diff at tolerance must fall through to synthesis")
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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote("AAPL", 0.0, volume = 0)),
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

    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote("AAPL", 155.0, volume = 1000000)),
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

    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf(
        "AAPL" to LatestQuote("AAPL", 110.0, volume = 1000000),
        "MSFT" to LatestQuote("MSFT", 210.0, volume = 2000000),
      ),
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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(
      mapOf("AAPL" to LatestQuote("AAPL", 110.0, volume = 1000000)),
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
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(emptyMap())
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

  private fun createClosedTrade(
    id: Long,
    symbol: String,
    entryPrice: Double,
    exitPrice: Double,
    quantity: Int = 100,
    entryStrategyName: String = "Vcp",
    instrumentType: InstrumentType = InstrumentType.STOCK,
    optionPrice: Double? = null,
    multiplier: Int = 1,
  ): ScannerTrade {
    val pnl = if (instrumentType == InstrumentType.OPTION) {
      (exitPrice - (optionPrice ?: entryPrice)) * quantity * multiplier
    } else {
      (exitPrice - entryPrice) * quantity
    }
    return ScannerTrade(
      id = id,
      symbol = symbol,
      sectorSymbol = "XLK",
      instrumentType = instrumentType,
      entryPrice = entryPrice,
      entryDate = LocalDate.of(2024, 1, 15),
      quantity = quantity,
      optionType = if (instrumentType == InstrumentType.OPTION) OptionType.CALL else null,
      strikePrice = null,
      expirationDate = null,
      multiplier = multiplier,
      optionPrice = optionPrice,
      entryStrategyName = entryStrategyName,
      exitStrategyName = "VcpExitStrategy",
      notes = null,
      status = TradeStatus.CLOSED,
      exitPrice = exitPrice,
      exitDate = LocalDate.of(2024, 2, 15),
      realizedPnl = pnl,
    )
  }

  // --- Closed Trade Stats Tests ---

  @Test
  fun `getClosedTradeStats returns null overall when no closed trades`() {
    whenever(scannerTradeRepository.findClosed()).thenReturn(emptyList())

    val result = service.getClosedTradeStats()

    assertEquals(null, result.overall)
    assertEquals(0, result.byStrategy.size)
  }

  @Test
  fun `getClosedTradeStats computes correct stats for single strategy`() {
    val trades = listOf(
      createClosedTrade(1, "AAPL", 100.0, 115.0), // +15% win
      createClosedTrade(2, "MSFT", 200.0, 190.0), // -5% loss
      createClosedTrade(3, "GOOGL", 150.0, 165.0), // +10% win
    )
    whenever(scannerTradeRepository.findClosed()).thenReturn(trades)

    val result = service.getClosedTradeStats()

    val overall = result.overall!!
    assertEquals(3, overall.trades)
    assertEquals(2, overall.wins)
    assertEquals(1, overall.losses)
    assertEquals(66.7, overall.winRate, 0.1)
    assertTrue(overall.edge > 0)
    assertTrue(overall.profitFactor!! > 1.0)
    assertEquals(2000.0, overall.totalPnl, 0.01) // (1500 - 1000 + 1500)

    assertEquals(1, result.byStrategy.size)
    assertEquals("Vcp", result.byStrategy[0].strategy)
  }

  @Test
  fun `getClosedTradeStats groups by entry strategy`() {
    val trades = listOf(
      createClosedTrade(1, "AAPL", 100.0, 110.0, entryStrategyName = "Vcp"),
      createClosedTrade(2, "MSFT", 200.0, 220.0, entryStrategyName = "Vcp"),
      createClosedTrade(3, "GOOGL", 150.0, 140.0, entryStrategyName = "Mjolnir"),
    )
    whenever(scannerTradeRepository.findClosed()).thenReturn(trades)

    val result = service.getClosedTradeStats()

    assertEquals(3, result.overall!!.trades)
    assertEquals(2, result.byStrategy.size)

    val vcp = result.byStrategy.find { it.strategy == "Vcp" }!!
    assertEquals(2, vcp.trades)
    assertEquals(2, vcp.wins)
    assertEquals(0, vcp.losses)
    assertEquals(100.0, vcp.winRate, 0.1)

    val mjolnir = result.byStrategy.find { it.strategy == "Mjolnir" }!!
    assertEquals(1, mjolnir.trades)
    assertEquals(0, mjolnir.wins)
    assertEquals(1, mjolnir.losses)
    assertEquals(0.0, mjolnir.winRate, 0.1)
  }

  @Test
  fun `getClosedTradeStats handles option trades with correct P&L percent`() {
    val trades = listOf(
      createClosedTrade(
        1,
        "AAPL",
        150.0,
        5.0,
        quantity = 1,
        instrumentType = InstrumentType.OPTION,
        optionPrice = 3.0,
        multiplier = 100,
      ),
    )
    whenever(scannerTradeRepository.findClosed()).thenReturn(trades)

    val result = service.getClosedTradeStats()

    val overall = result.overall!!
    assertEquals(1, overall.trades)
    assertEquals(1, overall.wins)
    // P&L = (5.0 - 3.0) * 1 * 100 = 200
    assertEquals(200.0, overall.totalPnl, 0.01)
    // P&L % = 200 / (3.0 * 1 * 100) * 100 = 66.67%
    assertEquals(66.67, overall.avgWinPct, 0.1)
  }

  @Test
  fun `getClosedTradeStats handles all winners with null profit factor`() {
    val trades = listOf(
      createClosedTrade(1, "AAPL", 100.0, 110.0),
      createClosedTrade(2, "MSFT", 200.0, 220.0),
    )
    whenever(scannerTradeRepository.findClosed()).thenReturn(trades)

    val result = service.getClosedTradeStats()

    val overall = result.overall!!
    assertEquals(null, overall.profitFactor) // infinity
    assertEquals(100.0, overall.winRate, 0.1)
  }

  @Test
  fun `getClosedTradeStats handles all losers with zero profit factor`() {
    val trades = listOf(
      createClosedTrade(1, "AAPL", 100.0, 90.0),
      createClosedTrade(2, "MSFT", 200.0, 180.0),
    )
    whenever(scannerTradeRepository.findClosed()).thenReturn(trades)

    val result = service.getClosedTradeStats()

    val overall = result.overall!!
    assertEquals(0.0, overall.profitFactor)
    assertEquals(0.0, overall.winRate, 0.1)
  }

  @Test
  fun `getClosedTradeStats treats breakeven trades as neither win nor loss`() {
    val trades = listOf(
      createClosedTrade(1, "AAPL", 100.0, 110.0), // win
      createClosedTrade(2, "MSFT", 200.0, 200.0), // breakeven
      createClosedTrade(3, "GOOGL", 150.0, 140.0), // loss
    )
    whenever(scannerTradeRepository.findClosed()).thenReturn(trades)

    val result = service.getClosedTradeStats()

    val overall = result.overall!!
    assertEquals(3, overall.trades)
    assertEquals(1, overall.wins)
    assertEquals(1, overall.losses)
    // Win rate: 1/3 = 33.3%
    assertEquals(33.3, overall.winRate, 0.1)
  }
}
