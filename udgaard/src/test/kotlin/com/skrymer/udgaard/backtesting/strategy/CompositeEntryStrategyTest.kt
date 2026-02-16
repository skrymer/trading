package com.skrymer.udgaard.backtesting.strategy
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EmaAlignmentCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceAboveEmaCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.UptrendCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CompositeEntryStrategyTest {
  private val stock = Stock()

  @Test
  fun `should pass when all AND conditions are met`() {
    val strategy =
      CompositeEntryStrategy(
        conditions =
          listOf(
            UptrendCondition(),
            PriceAboveEmaCondition(10),
            EmaAlignmentCondition(10, 20),
          ),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Uptrend",
        closePrice = 100.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertTrue(
      strategy.test(stock, quote),
      "Strategy should pass when all AND conditions are met",
    )
  }

  @Test
  fun `should fail when one AND condition is not met`() {
    val strategy =
      CompositeEntryStrategy(
        conditions =
          listOf(
            UptrendCondition(),
            PriceAboveEmaCondition(10),
            EmaAlignmentCondition(10, 20),
          ),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Uptrend",
        closePrice = 100.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 96.0, // EMA10 < EMA20, so EmaAlignment fails
        closePriceEMA50 = 85.0,
      )

    assertFalse(
      strategy.test(stock, quote),
      "Strategy should fail when one AND condition is not met",
    )
  }

  @Test
  fun `should pass when at least one OR condition is met`() {
    val strategy =
      CompositeEntryStrategy(
        conditions =
          listOf(
            UptrendCondition(),
            PriceAboveEmaCondition(10),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Downtrend",
        closePrice = 100.0,
        closePriceEMA10 = 95.0, // Price > EMA10, so PriceAboveEma passes
        closePriceEMA20 = 105.0,
        closePriceEMA50 = 85.0,
      )

    assertTrue(
      strategy.test(stock, quote),
      "Strategy should pass when at least one OR condition is met",
    )
  }

  @Test
  fun `should fail when all OR conditions fail`() {
    val strategy =
      CompositeEntryStrategy(
        conditions =
          listOf(
            UptrendCondition(),
            PriceAboveEmaCondition(10),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Downtrend",
        closePrice = 90.0, // Below EMA10
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 100.0,
        closePriceEMA50 = 85.0,
      )

    assertFalse(
      strategy.test(stock, quote),
      "Strategy should fail when all OR conditions fail",
    )
  }

  @Test
  fun `should negate condition with NOT operator`() {
    val strategy =
      CompositeEntryStrategy(
        conditions = listOf(UptrendCondition()),
        operator = LogicalOperator.NOT,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Uptrend",
        closePrice = 100.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0, // In uptrend, but NOT negates it to false
      )

    assertFalse(
      strategy.test(stock, quote),
      "Strategy should negate condition with NOT operator",
    )
  }

  @Test
  fun `should return false when no conditions provided`() {
    val strategy =
      CompositeEntryStrategy(
        conditions = emptyList(),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
      )

    assertFalse(
      strategy.test(stock, quote),
      "Strategy should return false when no conditions provided",
    )
  }

  @Test
  fun `should work with DSL builder`() {
    val strategy =
      entryStrategy {
        uptrend()
        priceAbove(10)
        emaAlignment(10, 20)
      }

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Uptrend",
        closePrice = 100.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertTrue(
      strategy.test(stock, quote),
      "DSL builder should create working strategy",
    )
  }

  @Test
  fun `should provide custom description when specified`() {
    val strategy =
      CompositeEntryStrategy(
        conditions = listOf(UptrendCondition()),
        operator = LogicalOperator.AND,
        strategyDescription = "Custom strategy",
      )

    assertEquals("Custom strategy", strategy.description())
  }

  @Test
  fun `should generate description from conditions when not specified`() {
    val strategy =
      CompositeEntryStrategy(
        conditions =
          listOf(
            UptrendCondition(),
            PriceAboveEmaCondition(10),
          ),
        operator = LogicalOperator.AND,
      )

    val description = strategy.description()
    assertTrue(
      description.contains("Stock is in uptrend"),
      "Description should include first condition",
    )
    assertTrue(
      description.contains("Price > 10EMA"),
      "Description should include second condition",
    )
  }
}
