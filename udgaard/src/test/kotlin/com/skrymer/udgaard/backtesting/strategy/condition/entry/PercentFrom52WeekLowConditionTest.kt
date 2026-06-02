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

class PercentFrom52WeekLowConditionTest {
  private val today = LocalDate.of(2024, 1, 5)

  @Test
  fun `should pass when price is sufficiently above the 52-week low`() {
    // Given close 130 against a 52-week low of 100 (30% above, meets 30%)
    val condition = PercentFrom52WeekLowCondition(minPercentAboveLow = 30.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 130.0, low52Week = 100.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it passes
    assertTrue(result)
  }

  @Test
  fun `should fail when price is too close to the 52-week low`() {
    // Given close only 10% above the low
    val condition = PercentFrom52WeekLowCondition(minPercentAboveLow = 30.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 110.0, low52Week = 100.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it fails
    assertFalse(result)
  }

  @Test
  fun `should pass at the boundary distance`() {
    // Given close exactly 30% above the low
    val condition = PercentFrom52WeekLowCondition(minPercentAboveLow = 30.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 130.0, low52Week = 100.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the boundary (exactly 30%) passes
    assertTrue(result)
  }

  @Test
  fun `should fail when the 52-week low is unavailable`() {
    // Given a missing low52Week (insufficient history)
    val condition = PercentFrom52WeekLowCondition(minPercentAboveLow = 30.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 150.0, low52Week = null)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it fails
    assertFalse(result)
  }

  @Test
  fun `evaluateWithDetails agrees with evaluate and reports missing history`() {
    // Given a missing low52Week
    val condition = PercentFrom52WeekLowCondition()
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 150.0, low52Week = null)))

    // When the detailed result is produced
    val result = condition.evaluateWithDetails(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it matches evaluate and explains the missing data
    assertFalse(result.passed)
    assertEquals(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY), result.passed)
    assertTrue(result.message!!.contains("not available"))
  }

  @Test
  fun `should expose default metadata`() {
    // Given the default condition (>= 30% above low)
    val metadata = PercentFrom52WeekLowCondition().getMetadata()

    // Then it advertises the at-least-30%-above-low filter
    assertEquals("percentFrom52WeekLow", metadata.type)
    assertEquals("Trend", metadata.category)
    assertEquals(1, metadata.parameters.size)
    assertEquals("Price at least 30% above 52-week low", PercentFrom52WeekLowCondition().description())
  }

  @Test
  fun `should reject a negative distance threshold`() {
    // Given a nonsensical negative percentage
    // When constructed, Then it fails loudly
    assertThrows(IllegalArgumentException::class.java) {
      PercentFrom52WeekLowCondition(minPercentAboveLow = -1.0)
    }
  }
}
