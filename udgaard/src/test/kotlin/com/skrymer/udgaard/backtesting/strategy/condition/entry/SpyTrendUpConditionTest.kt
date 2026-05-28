package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SpyTrendUpConditionTest {
  private val condition = SpyTrendUpCondition()
  private val stock = Stock()
  private val date = LocalDate.of(2020, 6, 15)
  private val quote = StockQuote(date = date)

  @Test
  fun `permits entry when SPY close strictly above 200-EMA`() {
    // Given: SPY trading at 450 with 200-EMA at 400
    val context = contextWithSpy(closePrice = 450.0, ema200 = 400.0)

    // When: condition is evaluated
    val passed = condition.evaluate(stock, quote, context)

    // Then: entry is permitted
    assertTrue(passed, "Entry should be permitted when SPY close > 200-EMA")
  }

  @Test
  fun `suppresses entry when SPY close exactly at 200-EMA`() {
    // Given: SPY close exactly matching 200-EMA (strict > comparison)
    val context = contextWithSpy(closePrice = 400.0, ema200 = 400.0)

    // When: condition is evaluated
    val passed = condition.evaluate(stock, quote, context)

    // Then: entry is suppressed — boundary is exclusive
    assertFalse(passed, "Entry should be suppressed at exactly the 200-EMA")
  }

  @Test
  fun `suppresses entry when SPY close below 200-EMA`() {
    // Given: SPY in downtrend, close 380 vs 200-EMA 420
    val context = contextWithSpy(closePrice = 380.0, ema200 = 420.0)

    // When: condition is evaluated
    val passed = condition.evaluate(stock, quote, context)

    // Then: entry is suppressed
    assertFalse(passed, "Entry should be suppressed when SPY close < 200-EMA")
  }

  @Test
  fun `suppresses entry when SPY quote is missing for the date`() {
    // Given: BacktestContext with no SPY data
    // When: condition evaluates against the empty context
    val passed = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then: entry is suppressed — missing data conservatively closes the gate
    assertFalse(passed, "Entry should be suppressed when SPY quote is unavailable")
  }

  @Test
  fun `suppresses entry when SPY 200-EMA is zero or unset`() {
    // Given: SPY quote exists but 200-EMA is 0 (early-history fallback)
    val context = contextWithSpy(closePrice = 100.0, ema200 = 0.0)

    // When: condition is evaluated
    val passed = condition.evaluate(stock, quote, context)

    // Then: entry is suppressed — cannot validate trend without an EMA
    assertFalse(passed, "Entry should be suppressed when 200-EMA is unavailable")
  }

  @Test
  fun `evaluateWithDetails surfaces SPY close and 200-EMA values`() {
    // Given: SPY close 455.25 vs 200-EMA 422.18
    val context = contextWithSpy(closePrice = 455.25, ema200 = 422.18)

    // When: detailed evaluation is requested
    val result = condition.evaluateWithDetails(stock, quote, context)

    // Then: result includes actual close, threshold, and passes
    assertTrue(result.passed)
    assertEquals("455.25", result.actualValue)
    assertEquals("> 422.18 (200-EMA)", result.threshold)
  }

  @Test
  fun `description and metadata expose stable identifiers`() {
    // Given: a fresh condition instance
    // When: description and metadata are read
    val description = condition.description()
    val metadata = condition.getMetadata()

    // Then: identifiers match the registered type expected by the DSL
    assertEquals("SPY close > SPY 200-EMA", description)
    assertEquals("spyTrendUp", metadata.type)
    assertEquals("Market", metadata.category)
  }

  private fun contextWithSpy(
    closePrice: Double,
    ema200: Double,
  ): BacktestContext {
    val spy =
      StockQuote(
        symbol = "SPY",
        date = date,
        closePrice = closePrice,
        ema200 = ema200,
      )
    return BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = emptyMap(),
      spyQuoteMap = mapOf(date to spy),
    )
  }
}
