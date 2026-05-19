package com.skrymer.udgaard.scanner.service

import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.scanner.model.MatchedSymbol
import com.skrymer.udgaard.scanner.model.ScanRun
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.repository.ScanRunJooqRepository
import com.skrymer.udgaard.scanner.repository.ScannerTradeJooqRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for the [CohortDivergenceService] orchestrator. The service asks [CohortWindow]
 * questions — it does not branch on state or compute results itself (per ADR 0001).
 */
class CohortDivergenceServiceTest {
  private lateinit var scanRunRepo: ScanRunJooqRepository
  private lateinit var scannerTradeRepo: ScannerTradeJooqRepository
  private lateinit var service: CohortDivergenceService

  private val today = LocalDate.of(2026, 5, 19)
  private val fixedClock: Clock =
    Clock.fixed(today.atTime(18, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

  @BeforeEach
  fun setup() {
    scanRunRepo = mock()
    scannerTradeRepo = mock()
    service = CohortDivergenceService(scanRunRepo, scannerTradeRepo, fixedClock)
  }

  @Test
  fun `compute returns today's emitted-and-taken counts plus rolling jaccard from the windowed fixture`() {
    // Given: one scan run today with 3 matches; 2 of those 3 were taken as trades.
    val todayScan = ScanRun(
      id = 1L,
      signalDate = today,
      scanTimestamp = LocalDateTime.of(today, java.time.LocalTime.of(18, 30)),
      entryStrategyName = "Vcp",
      exitStrategyName = "VcpExitStrategy",
      rankerName = "SectorEdgeWithTightness",
      totalStocksScanned = 3000,
      matchedSymbols = listOf(
        MatchedSymbol("AAPL", "XLK", 175.0, 3.5, 109.95),
        MatchedSymbol("NFLX", "XLC", 425.0, 8.2, 109.90),
        MatchedSymbol("XOM", "XLE", 110.0, 2.1, 80.00),
      ),
    )
    val tradeAAPL = trade("AAPL", signalDate = today)
    val tradeNFLX = trade("NFLX", signalDate = today)
    whenever(scanRunRepo.findByWindow(any(), eq(today), eq("Vcp"), eq("VcpExitStrategy"), eq("SectorEdgeWithTightness")))
      .thenReturn(listOf(todayScan))
    whenever(scannerTradeRepo.findBySignalDateBetween(any(), eq(today)))
      .thenReturn(listOf(tradeAAPL, tradeNFLX))

    // When: compute with default production-config + 20-day window
    val report = service.compute(DivergenceConfig())

    // Then: today's stats reflect the 3 emitted, 2 taken
    assertEquals(today, report.today.scanDate)
    assertEquals(3, report.today.signalsEmitted)
    assertEquals(2, report.today.signalsTaken)
    // Rolling Jaccard: A = {AAPL, NFLX, XOM}, B = {AAPL, NFLX} → |A∩B|/|A∪B| = 2/3
    assertEquals(2.0 / 3.0, report.rolling.jaccard, 1e-9)
    assertEquals(1, report.scanRunsInWindow)
    // No alert with only 1 day of data
    assertEquals(false, report.alerts.executionDrift)
    assertEquals(false, report.alerts.traderFiltering)
  }

  @Test
  fun `compute rejects windowDays outside 1-365`() {
    // Given / When / Then: zero, negative, and absurd values all 400 before any repo work
    assertFailsWith<IllegalArgumentException> { service.compute(DivergenceConfig(windowDays = 0)) }
    assertFailsWith<IllegalArgumentException> { service.compute(DivergenceConfig(windowDays = -5)) }
    assertFailsWith<IllegalArgumentException> { service.compute(DivergenceConfig(windowDays = 366)) }
  }

  private fun trade(symbol: String, signalDate: LocalDate) = ScannerTrade(
    id = null,
    symbol = symbol,
    sectorSymbol = "XLK",
    instrumentType = InstrumentType.STOCK,
    entryPrice = 100.0,
    entryDate = signalDate.plusDays(1),
    quantity = 100,
    optionType = null,
    strikePrice = null,
    expirationDate = null,
    entryStrategyName = "Vcp",
    exitStrategyName = "VcpExitStrategy",
    notes = null,
    createdAt = LocalDateTime.now(),
    signalDate = signalDate,
  )
}
