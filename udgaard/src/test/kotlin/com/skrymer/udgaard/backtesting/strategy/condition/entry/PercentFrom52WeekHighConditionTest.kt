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

class PercentFrom52WeekHighConditionTest {
  private val today = LocalDate.of(2024, 1, 5)

  @Test
  fun `should pass when price is within the allowed percentage of the 52-week high`() {
    // Given close 80 against a 52-week high of 100 (20% below, within 25%)
    val condition = PercentFrom52WeekHighCondition(maxPercentBelowHigh = 25.0)
    val stock =
      Stock(
        quotes = mutableListOf(StockQuote(date = today, closePrice = 80.0, high52Week = 100.0)),
      )

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it passes
    assertTrue(result)
  }

  @Test
  fun `should fail when price is further below the high than allowed`() {
    // Given close 70 against a high of 100 (30% below, beyond 25%)
    val condition = PercentFrom52WeekHighCondition(maxPercentBelowHigh = 25.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 70.0, high52Week = 100.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it fails
    assertFalse(result)
  }

  @Test
  fun `should pass at the boundary distance`() {
    // Given close exactly 25% below the high
    val condition = PercentFrom52WeekHighCondition(maxPercentBelowHigh = 25.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 75.0, high52Week = 100.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the boundary (exactly 25%) passes
    assertTrue(result)
  }

  @Test
  fun `should pass when price is making a new high`() {
    // Given close above the recorded 52-week high (negative distance)
    val condition = PercentFrom52WeekHighCondition(maxPercentBelowHigh = 25.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 105.0, high52Week = 100.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then being at/above the high passes
    assertTrue(result)
  }

  @Test
  fun `should fail when the 52-week high is unavailable`() {
    // Given a missing high52Week (insufficient history)
    val condition = PercentFrom52WeekHighCondition(maxPercentBelowHigh = 25.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 90.0, high52Week = null)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it fails
    assertFalse(result)
  }

  @Test
  fun `evaluateWithDetails agrees with evaluate and reports missing history`() {
    // Given a missing high52Week
    val condition = PercentFrom52WeekHighCondition()
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 90.0, high52Week = null)))

    // When the detailed result is produced
    val result = condition.evaluateWithDetails(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it matches evaluate and explains the missing data
    assertFalse(result.passed)
    assertEquals(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY), result.passed)
    assertTrue(result.message!!.contains("not available"))
  }

  @Test
  fun `should expose default metadata`() {
    // Given the default condition (25% within high)
    val metadata = PercentFrom52WeekHighCondition().getMetadata()

    // Then it advertises the within-25%-of-high filter
    assertEquals("percentFrom52WeekHigh", metadata.type)
    assertEquals("Trend", metadata.category)
    assertEquals(1, metadata.parameters.size)
    assertEquals("Price within 25% of 52-week high", PercentFrom52WeekHighCondition().description())
  }

  @Test
  fun `should reject a negative distance threshold`() {
    // Given a nonsensical negative percentage
    // When constructed, Then it fails loudly
    assertThrows(IllegalArgumentException::class.java) {
      PercentFrom52WeekHighCondition(maxPercentBelowHigh = -1.0)
    }
  }
}
