package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

private const val SVC_EPSILON = 1e-9

class RiskFreeRateServiceTest {
  private lateinit var midgaardClient: MidgaardClient
  private lateinit var service: RiskFreeRateService

  @BeforeEach
  fun setUp() {
    midgaardClient = mock()
    service = RiskFreeRateService(midgaardClient)
  }

  @Test
  fun `loadProvider builds a provider from the fetched gross series, net of expense`() {
    // Given Midgaard serves the gross US3M series
    whenever(midgaardClient.getTreasuryYields("US3M"))
      .thenReturn(mapOf(LocalDate.of(2025, 5, 1) to 4.2931))

    // When loading the provider with the SGOV expense
    val provider = service.loadProvider(expensePct = 0.10)

    // Then the provider reflects the fetched series net of the expense once
    assertEquals(0.0419310, provider.netAnnualRate(LocalDate.of(2025, 5, 15)), SVC_EPSILON)
  }

  @Test
  fun `loadProvider falls back to a zero-rate provider when the series is unavailable`() {
    // Given Midgaard cannot serve the series (null) — never silently credit a wrong rate
    whenever(midgaardClient.getTreasuryYields(any())).thenReturn(null)

    // When loading the provider
    val provider = service.loadProvider(expensePct = 0.10)

    // Then it falls back to a 0pct rate everywhere (the loud-fallback contract)
    assertEquals(0.0, provider.netAnnualRate(LocalDate.of(2025, 5, 15)), SVC_EPSILON)
  }
}
