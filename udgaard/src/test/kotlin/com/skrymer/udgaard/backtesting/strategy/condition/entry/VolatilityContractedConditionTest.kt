package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VolatilityContractedConditionTest {
  @Test
  fun `should return true when price range is contracted relative to ATR`() {
    val condition = VolatilityContractedCondition(lookbackDays = 5, maxAtrMultiple = 2.5)

    // ATR = 2.0, range over 5 days: high=105, low=101 → range=4 → 4/2=2.0× ATR (≤ 2.5)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), high = 103.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 2), high = 104.0, low = 102.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 3), high = 105.0, low = 102.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 4), high = 104.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 5), high = 103.0, low = 102.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes.last()

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should pass when range (4.0) / ATR (2.0) = 2.0× ≤ 2.5×",
    )
  }

  @Test
  fun `should return false when price range is wide relative to ATR`() {
    val condition = VolatilityContractedCondition(lookbackDays = 5, maxAtrMultiple = 2.5)

    // ATR = 2.0, range over 5 days: high=110, low=101 → range=9 → 9/2=4.5× ATR (> 2.5)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), high = 110.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 2), high = 108.0, low = 103.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 3), high = 107.0, low = 104.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 4), high = 106.0, low = 103.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 5), high = 105.0, low = 102.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should fail when range (9.0) / ATR (2.0) = 4.5× > 2.5×",
    )
  }

  @Test
  fun `should return false when insufficient historical data`() {
    val condition = VolatilityContractedCondition(lookbackDays = 10, maxAtrMultiple = 2.5)

    // Only 3 quotes, less than the required 10
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), high = 102.0, low = 100.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 2), high = 103.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 3), high = 102.0, low = 100.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should fail when insufficient data (3 < 10 lookback days)",
    )
  }

  @Test
  fun `should return true at boundary when range equals maxAtrMultiple`() {
    val condition = VolatilityContractedCondition(lookbackDays = 5, maxAtrMultiple = 2.5)

    // ATR = 2.0, range = 5.0 → 5.0/2.0 = 2.5× ATR (exactly at threshold)
    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), high = 105.0, low = 100.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 2), high = 104.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 3), high = 103.0, low = 102.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 4), high = 104.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 5), high = 103.0, low = 101.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes.last()

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should pass at boundary when range/ATR exactly equals maxAtrMultiple",
    )
  }

  @Test
  fun `should return false when ATR is zero`() {
    val condition = VolatilityContractedCondition(lookbackDays = 5, maxAtrMultiple = 2.5)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), high = 102.0, low = 100.0, atr = 0.0),
            StockQuote(date = LocalDate.of(2024, 1, 2), high = 102.0, low = 100.0, atr = 0.0),
            StockQuote(date = LocalDate.of(2024, 1, 3), high = 102.0, low = 100.0, atr = 0.0),
            StockQuote(date = LocalDate.of(2024, 1, 4), high = 102.0, low = 100.0, atr = 0.0),
            StockQuote(date = LocalDate.of(2024, 1, 5), high = 102.0, low = 100.0, atr = 0.0),
          ),
      )

    val quote = stock.quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should fail when ATR is zero",
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = VolatilityContractedCondition(lookbackDays = 10, maxAtrMultiple = 2.5)
    assertEquals("Volatility contracted (range ≤ 2.5× ATR over 10 days)", condition.description())
  }

  @Test
  fun `should provide correct description with custom parameters`() {
    val condition = VolatilityContractedCondition(lookbackDays = 20, maxAtrMultiple = 3.0)
    assertEquals("Volatility contracted (range ≤ 3.0× ATR over 20 days)", condition.description())
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = VolatilityContractedCondition()
    val metadata = condition.getMetadata()

    assertEquals("volatilityContracted", metadata.type)
    assertEquals("Volatility Contracted", metadata.displayName)
    assertEquals("Volatility", metadata.category)
    assertEquals(2, metadata.parameters.size)

    val lookbackParam = metadata.parameters.find { it.name == "lookbackDays" }
    assertNotNull(lookbackParam)
    assertEquals("number", lookbackParam?.type)
    assertEquals(10, lookbackParam?.defaultValue)
    assertEquals(3, lookbackParam?.min)
    assertEquals(60, lookbackParam?.max)

    val atrParam = metadata.parameters.find { it.name == "maxAtrMultiple" }
    assertNotNull(atrParam)
    assertEquals("number", atrParam?.type)
    assertEquals(2.5, atrParam?.defaultValue)
    assertEquals(0.5, atrParam?.min)
    assertEquals(10.0, atrParam?.max)
  }

  @Test
  fun `should provide detailed evaluation result when passed`() {
    val condition = VolatilityContractedCondition(lookbackDays = 5, maxAtrMultiple = 2.5)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), high = 103.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 2), high = 104.0, low = 102.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 3), high = 105.0, low = 102.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 4), high = 104.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 5), high = 103.0, low = 102.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes.last()
    val result = condition.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    assertTrue(result.passed)
    assertEquals("VolatilityContractedCondition", result.conditionType)
    assertEquals("2.0×", result.actualValue)
    assertEquals("≤ 2.5×", result.threshold)
    assertTrue(result.message!!.contains("✓"))
  }

  @Test
  fun `should provide detailed evaluation result when failed`() {
    val condition = VolatilityContractedCondition(lookbackDays = 5, maxAtrMultiple = 2.5)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), high = 110.0, low = 101.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 2), high = 108.0, low = 103.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 3), high = 107.0, low = 104.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 4), high = 106.0, low = 103.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 5), high = 105.0, low = 102.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes.last()
    val result = condition.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    assertFalse(result.passed)
    assertEquals("VolatilityContractedCondition", result.conditionType)
    assertEquals("4.5×", result.actualValue)
    assertEquals("≤ 2.5×", result.threshold)
    assertTrue(result.message!!.contains("needs"))
    assertTrue(result.message!!.contains("✗"))
  }

  @Test
  fun `should provide detailed evaluation result when insufficient data`() {
    val condition = VolatilityContractedCondition(lookbackDays = 10, maxAtrMultiple = 2.5)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), high = 102.0, low = 100.0, atr = 2.0),
            StockQuote(date = LocalDate.of(2024, 1, 2), high = 102.0, low = 100.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes.last()
    val result = condition.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    assertFalse(result.passed)
    assertTrue(result.message!!.contains("Insufficient data"))
    assertTrue(result.message!!.contains("✗"))
  }
}
