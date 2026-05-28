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

class BreadthEma10Above50ConditionTest {
  private val condition = BreadthEma10Above50Condition()
  private val stock = Stock()
  private val date = LocalDate.of(2020, 6, 15)
  private val quote = StockQuote(date = date)

  @Test
  fun `permits entry when breadth EMA10 strictly above 50`() {
    // Given: market breadth EMA(10) at 55%, well above threshold
    val context = contextWithBreadthEma10(60.0, 55.0)

    // When: condition is evaluated
    val passed = condition.evaluate(stock, quote, context)

    // Then: entry is permitted
    assertTrue(passed, "Entry should be permitted when EMA(10) > 50")
  }

  @Test
  fun `suppresses entry when breadth EMA10 is exactly 50`() {
    // Given: market breadth EMA(10) equal to threshold (strict > )
    val context = contextWithBreadthEma10(50.0, 50.0)

    // When: condition is evaluated
    val passed = condition.evaluate(stock, quote, context)

    // Then: entry is suppressed — boundary is exclusive
    assertFalse(passed, "Entry should be suppressed at exactly 50 (strict comparison)")
  }

  @Test
  fun `suppresses entry when breadth EMA10 below 50`() {
    // Given: market breadth EMA(10) collapsed to 35%
    val context = contextWithBreadthEma10(40.0, 35.0)

    // When: condition is evaluated
    val passed = condition.evaluate(stock, quote, context)

    // Then: entry is suppressed
    assertFalse(passed, "Entry should be suppressed when EMA(10) < 50")
  }

  @Test
  fun `suppresses entry when no breadth row exists for the date`() {
    // Given: empty BacktestContext — no breadth data for any date
    // When: condition evaluates against an empty context
    val passed = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then: entry is suppressed — missing data conservatively closes the gate
    assertFalse(passed, "Entry should be suppressed when breadth data is unavailable")
  }

  @Test
  fun `permits entry just above the boundary`() {
    // Given: market breadth EMA(10) at 50.01% — fractionally above threshold
    val context = contextWithBreadthEma10(52.0, 50.01)

    // When: condition is evaluated
    val passed = condition.evaluate(stock, quote, context)

    // Then: entry is permitted
    assertTrue(passed, "Entry should be permitted when EMA(10) is fractionally above 50")
  }

  @Test
  fun `evaluateWithDetails surfaces the EMA10 value and threshold`() {
    // Given: market breadth EMA(10) at 62.5%
    val context = contextWithBreadthEma10(58.0, 62.5)

    // When: detailed evaluation is requested
    val result = condition.evaluateWithDetails(stock, quote, context)

    // Then: result includes actual EMA(10) value, threshold, and passes
    assertTrue(result.passed)
    assertEquals("62.5%", result.actualValue)
    assertEquals("> 50%", result.threshold)
  }

  @Test
  fun `description and metadata expose stable identifiers`() {
    // Given: a fresh condition instance
    // When: description and metadata are read
    val description = condition.description()
    val metadata = condition.getMetadata()

    // Then: identifiers match the registered type expected by the DSL
    assertEquals("Market-breadth EMA(10) > 50%", description)
    assertEquals("breadthEma10Above50", metadata.type)
    assertEquals("Market", metadata.category)
  }

  private fun contextWithBreadthEma10(
    breadthPercent: Double,
    ema10: Double,
  ): BacktestContext {
    val row =
      MarketBreadthDaily(
        quoteDate = date,
        breadthPercent = breadthPercent,
        ema10 = ema10,
      )
    return BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(date to row),
    )
  }
}
