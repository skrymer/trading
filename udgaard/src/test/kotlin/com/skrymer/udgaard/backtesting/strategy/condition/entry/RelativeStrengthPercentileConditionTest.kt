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

class RelativeStrengthPercentileConditionTest {
  private val today = LocalDate.of(2024, 1, 5)

  @Test
  fun `should pass when the stock is at or above the required market percentile`() {
    // Given a stock ranked in the 80th percentile vs the market, threshold 70
    val condition = RelativeStrengthPercentileCondition(minPercentile = 70.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, relativeStrengthPercentile = 80.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it passes
    assertTrue(result)
  }

  @Test
  fun `should pass at the boundary percentile`() {
    // Given a stock exactly at the threshold
    val condition = RelativeStrengthPercentileCondition(minPercentile = 70.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, relativeStrengthPercentile = 70.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the boundary passes
    assertTrue(result)
  }

  @Test
  fun `should fail when the stock is below the required percentile`() {
    // Given a stock ranked in the 60th percentile, threshold 70
    val condition = RelativeStrengthPercentileCondition(minPercentile = 70.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, relativeStrengthPercentile = 60.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it fails
    assertFalse(result)
  }

  @Test
  fun `should fail when the percentile is unavailable`() {
    // Given a stock with no market-relative rank (insufficient history or thin universe)
    val condition = RelativeStrengthPercentileCondition(minPercentile = 70.0)
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, relativeStrengthPercentile = null)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then the missing rank fails closed
    assertFalse(result)
  }

  @Test
  fun `evaluateWithDetails agrees with evaluate and reports a missing rank`() {
    // Given a stock with no percentile
    val condition = RelativeStrengthPercentileCondition()
    val stock =
      Stock(quotes = mutableListOf(StockQuote(date = today, relativeStrengthPercentile = null)))

    // When the detailed result is produced
    val result = condition.evaluateWithDetails(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it matches evaluate and explains the missing data
    assertFalse(result.passed)
    assertEquals(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY), result.passed)
    assertTrue(result.message!!.contains("not available"))
  }

  @Test
  fun `should reject a threshold outside 0 to 100`() {
    // Given a nonsensical percentile threshold, Then construction fails loudly
    assertThrows(IllegalArgumentException::class.java) { RelativeStrengthPercentileCondition(minPercentile = -1.0) }
    assertThrows(IllegalArgumentException::class.java) { RelativeStrengthPercentileCondition(minPercentile = 101.0) }
  }

  @Test
  fun `should round-trip the threshold through parseConfig`() {
    // Given a parameter map overriding the default
    val parsed =
      RelativeStrengthPercentileCondition().parseConfig(mapOf("minPercentile" to 85.0)) as RelativeStrengthPercentileCondition

    // Then the description reflects the override
    assertEquals("Market-relative strength percentile ≥ 85", parsed.description())
  }

  @Test
  fun `should expose default metadata`() {
    // Given the default condition (>= 70th percentile)
    val metadata = RelativeStrengthPercentileCondition().getMetadata()

    // Then it advertises the relative-strength gate
    assertEquals("relativeStrengthPercentile", metadata.type)
    assertEquals("Trend", metadata.category)
    assertEquals(1, metadata.parameters.size)
    assertEquals("Market-relative strength percentile ≥ 70", RelativeStrengthPercentileCondition().description())
  }
}
