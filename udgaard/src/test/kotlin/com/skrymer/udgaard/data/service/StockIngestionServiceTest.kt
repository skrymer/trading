package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardSymbolDto
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.OvtlyrSignal
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.service.UserSettingsJooqRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class StockIngestionServiceTest {
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var stockProvider: StockProvider
  private lateinit var technicalIndicatorService: TechnicalIndicatorService
  private lateinit var orderBlockCalculator: OrderBlockCalculator
  private lateinit var midgaardClient: MidgaardClient
  private lateinit var sectorBreadthService: SectorBreadthService
  private lateinit var marketBreadthService: MarketBreadthService
  private lateinit var marketBreadthRepository: MarketBreadthRepository
  private lateinit var userSettingsRepository: UserSettingsJooqRepository

  private val fixedInstant = LocalDateTime.of(2026, 5, 19, 14, 30).toInstant(ZoneOffset.UTC)
  private val fixedClock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

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
    userSettingsRepository = mock()

    service = StockIngestionService(
      stockRepository = stockRepository,
      stockProvider = stockProvider,
      technicalIndicatorService = technicalIndicatorService,
      orderBlockCalculator = orderBlockCalculator,
      midgaardClient = midgaardClient,
      sectorBreadthService = sectorBreadthService,
      marketBreadthService = marketBreadthService,
      marketBreadthRepository = marketBreadthRepository,
      userSettingsRepository = userSettingsRepository,
      clock = fixedClock,
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
  fun `lastRefreshedAt reads the wall-clock timestamp persisted via user_settings`() {
    // Given: a persisted timestamp from a prior refresh
    whenever(userSettingsRepository.findByKey("data.last_refreshed_at"))
      .thenReturn("2026-05-19T13:45:30")

    // When
    val result = service.lastRefreshedAt

    // Then
    assertEquals(LocalDateTime.of(2026, 5, 19, 13, 45, 30), result)
  }

  @Test
  fun `lastRefreshedAt returns null when no refresh has been recorded`() {
    // Given
    whenever(userSettingsRepository.findByKey("data.last_refreshed_at")).thenReturn(null)

    // When
    val result = service.lastRefreshedAt

    // Then
    assertNull(result)
  }

  @Test
  fun `lastRefreshedAt does NOT fall back to MAX(quote_date) — that's the bug being fixed`() {
    // Given: no persisted timestamp, but stock_quotes has bars (the pre-fix initializer
    // would have seeded from this and called it "refreshed")
    whenever(userSettingsRepository.findByKey("data.last_refreshed_at")).thenReturn(null)

    // When
    val result = service.lastRefreshedAt

    // Then: no read from stockRepository.getLatestQuoteTimestamp() — the two concepts
    // (bar date vs wall-clock refresh) must not be conflated
    assertNull(result)
    verify(stockRepository, never()).getLatestQuoteTimestamp()
  }

  @Test
  fun `lastRefreshedAt degrades to null when the persisted value is unparseable`() {
    // Given: a corrupt value (e.g., from a future schema change or manual DB edit)
    whenever(userSettingsRepository.findByKey("data.last_refreshed_at")).thenReturn("not-a-timestamp")

    // When / Then: getter must not throw — a 500 on the data-manager page would be worse
    // than "unknown"
    assertNull(service.lastRefreshedAt)
  }

  @Test
  fun `refreshStock writes the wall-clock timestamp on success`() {
    // Given: a successful single-symbol refresh
    stubSuccessfulFetch()
    whenever(stockRepository.save(any())).thenAnswer { it.arguments[0] }

    // When
    service.refreshStock("AAPL")

    // Then: the fixed clock's value (2026-05-19T14:30) is persisted
    verify(userSettingsRepository).upsert("data.last_refreshed_at", "2026-05-19T14:30")
  }

  @Test
  fun `refreshStock does NOT write the timestamp when the provider returns null`() {
    // Given: provider miss → refreshStock returns null without saving
    whenever(stockProvider.getDailyAdjustedTimeSeries(any())).thenReturn(null)

    // When
    val result = service.refreshStock("UNKNOWN")

    // Then: nothing persisted — a failed refresh shouldn't update "last refreshed"
    assertNull(result)
    verify(userSettingsRepository, never()).upsert(any(), any(), anyOrNull())
  }

  @Test
  fun `fetchAndBuildStock attaches ovtlyr signals from Midgaard`() {
    // Given: Midgaard has a Buy signal for the symbol
    stubSuccessfulFetch()
    val signals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 11), OvtlyrSignalType.BUY))
    whenever(midgaardClient.getOvtlyrSignals("AAPL")).thenReturn(signals)

    // When
    val result = service.fetchAndBuildStock("AAPL")

    // Then: the signals are carried onto the built Stock
    assertEquals(signals, result?.ovtlyrSignals)
  }

  @Test
  fun `fetchAndBuildStock yields empty ovtlyr signals when Midgaard returns none, stock still builds`() {
    // Given: Midgaard has no ovtlyr coverage for the symbol (fetch returns null)
    stubSuccessfulFetch()
    whenever(midgaardClient.getOvtlyrSignals("AAPL")).thenReturn(null)

    // When
    val result = service.fetchAndBuildStock("AAPL")

    // Then: the build succeeds with an empty signal list rather than failing
    assertNotNull(result)
    assertEquals(emptyList<OvtlyrSignal>(), result!!.ovtlyrSignals)
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

  @Test
  fun `fetchAndBuildStock stamps the asset type from the Midgaard symbol info`() {
    // Given Midgaard classifies the symbol as a leveraged ETF
    stubSuccessfulFetch()
    whenever(midgaardClient.getSymbolInfo("LABU"))
      .thenReturn(MidgaardSymbolDto(symbol = "LABU", assetType = "LEVERAGED_ETF", sector = null))

    // When the stock is built
    val result = service.fetchAndBuildStock("LABU")

    // Then the asset type is carried onto the built Stock
    assertEquals(AssetType.LEVERAGED_ETF, result?.assetType)
  }

  @Test
  fun `fetchAndBuildStock keeps the stock with a null asset type when Midgaard sends an unknown value`() {
    // Given Midgaard reports an asset type Udgaard's enum does not know
    stubSuccessfulFetch()
    whenever(midgaardClient.getSymbolInfo("WTF"))
      .thenReturn(MidgaardSymbolDto(symbol = "WTF", assetType = "MUTUAL_FUND", sector = null))

    // When the stock is built
    val result = service.fetchAndBuildStock("WTF")

    // Then the stock still ingests (not silently dropped); the unknown type is stored as null
    assertNotNull(result)
    assertNull(result!!.assetType)
  }

  @Test
  fun `reconcileAndRefreshAll prunes stocks absent from the Midgaard catalogue and queues the catalogue`() {
    // Given the local universe still holds a drifted-dead ticker the catalogue no longer lists
    whenever(midgaardClient.getAllSymbols()).thenReturn(
      listOf(
        MidgaardSymbolDto(symbol = "AAPL", assetType = "STOCK", sector = null),
        MidgaardSymbolDto(symbol = "MSFT", assetType = "STOCK", sector = null),
      ),
    )
    whenever(stockRepository.findAllSymbols()).thenReturn(listOf("AAPL", "MSFT", "CIGA"))

    // When the full-universe reconcile runs
    val result = service.reconcileAndRefreshAll()

    // Then the drifted ticker is pruned and the live catalogue is queued for refresh
    verify(stockRepository).batchDelete(listOf("CIGA"))
    assertTrue(result.reconciled)
    assertEquals(2, result.queued)
    assertEquals(1, result.pruned)

    service.clearQueue()
  }

  @Test
  fun `reconcileAndRefreshAll leaves the universe untouched when the catalogue lookup returns null`() {
    // Given the catalogue source is unreachable (returns null)
    whenever(midgaardClient.getAllSymbols()).thenReturn(null)

    // When the full-universe reconcile runs
    val result = service.reconcileAndRefreshAll()

    // Then nothing is pruned, nothing is queued, and the local stocks are not even inspected
    assertFalse(result.reconciled)
    assertEquals(0, result.queued)
    assertEquals(0, result.pruned)
    verify(stockRepository, never()).batchDelete(any())
    verify(stockRepository, never()).findAllSymbols()
  }

  @Test
  fun `reconcileAndRefreshAll leaves the universe untouched when the catalogue is empty`() {
    // Given the catalogue source returns an empty list (e.g. mid-redeploy)
    whenever(midgaardClient.getAllSymbols()).thenReturn(emptyList())

    // When the full-universe reconcile runs
    val result = service.reconcileAndRefreshAll()

    // Then a single bad response cannot wipe the universe
    assertFalse(result.reconciled)
    assertEquals(0, result.queued)
    assertEquals(0, result.pruned)
    verify(stockRepository, never()).batchDelete(any())
    verify(stockRepository, never()).findAllSymbols()
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
