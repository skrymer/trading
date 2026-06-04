package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MarketBreadthSustainedWashoutWithinConditionTest {
  private val crisisThreshold = 15.0

  private fun crisisDay(date: LocalDate) = MarketBreadthDaily(quoteDate = date, breadthPercent = 12.0)

  private fun normalDay(date: LocalDate) = MarketBreadthDaily(quoteDate = date, breadthPercent = 55.0)

  private fun contextOf(vararg readings: MarketBreadthDaily) =
    BacktestContext(sectorBreadthMap = emptyMap(), marketBreadthMap = readings.associateBy { it.quoteDate })

  private fun quoteOn(date: LocalDate) = StockQuote(date = date)

  /** Build a run of `count` consecutive crisis readings ending on `lastDate` (going back day-by-day). */
  private fun crisisRun(lastDate: LocalDate, count: Int): List<MarketBreadthDaily> =
    (0 until count).map { crisisDay(lastDate.minusDays(it.toLong())) }

  @Test
  fun `passes when breadth held below the floor for the required consecutive run within the window`() {
    // Given: 3 consecutive crisis days, then breadth recovered (today is normal)
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      *crisisRun(LocalDate.of(2009, 3, 11), 3).toTypedArray(),
      normalDay(LocalDate.of(2009, 3, 12)),
      normalDay(LocalDate.of(2009, 3, 13)),
      normalDay(today),
    )
    val condition = MarketBreadthSustainedWashoutWithinCondition(threshold = crisisThreshold, consecutiveDays = 3, lookbackDays = 40)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: a sustained washout run is remembered even though the current bar has recovered
    assertTrue(result)
  }

  @Test
  fun `fails when sub-floor touches are scattered and never reach the required consecutive run`() {
    // Given: several days <= floor but each isolated between normal days (longest run = 1), need 2
    val today = LocalDate.of(2009, 3, 20)
    val context = contextOf(
      crisisDay(LocalDate.of(2009, 3, 9)),
      normalDay(LocalDate.of(2009, 3, 10)),
      crisisDay(LocalDate.of(2009, 3, 11)),
      normalDay(LocalDate.of(2009, 3, 12)),
      crisisDay(LocalDate.of(2009, 3, 13)),
      normalDay(LocalDate.of(2009, 3, 16)),
      normalDay(today),
    )
    val condition = MarketBreadthSustainedWashoutWithinCondition(threshold = crisisThreshold, consecutiveDays = 2, lookbackDays = 40)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: scattered single touches are routine pullbacks, not a sustained washout
    assertFalse(result)
  }

  @Test
  fun `ignores a sustained run that occurs after the evaluated bar (no lookahead)`() {
    // Given: today is normal; a qualifying 3-day run exists only on FUTURE readings
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      normalDay(LocalDate.of(2009, 3, 13)),
      normalDay(today),
      *crisisRun(LocalDate.of(2009, 3, 19), 3).toTypedArray(),
    )
    val condition = MarketBreadthSustainedWashoutWithinCondition(threshold = crisisThreshold, consecutiveDays = 3, lookbackDays = 40)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: future data is invisible on the evaluated bar
    assertFalse(result)
  }

  @Test
  fun `fails when the qualifying run is older than the lookback window`() {
    // Given: lookbackDays=5; a 3-day run on the oldest readings, then 6 normal readings push it out
    val today = LocalDate.of(2009, 3, 24)
    val context = contextOf(
      *crisisRun(LocalDate.of(2009, 3, 11), 3).toTypedArray(),
      normalDay(LocalDate.of(2009, 3, 12)),
      normalDay(LocalDate.of(2009, 3, 13)),
      normalDay(LocalDate.of(2009, 3, 16)),
      normalDay(LocalDate.of(2009, 3, 17)),
      normalDay(LocalDate.of(2009, 3, 18)),
      normalDay(today),
    )
    val condition = MarketBreadthSustainedWashoutWithinCondition(threshold = crisisThreshold, consecutiveDays = 3, lookbackDays = 5)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: the run has aged out of the lookback window
    assertFalse(result)
  }

  @Test
  fun `fails when the longest run is one short of the required length`() {
    // Given: a 2-day consecutive run but 3 are required
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      *crisisRun(LocalDate.of(2009, 3, 11), 2).toTypedArray(),
      normalDay(LocalDate.of(2009, 3, 12)),
      normalDay(LocalDate.of(2009, 3, 13)),
      normalDay(today),
    )
    val condition = MarketBreadthSustainedWashoutWithinCondition(threshold = crisisThreshold, consecutiveDays = 3, lookbackDays = 40)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: a run below the required length does not qualify
    assertFalse(result)
  }

  @Test
  fun `parseConfig reflects custom threshold, consecutiveDays and lookbackDays`() {
    // Given: the discovered singleton (default-bearer)
    val singleton = MarketBreadthSustainedWashoutWithinCondition()

    // When
    val parsed = singleton.parseConfig(mapOf("threshold" to 10.0, "consecutiveDays" to 8, "lookbackDays" to 30))

    // Then: description encodes the parsed values
    assertEquals(
      "Market breadth held <= 10.0% for >= 8 consecutive days within last 30 days",
      parsed.description(),
    )
  }

  @Test
  fun `parseConfig with empty parameters falls back to defaults`() {
    // Given
    val singleton = MarketBreadthSustainedWashoutWithinCondition()

    // When
    val parsed = singleton.parseConfig(emptyMap())

    // Then
    assertEquals(singleton.description(), parsed.description())
  }
}
