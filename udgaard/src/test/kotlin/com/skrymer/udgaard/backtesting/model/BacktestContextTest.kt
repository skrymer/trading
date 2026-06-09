package com.skrymer.udgaard.backtesting.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BacktestContextTest {
  private fun regime(date: LocalDate, on: Boolean) =
    LeadershipRegimeDaily(
      quoteDate = date,
      gap = 0.0,
      gapSmoothed = 0.0,
      schmittOn = on,
      washoutActive = false,
      regimeOn = on,
      contributingN = 5000,
      standardError = 0.001,
      trustworthy = true,
    )

  @Test
  fun `getLeadershipRegimeOn returns the precomputed regime for a known date`() {
    // Given: a context whose regime is ON for one date and OFF for another
    val deploy = LocalDate.of(2020, 6, 1)
    val cash = LocalDate.of(2020, 6, 2)
    val context =
      BacktestContext(
        sectorBreadthMap = emptyMap(),
        marketBreadthMap = emptyMap(),
        leadershipRegimeMap = mapOf(deploy to regime(deploy, true), cash to regime(cash, false)),
      )

    // When / Then
    assertTrue(context.getLeadershipRegimeOn(deploy))
    assertFalse(context.getLeadershipRegimeOn(cash))
  }

  @Test
  fun `getLeadershipRegimeOn defaults to cash for a date with no regime read`() {
    // Given: a context with no regime entry for the queried date
    val context = BacktestContext(sectorBreadthMap = emptyMap(), marketBreadthMap = emptyMap())

    // When / Then: a missing read cannot confirm a deploy regime, so the gate stays in cash
    assertFalse(context.getLeadershipRegimeOn(LocalDate.of(2020, 6, 1)))
  }
}
