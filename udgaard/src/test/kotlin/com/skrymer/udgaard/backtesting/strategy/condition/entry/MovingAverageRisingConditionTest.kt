package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MovingAverageRisingConditionTest {
  private fun day(n: Int) = LocalDate.of(2024, 1, n)

  @Test
  fun `should pass when the MA is higher than it was lookback bars ago`() {
    // Given an SMA200 that has risen over the last 3 bars
    val condition = MovingAverageRisingCondition(period = 200, lookbackBars = 3)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = day(1), sma200 = 80.0),
            StockQuote(date = day(2), sma200 = 82.0),
            StockQuote(date = day(3), sma200 = 84.0),
            StockQuote(date = day(4), sma200 = 90.0),
          ),
      )

    // When evaluating the latest bar
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it passes because today's MA (90) exceeds the MA 3 bars ago (80)
    assertTrue(result)
  }

  @Test
  fun `should fail when the MA is lower than it was lookback bars ago`() {
    // Given an SMA200 that has fallen over the last 3 bars
    val condition = MovingAverageRisingCondition(period = 200, lookbackBars = 3)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = day(1), sma200 = 100.0),
            StockQuote(date = day(2), sma200 = 95.0),
            StockQuote(date = day(3), sma200 = 92.0),
            StockQuote(date = day(4), sma200 = 90.0),
          ),
      )

    // When evaluating the latest bar
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it fails because today's MA (90) is below the MA 3 bars ago (100)
    assertFalse(result)
  }

  @Test
  fun `should fail when the MA is flat because rising is strict`() {
    // Given an SMA200 that is identical 3 bars ago and today
    val condition = MovingAverageRisingCondition(period = 200, lookbackBars = 3)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = day(1), sma200 = 90.0),
            StockQuote(date = day(2), sma200 = 91.0),
            StockQuote(date = day(3), sma200 = 89.0),
            StockQuote(date = day(4), sma200 = 90.0),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then a flat MA (90 == 90) does not count as rising
    assertFalse(result)
  }

  @Test
  fun `should fail when there are not enough bars to look back over`() {
    // Given only 3 bars but a lookback of 3 (need a 4th, lookbackBars ago)
    val condition = MovingAverageRisingCondition(period = 200, lookbackBars = 3)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = day(1), sma200 = 80.0),
            StockQuote(date = day(2), sma200 = 85.0),
            StockQuote(date = day(3), sma200 = 90.0),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then insufficient history fails
    assertFalse(result)
  }

  @Test
  fun `should fail when the current MA is unavailable`() {
    // Given today's sma200 missing
    val condition = MovingAverageRisingCondition(period = 200, lookbackBars = 3)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = day(1), sma200 = 80.0),
            StockQuote(date = day(2), sma200 = 82.0),
            StockQuote(date = day(3), sma200 = 84.0),
            StockQuote(date = day(4), sma200 = null),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the missing current MA fails
    assertFalse(result)
  }

  @Test
  fun `should not look past the evaluated bar`() {
    // Given a falling series up to day 4, then a sharp rise afterwards
    val condition = MovingAverageRisingCondition(period = 200, lookbackBars = 3)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = day(1), sma200 = 100.0),
            StockQuote(date = day(2), sma200 = 96.0),
            StockQuote(date = day(3), sma200 = 93.0),
            StockQuote(date = day(4), sma200 = 90.0),
            StockQuote(date = day(5), sma200 = 200.0),
            StockQuote(date = day(6), sma200 = 250.0),
          ),
      )

    // When evaluating day 4 (not the last bar)
    val result = condition.evaluate(stock, stock.getQuoteByDate(day(4))!!, BacktestContext.EMPTY)

    // Then only bars up to day 4 are considered (90 < 100), so the later rise is ignored
    assertFalse(result)
  }

  @Test
  fun `should round-trip every parameter through parseConfig`() {
    // Given a parameter map overriding all defaults
    val params = mapOf<String, Any>("maType" to "EMA", "period" to 50, "lookbackBars" to 10)

    // When parsed
    val parsed = MovingAverageRisingCondition().parseConfig(params) as MovingAverageRisingCondition

    // Then the description reflects the overridden config
    assertEquals("EMA50 rising over 10 bars", parsed.description())
  }

  @Test
  fun `should expose default metadata`() {
    // Given the default condition
    val metadata = MovingAverageRisingCondition().getMetadata()

    // Then it advertises the rising SMA200 trend-slope filter
    assertEquals("movingAverageRising", metadata.type)
    assertEquals("Trend", metadata.category)
    assertEquals(3, metadata.parameters.size)
    assertEquals("SMA200 rising over 30 bars", MovingAverageRisingCondition().description())
  }

  @Test
  fun `should reject a non-positive lookback`() {
    // Given a zero lookback (current bar compared to itself) — and a negative one would index forward
    // When constructed, Then it fails loudly rather than admitting a config-injected lookahead
    assertThrows(IllegalArgumentException::class.java) { MovingAverageRisingCondition(lookbackBars = 0) }
    assertThrows(IllegalArgumentException::class.java) { MovingAverageRisingCondition(lookbackBars = -5) }
  }

  @Test
  fun `should reject a period the configured MA type does not provide`() {
    // Given an EMA period of 150, which has no persisted field
    // When constructed, Then it fails loudly
    assertThrows(IllegalArgumentException::class.java) {
      MovingAverageRisingCondition(maType = "EMA", period = 150)
    }
  }
}
