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

class MarketBreadthAbsoluteWashoutWithinConditionTest {
  private val crisisThreshold = 15.0

  /** Breadth reading at an absolute crisis floor (bull-% in the teens). */
  private fun crisisDay(date: LocalDate) = MarketBreadthDaily(quoteDate = date, breadthPercent = 12.0)

  /** Breadth reading well above the crisis floor (a recovered / normal bar). */
  private fun normalDay(date: LocalDate) = MarketBreadthDaily(quoteDate = date, breadthPercent = 55.0)

  private fun contextOf(vararg readings: MarketBreadthDaily) =
    BacktestContext(sectorBreadthMap = emptyMap(), marketBreadthMap = readings.associateBy { it.quoteDate })

  private fun quoteOn(date: LocalDate) = StockQuote(date = date)

  @Test
  fun `passes when breadth hit an absolute crisis floor earlier in the window even though today has recovered`() {
    // Given: breadth collapsed to 12% a week ago, then recovered above the floor, including today
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      crisisDay(LocalDate.of(2009, 3, 9)),
      normalDay(LocalDate.of(2009, 3, 10)),
      normalDay(LocalDate.of(2009, 3, 13)),
      normalDay(today),
    )
    val condition = MarketBreadthAbsoluteWashoutWithinCondition(threshold = crisisThreshold, lookbackDays = 30)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: the recent absolute washout is remembered even though the current bar is above the floor
    assertTrue(result)
  }

  @Test
  fun `ignores a crisis floor that occurs after the evaluated bar (no lookahead)`() {
    // Given: today is normal; the crisis floor exists only on a FUTURE reading
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      normalDay(LocalDate.of(2009, 3, 13)),
      normalDay(today),
      crisisDay(LocalDate.of(2009, 3, 17)),
    )
    val condition = MarketBreadthAbsoluteWashoutWithinCondition(threshold = crisisThreshold, lookbackDays = 30)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: future data is invisible on the evaluated bar
    assertFalse(result)
  }

  @Test
  fun `fails when the only crisis floor is older than the lookback window`() {
    // Given: lookbackDays=5, a crisis floor on the oldest reading and 6 normal readings after it
    val today = LocalDate.of(2009, 3, 20)
    val context = contextOf(
      crisisDay(LocalDate.of(2009, 3, 9)),
      normalDay(LocalDate.of(2009, 3, 10)),
      normalDay(LocalDate.of(2009, 3, 11)),
      normalDay(LocalDate.of(2009, 3, 12)),
      normalDay(LocalDate.of(2009, 3, 13)),
      normalDay(LocalDate.of(2009, 3, 16)),
      normalDay(today),
    )
    val condition = MarketBreadthAbsoluteWashoutWithinCondition(threshold = crisisThreshold, lookbackDays = 5)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: the washout has aged out of the window
    assertFalse(result)
  }

  @Test
  fun `fails when no reading in the window reached the absolute floor`() {
    // Given: a low-ish but above-floor dip (20%) never crosses the 15% crisis line
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      MarketBreadthDaily(quoteDate = LocalDate.of(2009, 3, 12), breadthPercent = 20.0),
      normalDay(LocalDate.of(2009, 3, 13)),
      normalDay(today),
    )
    val condition = MarketBreadthAbsoluteWashoutWithinCondition(threshold = crisisThreshold, lookbackDays = 30)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: a shallow dip is not an absolute washout
    assertFalse(result)
  }

  @Test
  fun `passes when the current bar itself is at the absolute floor`() {
    // Given: today's reading is the crisis floor (window is inclusive of the current bar)
    val today = LocalDate.of(2009, 3, 9)
    val context = contextOf(
      normalDay(LocalDate.of(2009, 3, 5)),
      normalDay(LocalDate.of(2009, 3, 6)),
      crisisDay(today),
    )
    val condition = MarketBreadthAbsoluteWashoutWithinCondition(threshold = crisisThreshold, lookbackDays = 30)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then
    assertTrue(result)
  }

  @Test
  fun `parseConfig reflects custom threshold and lookbackDays`() {
    // Given: the discovered singleton (default-bearer)
    val singleton = MarketBreadthAbsoluteWashoutWithinCondition()

    // When
    val parsed = singleton.parseConfig(mapOf("threshold" to 20.0, "lookbackDays" to 45))

    // Then: description encodes the parsed values
    assertEquals(
      "Market breadth washed out below 20.0% within last 45 days",
      parsed.description(),
    )
  }

  @Test
  fun `parseConfig with empty parameters falls back to defaults`() {
    // Given
    val singleton = MarketBreadthAbsoluteWashoutWithinCondition()

    // When
    val parsed = singleton.parseConfig(emptyMap())

    // Then
    assertEquals(singleton.description(), parsed.description())
  }
}
