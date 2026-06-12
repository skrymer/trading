package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.backtesting.service.RegimeReadoutService
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
  private val newYork = ZoneId.of("America/New_York")
  private val clock = Clock.fixed(Instant.parse("2026-06-11T20:00:00Z"), newYork)
  private val controller = RegimeController(regimeReadoutService, clock)

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
}
