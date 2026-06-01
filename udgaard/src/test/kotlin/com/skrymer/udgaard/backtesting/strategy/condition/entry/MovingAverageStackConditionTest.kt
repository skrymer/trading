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

class MovingAverageStackConditionTest {
  private val today = LocalDate.of(2024, 1, 5)

  @Test
  fun `should pass when price and SMAs form a descending Minervini stack`() {
    // Given a bar where close > sma50 > sma150 > sma200
    val condition = MovingAverageStackCondition()
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = today, closePrice = 110.0, sma50 = 100.0, sma150 = 90.0, sma200 = 80.0),
          ),
      )

    // When the condition evaluates the bar
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the stack gate passes
    assertTrue(result)
  }

  @Test
  fun `should fail when the moving averages are not in descending order`() {
    // Given sma150 above sma50 (stack out of order)
    val condition = MovingAverageStackCondition()
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = today, closePrice = 110.0, sma50 = 90.0, sma150 = 100.0, sma200 = 80.0),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the gate fails
    assertFalse(result)
  }

  @Test
  fun `should fail when price is below the fast MA and price gate is required`() {
    // Given a correctly stacked set of MAs but price below sma50
    val condition = MovingAverageStackCondition(requirePriceAboveFast = true)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = today, closePrice = 95.0, sma50 = 100.0, sma150 = 90.0, sma200 = 80.0),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the gate fails on the price criterion
    assertFalse(result)
  }

  @Test
  fun `should ignore price when the price gate is disabled`() {
    // Given price below sma50 but the MA stack itself is ordered, with the price gate off
    val condition = MovingAverageStackCondition(requirePriceAboveFast = false)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = today, closePrice = 95.0, sma50 = 100.0, sma150 = 90.0, sma200 = 80.0),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then only the MA ordering matters, so it passes
    assertTrue(result)
  }

  @Test
  fun `should fail when a required SMA is missing (insufficient history)`() {
    // Given a full price/stack but sma200 unavailable (less than 200 bars of history)
    val condition = MovingAverageStackCondition()
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = today, closePrice = 110.0, sma50 = 100.0, sma150 = 90.0, sma200 = null),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the missing MA forces a fail
    assertFalse(result)
  }

  @Test
  fun `should fail when MAs are equal because ordering is strict`() {
    // Given sma50 == sma150 (not strictly descending)
    val condition = MovingAverageStackCondition()
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = today, closePrice = 110.0, sma50 = 100.0, sma150 = 100.0, sma200 = 80.0),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then strict ordering rejects the equal pair
    assertFalse(result)
  }

  @Test
  fun `should read EMA fields when MA type is EMA`() {
    // Given an EMA-typed stack with descending EMA50 > EMA100 > EMA200
    val condition = MovingAverageStackCondition(maType = "EMA", fastPeriod = 50, midPeriod = 100, slowPeriod = 200)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(
              date = today,
              closePrice = 110.0,
              closePriceEMA50 = 100.0,
              closePriceEMA100 = 90.0,
              ema200 = 80.0,
            ),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the EMA stack passes
    assertTrue(result)
  }

  @Test
  fun `should fail when an EMA field is the zero placeholder (unavailable)`() {
    // Given ema200 still at its 0.0 default (not yet computed)
    val condition = MovingAverageStackCondition(maType = "EMA", fastPeriod = 50, midPeriod = 100, slowPeriod = 200)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(
              date = today,
              closePrice = 110.0,
              closePriceEMA50 = 100.0,
              closePriceEMA100 = 90.0,
              ema200 = 0.0,
            ),
          ),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the zero placeholder is treated as missing history and fails
    assertFalse(result)
  }

  @Test
  fun `should round-trip every parameter through parseConfig`() {
    // Given a parameter map overriding all defaults
    val base = MovingAverageStackCondition()
    val params =
      mapOf<String, Any>(
        "maType" to "EMA",
        "fastPeriod" to 20,
        "midPeriod" to 50,
        "slowPeriod" to 100,
        "requirePriceAboveFast" to false,
      )

    // When parsed
    val parsed = base.parseConfig(params) as MovingAverageStackCondition

    // Then the description reflects the overridden config
    assertEquals("EMA20 > EMA50 > EMA100", parsed.description())
  }

  @Test
  fun `evaluateWithDetails agrees with evaluate and reports missing history`() {
    // Given a bar missing sma150
    val condition = MovingAverageStackCondition()
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = today, closePrice = 110.0, sma50 = 100.0, sma150 = null, sma200 = 80.0),
          ),
      )

    // When the detailed result is produced
    val result = condition.evaluateWithDetails(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it matches evaluate and explains the missing data
    assertFalse(result.passed)
    assertEquals(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY), result.passed)
    assertTrue(result.message!!.contains("not available"))
  }

  @Test
  fun `should expose Minervini default metadata`() {
    // Given the default condition
    val metadata = MovingAverageStackCondition().getMetadata()

    // Then it advertises the SMA 50/150/200 trend filter
    assertEquals("movingAverageStack", metadata.type)
    assertEquals("Trend", metadata.category)
    assertEquals(5, metadata.parameters.size)
    assertEquals("Price > SMA50 > SMA150 > SMA200", MovingAverageStackCondition().description())
  }

  @Test
  fun `should reject a period the configured MA type does not provide`() {
    // Given an SMA stack asking for SMA100, which is not persisted (no sma100 field)
    // When constructed (as parseConfig would), Then it fails loudly rather than silently never passing
    assertThrows(IllegalArgumentException::class.java) {
      MovingAverageStackCondition(maType = "SMA", fastPeriod = 100, midPeriod = 150, slowPeriod = 200)
    }
  }

  @Test
  fun `should reject an unknown MA type`() {
    // Given an unsupported MA type
    // When constructed, Then it fails loudly
    assertThrows(IllegalArgumentException::class.java) {
      MovingAverageStackCondition(maType = "WMA")
    }
  }
}
