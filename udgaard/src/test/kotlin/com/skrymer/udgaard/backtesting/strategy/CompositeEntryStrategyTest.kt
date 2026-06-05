package com.skrymer.udgaard.backtesting.strategy
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EmaAlignmentCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryConditionGroup
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceAboveEmaCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.UptrendCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

private class FixedCondition(
  private val result: Boolean
) : com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition {
  override fun evaluate(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean = result

  override fun description(): String = if (result) "T" else "F"

  override fun getMetadata() = throw UnsupportedOperationException("stub")

  override fun evaluateWithDetails(stock: Stock, quote: StockQuote, context: BacktestContext) =
    com.skrymer.udgaard.backtesting.dto
      .ConditionEvaluationResult("FixedCondition", description(), result)

  override fun parseConfig(parameters: Map<String, Any>) = this
}

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
        closePrice = 100.0,
        closePriceEMA5 = 98.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertTrue(
      strategy.test(stock, quote, BacktestContext.EMPTY),
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
        closePrice = 100.0,
        closePriceEMA5 = 98.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 96.0, // EMA10 < EMA20, so EmaAlignment fails
        closePriceEMA50 = 85.0,
      )

    assertFalse(
      strategy.test(stock, quote, BacktestContext.EMPTY),
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
        closePrice = 100.0,
        closePriceEMA5 = 90.0, // EMA5 < EMA10, so uptrend fails
        closePriceEMA10 = 95.0, // Price > EMA10, so PriceAboveEma passes
        closePriceEMA20 = 105.0,
        closePriceEMA50 = 85.0,
      )

    assertTrue(
      strategy.test(stock, quote, BacktestContext.EMPTY),
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
        closePrice = 90.0, // Below EMA10
        closePriceEMA5 = 88.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 100.0,
        closePriceEMA50 = 85.0,
      )

    assertFalse(
      strategy.test(stock, quote, BacktestContext.EMPTY),
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
        closePrice = 100.0,
        closePriceEMA5 = 98.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0, // In uptrend, but NOT negates it to false
      )

    assertFalse(
      strategy.test(stock, quote, BacktestContext.EMPTY),
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
      strategy.test(stock, quote, BacktestContext.EMPTY),
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
        closePrice = 100.0,
        closePriceEMA5 = 98.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertTrue(
      strategy.test(stock, quote, BacktestContext.EMPTY),
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
  fun `nested OR-of-AND-groups fires only when a whole premise is satisfied`() {
    // Given two disjoint AND-premises (A AND B) OR (C AND D), with only A satisfied.
    val conditionA = FixedCondition(true)
    val conditionB = FixedCondition(false)
    val conditionC = FixedCondition(false)
    val conditionD = FixedCondition(false)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 15))

    val nested =
      CompositeEntryStrategy(
        conditions =
          listOf(
            EntryConditionGroup(LogicalOperator.AND, listOf(conditionA, conditionB)),
            EntryConditionGroup(LogicalOperator.AND, listOf(conditionC, conditionD)),
          ),
        operator = LogicalOperator.OR,
      )
    // A flat OR over the same leaves fires on the lone A — the wrong answer.
    val flatOr = CompositeEntryStrategy(conditions = listOf(conditionA, conditionB, conditionC, conditionD), operator = LogicalOperator.OR)

    // Then nested correctly rejects (no complete premise) while flat OR wrongly fires.
    assertFalse(nested.test(stock, quote, BacktestContext.EMPTY))
    assertTrue(flatOr.test(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `getConditions returns the flattened leaf set across nested groups`() {
    // Given a strategy whose first member is a nested group of two leaves
    val uptrend = UptrendCondition()
    val priceAbove = PriceAboveEmaCondition(10)
    val emaAlignment = EmaAlignmentCondition(10, 20)
    val strategy =
      CompositeEntryStrategy(
        conditions =
          listOf(
            EntryConditionGroup(LogicalOperator.AND, listOf(uptrend, priceAbove)),
            emaAlignment,
          ),
        operator = LogicalOperator.OR,
      )

    // When
    val leaves = strategy.getConditions()

    // Then the group is expanded so stateful conditions inside it still get reset
    assertEquals(listOf(uptrend, priceAbove, emaAlignment), leaves)
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
