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

class MarketBreadthWashoutWithinConditionTest {
  private val washoutPercentile = 0.10

  /** Breadth reading whose breadthPercent sits at the Donchian-low washout threshold (bottom 10%). */
  private fun washoutDay(date: LocalDate) =
    MarketBreadthDaily(quoteDate = date, breadthPercent = 12.0, donchianUpperBand = 100.0, donchianLowerBand = 10.0)

  /** Breadth reading well above the washout zone (a recovered / healthy bar). */
  private fun healthyDay(date: LocalDate) =
    MarketBreadthDaily(quoteDate = date, breadthPercent = 60.0, donchianUpperBand = 100.0, donchianLowerBand = 10.0)

  private fun contextOf(vararg readings: MarketBreadthDaily) =
    BacktestContext(sectorBreadthMap = emptyMap(), marketBreadthMap = readings.associateBy { it.quoteDate })

  private fun quoteOn(date: LocalDate) = StockQuote(date = date)

  @Test
  fun `passes when breadth was washed out earlier in the window even though today is recovering`() {
    // Given: a washout 5 trading days ago, with breadth recovered (healthy) since, including today
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      washoutDay(LocalDate.of(2009, 3, 9)),
      healthyDay(LocalDate.of(2009, 3, 10)),
      healthyDay(LocalDate.of(2009, 3, 13)),
      healthyDay(today),
    )
    val condition = MarketBreadthWashoutWithinCondition(percentile = washoutPercentile, lookbackDays = 15)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: the recent washout is remembered even though the current bar is no longer a washout
    assertTrue(result)
  }

  @Test
  fun `fails when the only washout is older than the lookback window`() {
    // Given: lookbackDays=5, a washout on the oldest reading and 6 healthy readings after it
    // (so takeLast(5) drops the washout out of the remembered window)
    val today = LocalDate.of(2009, 3, 20)
    val context = contextOf(
      washoutDay(LocalDate.of(2009, 3, 9)),
      healthyDay(LocalDate.of(2009, 3, 10)),
      healthyDay(LocalDate.of(2009, 3, 11)),
      healthyDay(LocalDate.of(2009, 3, 12)),
      healthyDay(LocalDate.of(2009, 3, 13)),
      healthyDay(LocalDate.of(2009, 3, 16)),
      healthyDay(today),
    )
    val condition = MarketBreadthWashoutWithinCondition(percentile = washoutPercentile, lookbackDays = 5)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: the washout has aged out of the window
    assertFalse(result)
  }

  @Test
  fun `fails when no reading in the window was a washout`() {
    // Given: only healthy readings within the window
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      healthyDay(LocalDate.of(2009, 3, 12)),
      healthyDay(LocalDate.of(2009, 3, 13)),
      healthyDay(today),
    )
    val condition = MarketBreadthWashoutWithinCondition(percentile = washoutPercentile, lookbackDays = 15)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then
    assertFalse(result)
  }

  @Test
  fun `passes when the current bar itself is a washout`() {
    // Given: today's reading is the washout (window is inclusive of the current bar)
    val today = LocalDate.of(2009, 3, 9)
    val context = contextOf(
      healthyDay(LocalDate.of(2009, 3, 5)),
      healthyDay(LocalDate.of(2009, 3, 6)),
      washoutDay(today),
    )
    val condition = MarketBreadthWashoutWithinCondition(percentile = washoutPercentile, lookbackDays = 15)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then
    assertTrue(result)
  }

  @Test
  fun `ignores a washout that occurs after the evaluated bar (no lookahead)`() {
    // Given: today is healthy; a washout exists only on a FUTURE reading
    val today = LocalDate.of(2009, 3, 16)
    val context = contextOf(
      healthyDay(LocalDate.of(2009, 3, 13)),
      healthyDay(today),
      washoutDay(LocalDate.of(2009, 3, 17)),
    )
    val condition = MarketBreadthWashoutWithinCondition(percentile = washoutPercentile, lookbackDays = 15)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), context)

    // Then: future data is invisible on the evaluated bar
    assertFalse(result)
  }

  @Test
  fun `does not count a reading with a degenerate Donchian range as a washout`() {
    // Given: a low breadthPercent but upper == lower (range 0) — channel not yet established
    val today = LocalDate.of(2009, 3, 16)
    val degenerate = MarketBreadthDaily(
      quoteDate = today,
      breadthPercent = 2.0,
      donchianUpperBand = 50.0,
      donchianLowerBand = 50.0,
    )
    val condition = MarketBreadthWashoutWithinCondition(percentile = washoutPercentile, lookbackDays = 15)

    // When
    val result = condition.evaluate(Stock(), quoteOn(today), contextOf(degenerate))

    // Then: a zero-width channel yields no washout signal
    assertFalse(result)
  }

  @Test
  fun `parseConfig reflects custom percentile and lookbackDays`() {
    // Given: the discovered singleton (default-bearer)
    val singleton = MarketBreadthWashoutWithinCondition()

    // When: a wire config supplies both parameters
    val parsed = singleton.parseConfig(mapOf("percentile" to 0.05, "lookbackDays" to 20))

    // Then: description encodes the parsed values (bottom 5%, last 20 days)
    assertEquals(
      "Market breadth washed out (bottom 5%) within last 20 days",
      parsed.description(),
    )
  }

  @Test
  fun `parseConfig with empty parameters falls back to defaults`() {
    // Given
    val singleton = MarketBreadthWashoutWithinCondition()

    // When
    val parsed = singleton.parseConfig(emptyMap())

    // Then
    assertEquals(singleton.description(), parsed.description())
  }
}
