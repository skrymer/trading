package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.service.BacktestResultStore
import com.skrymer.udgaard.backtesting.service.RegimeDecompositionService
import com.skrymer.udgaard.backtesting.service.RegimeReadoutService
import com.skrymer.udgaard.backtesting.service.RegimeSectorCell
import com.skrymer.udgaard.backtesting.service.RegimeSectorMatrix
import com.skrymer.udgaard.backtesting.service.RegimeSectorMatrixService
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RegimeControllerTest {
  private val regimeReadoutService: RegimeReadoutService = mock()
  private val regimeSectorMatrixService: RegimeSectorMatrixService = mock()
  private val backtestResultStore: BacktestResultStore = mock()
  private val newYork = ZoneId.of("America/New_York")
  private val clock = Clock.fixed(Instant.parse("2026-06-11T20:00:00Z"), newYork)
  private val controller =
    RegimeController(regimeReadoutService, RegimeDecompositionService(), regimeSectorMatrixService, backtestResultStore, clock)

  private fun day(date: LocalDate, label: RegimeLabel?) =
    RegimeReadoutDaily(quoteDate = date, rawLabel = label, publishedLabel = label)

  @Test
  fun `the readout endpoint returns the window's daily series in date order`() {
    // Given: a loaded read-out series for the requested window (map order unspecified)
    val after = LocalDate.of(2026, 1, 5)
    val before = LocalDate.of(2026, 1, 7)
    whenever(regimeReadoutService.loadReadoutSeries(eq(after), eq(before), any()))
      .thenReturn(
        mapOf(
          LocalDate.of(2026, 1, 6) to day(LocalDate.of(2026, 1, 6), RegimeLabel.THRUST),
          LocalDate.of(2026, 1, 5) to day(LocalDate.of(2026, 1, 5), RegimeLabel.THRUST),
          LocalDate.of(2026, 1, 7) to day(LocalDate.of(2026, 1, 7), RegimeLabel.NARROW),
        ),
      )

    // When
    val response = controller.getReadout(after, before)

    // Then: the series comes back date-ascending
    val body = requireNotNull(response.body)
    assertEquals(listOf(5, 6, 7).map { LocalDate.of(2026, 1, it) }, body.map { it.quoteDate })
    assertEquals(RegimeLabel.NARROW, body.last().publishedLabel)
  }

  @Test
  fun `the readout endpoint rejects an inverted window`() {
    // Given a window whose start is after its end
    val after = LocalDate.of(2026, 1, 7)
    val before = LocalDate.of(2026, 1, 5)

    // When called, Then 400 rather than loading anything
    val response = controller.getReadout(after, before)
    assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `the current endpoint returns the latest available read as of the NY trading day`() {
    // Given: the clock reads 2026-06-11 in New York, and the latest available read lags a day
    val today = LocalDate.of(2026, 6, 11)
    val latest = day(LocalDate.of(2026, 6, 10), RegimeLabel.NARROW)
    whenever(regimeReadoutService.loadReadoutSeries(any(), any(), any()))
      .thenReturn(
        mapOf(
          LocalDate.of(2026, 6, 9) to day(LocalDate.of(2026, 6, 9), RegimeLabel.NARROW),
          latest.quoteDate to latest,
        ),
      )

    // When
    val response = controller.getCurrent()

    // Then: the latest read comes back, requested over a trailing window ending today that is wide
    // enough to bridge a long weekend and a lagging data refresh
    assertEquals(latest, response.body)
    val window = argumentCaptor<LocalDate>()
    verify(regimeReadoutService).loadReadoutSeries(window.capture(), eq(today), any())
    assertTrue(window.firstValue <= today.minusDays(7))
  }

  @Test
  fun `the current endpoint returns 404 when no read is available`() {
    // Given: no read-out data at all in the trailing window
    whenever(regimeReadoutService.loadReadoutSeries(any(), any(), any())).thenReturn(emptyMap())

    // When / Then
    assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, controller.getCurrent().statusCode)
  }

  @Test
  fun `the decomposition endpoint buckets a stored backtest's trades by regime at entry`() {
    // Given: a stored backtest whose 30 winning trades entered on a THRUST day and whose 5 losers
    // entered on a NARROW day
    val thrustDay = LocalDate.of(2010, 3, 1)
    val narrowDay = LocalDate.of(2023, 6, 1)
    val report: BacktestReport = mock()
    whenever(report.trades).thenReturn(
      (0 until 30).map { tradeOn(thrustDay, profit = 2.0) } + (0 until 5).map { tradeOn(narrowDay, profit = -1.0) },
    )
    whenever(backtestResultStore.get(eq("bt-1"))).thenReturn(report)
    whenever(regimeReadoutService.loadReadoutSeries(eq(thrustDay), eq(narrowDay), any()))
      .thenReturn(
        mapOf(
          thrustDay to day(thrustDay, RegimeLabel.THRUST),
          narrowDay to day(narrowDay, RegimeLabel.NARROW),
        ),
      )

    // When
    val response = controller.getDecomposition("bt-1")

    // Then: the THRUST bucket is inferable (30 trades, +2% on a $100 entry), the NARROW one is not
    val body = requireNotNull(response.body)
    val thrustRow = body.rows.first { it.label == RegimeLabel.THRUST }
    val narrowRow = body.rows.first { it.label == RegimeLabel.NARROW }
    assertEquals(30, thrustRow.tradeCount)
    assertEquals(2.0, thrustRow.edge!!, 1e-9)
    assertEquals(true, narrowRow.insufficient)
  }

  @Test
  fun `the decomposition endpoint returns 404 for an unknown backtest`() {
    // Given
    whenever(backtestResultStore.get(eq("missing"))).thenReturn(null)

    // When / Then
    assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, controller.getDecomposition("missing").statusCode)
  }

  @Test
  fun `the sector-matrix endpoint returns the window's regime x sector matrix`() {
    // Given
    val after = LocalDate.of(2010, 1, 1)
    val before = LocalDate.of(2010, 12, 31)
    val matrix = RegimeSectorMatrix(
      cells = listOf(
        RegimeSectorCell(
          label = RegimeLabel.THRUST,
          sector = "XLK",
          dayCount = 120,
          spellCount = 4,
          annualizedReturn = 0.21,
          annualizedStandardError = 0.06,
        ),
      ),
    )
    whenever(regimeSectorMatrixService.loadMatrix(eq(after), eq(before))).thenReturn(matrix)

    // When / Then: the matrix comes back, and an inverted window is rejected before loading
    assertEquals(matrix, controller.getSectorMatrix(after, before).body)
    assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, controller.getSectorMatrix(before, after).statusCode)
  }

  private fun tradeOn(date: LocalDate, profit: Double) =
    Trade(
      stockSymbol = "AAPL",
      entryQuote = StockQuote(symbol = "AAPL", date = date, closePrice = 100.0),
      quotes = emptyList(),
      exitReason = "test",
      profit = profit,
      startDate = date,
      sector = "XLK",
    )
}
