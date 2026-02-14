package com.skrymer.udgaard.backtesting.strategy
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.BuySignalCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.HeatmapCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.UptrendCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.*
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
            BuySignalCondition(daysOld = -1),
            HeatmapCondition(70.0),
          ),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2024, 1, 10),
        heatmap = 65.0,
        closePrice = 100.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0, // closePrice > EMA50 and EMA10 > EMA20 = uptrend
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
            BuySignalCondition(daysOld = -1),
            HeatmapCondition(70.0),
          ),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Uptrend",
        heatmap = 65.0,
        closePrice = 100.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0, // Missing lastBuySignal, so BuySignalCondition fails
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
            BuySignalCondition(daysOld = -1),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Downtrend",
        lastBuySignal = LocalDate.of(2024, 1, 10),
        closePrice = 100.0,
        closePriceEMA10 = 90.0,
        closePriceEMA20 = 95.0, // EMA10 < EMA20, so NOT in uptrend
        closePriceEMA50 = 85.0, // But has buy signal, so OR passes
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
            BuySignalCondition(daysOld = -1),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Downtrend",
        closePrice = 100.0,
        closePriceEMA10 = 90.0,
        closePriceEMA20 = 95.0, // EMA10 < EMA20, so NOT in uptrend
        closePriceEMA50 = 85.0, // No buy signal either, so OR fails
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
        buySignal(daysOld = -1)
        heatmap(70)
      }

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2024, 1, 10),
        heatmap = 65.0,
        closePrice = 100.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0, // All conditions met: uptrend, buy signal, heatmap < 70
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
            BuySignalCondition(daysOld = -1),
          ),
        operator = LogicalOperator.AND,
      )

    val description = strategy.description()
    assertTrue(
      description.contains("Stock is in uptrend"),
      "Description should include first condition",
    )
    assertTrue(
      description.contains("Has buy signal"),
      "Description should include second condition",
    )
  }
}
