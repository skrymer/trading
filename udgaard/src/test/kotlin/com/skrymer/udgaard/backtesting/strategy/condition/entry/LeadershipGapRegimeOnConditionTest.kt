package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.LeadershipRegimeDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LeadershipGapRegimeOnConditionTest {
  private val condition = LeadershipGapRegimeOnCondition()

  private fun contextWithRegime(date: LocalDate, on: Boolean) =
    BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = emptyMap(),
      leadershipRegimeMap =
        mapOf(
          date to
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
            ),
        ),
    )

  @Test
  fun `permits entry when the leadership regime is ON for the bar`() {
    // Given: a context whose regime is ON for today
    val today = LocalDate.of(2021, 3, 10)
    val context = contextWithRegime(today, on = true)

    // When
    val result = condition.evaluate(Stock(), StockQuote(date = today), context)

    // Then
    assertTrue(result)
  }

  @Test
  fun `suppresses entry when the regime is OFF or has no read for the bar`() {
    // Given: one bar in a cash regime, and one bar the regime never scored
    val cashDay = LocalDate.of(2021, 3, 10)
    val cashContext = contextWithRegime(cashDay, on = false)
    val unscoredDay = LocalDate.of(2024, 1, 2)

    // When / Then: only an explicit ON read deploys; OFF and missing both stay in cash
    assertFalse(condition.evaluate(Stock(), StockQuote(date = cashDay), cashContext))
    assertFalse(condition.evaluate(Stock(), StockQuote(date = unscoredDay), cashContext))
  }

  @Test
  fun `evaluateWithDetails reports the same verdict as evaluate`() {
    // Given: an ON bar and an OFF bar
    val day = LocalDate.of(2021, 3, 10)
    val onContext = contextWithRegime(day, on = true)
    val offContext = contextWithRegime(day, on = false)
    val quote = StockQuote(date = day)

    // When / Then: the detailed result's passed flag tracks evaluate() on both
    assertTrue(condition.evaluateWithDetails(Stock(), quote, onContext).passed)
    assertFalse(condition.evaluateWithDetails(Stock(), quote, offContext).passed)
  }

  @Test
  fun `exposes the DSL config key and round-trips an empty parameter map`() {
    // Given / When: the metadata type is the key a strategy config references
    // Then: it matches and parseConfig returns a usable condition (no tunables to round-trip)
    assertEquals("leadershipGapRegimeOn", condition.getMetadata().type)
    assertTrue(condition.parseConfig(emptyMap()) is LeadershipGapRegimeOnCondition)
  }
}
