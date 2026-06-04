package com.skrymer.udgaard.backtesting.strategy
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.exit.EmaCrossExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitConditionGroup
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitProximity
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ProfitTargetExit
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CompositeExitStrategyTest {
  // Create stock with previous quote for EMA cross detection
  private val stock =
    Stock(
      quotes =
        listOf(
          StockQuote(
            date = LocalDate.of(2024, 1, 14),
            closePriceEMA10 = 105.0, // Above 20 EMA (before cross)
            closePriceEMA20 = 100.0,
          ),
        ),
    )

  @Test
  fun `should exit when any OR condition is met`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            PriceBelowEmaExit(10),
            EmaCrossExit(10, 20),
            ProfitTargetExit(3.0, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 90.0, // Below EMA10 — triggers PriceBelowEmaExit
        closePriceEMA10 = 105.0,
        closePriceEMA20 = 100.0,
        atr = 2.0,
      )

    assertTrue(
      strategy.match(stock, null, quote),
      "Should exit when any OR condition is met",
    )
  }

  @Test
  fun `should not exit when all OR conditions fail`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            PriceBelowEmaExit(10),
            EmaCrossExit(10, 20),
            ProfitTargetExit(3.0, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 105.0, // Above 20 EMA (no cross)
        closePriceEMA20 = 100.0,
        closePrice = 106.0, // Above EMA10, below profit target
        atr = 2.0,
      )

    assertFalse(
      strategy.match(stock, null, quote),
      "Should not exit when all OR conditions fail",
    )
  }

  @Test
  fun `should exit when all AND conditions are met`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            PriceBelowEmaExit(10),
            EmaCrossExit(10, 20),
          ),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 90.0, // Below EMA10
        closePriceEMA10 = 95.0, // Below 20 EMA (crossed under)
        closePriceEMA20 = 100.0,
      )

    assertTrue(
      strategy.match(stock, null, quote),
      "Should exit when all AND conditions are met",
    )
  }

  @Test
  fun `should not exit when one AND condition fails`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            PriceBelowEmaExit(10),
            EmaCrossExit(10, 20),
          ),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 110.0, // Above EMA10 — PriceBelowEmaExit fails
        closePriceEMA10 = 105.0, // Above 20 EMA (not crossed)
        closePriceEMA20 = 100.0,
      )

    assertFalse(
      strategy.match(stock, null, quote),
      "Should not exit when one AND condition fails",
    )
  }

  @Test
  fun `should provide exit reason for first matching condition`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            PriceBelowEmaExit(10),
            EmaCrossExit(10, 20),
            ProfitTargetExit(3.0, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 95.0, // This triggers EMA cross exit
        closePriceEMA20 = 100.0,
        closePrice = 96.0, // Above EMA10, so PriceBelowEma doesn't trigger
        atr = 2.0,
      )

    assertTrue(strategy.match(stock, null, quote))
    val reason = strategy.reason(stock, null, quote)
    assertEquals("10 ema has crossed under the 20 ema", reason)
  }

  @Test
  fun `should return null reason when no condition matches`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            PriceBelowEmaExit(10),
            EmaCrossExit(10, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 110.0, // Above EMA10
        closePriceEMA10 = 105.0,
        closePriceEMA20 = 100.0,
      )

    val reason = strategy.reason(stock, null, quote)
    assertNull(reason, "Should return null when no condition matches")
  }

  @Test
  fun `should work with DSL builder`() {
    val strategy =
      exitStrategy {
        priceBelowEma(10)
        emaCross(10, 20)
        profitTarget(3.0, 20)
      }

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 105.0,
        closePriceEMA20 = 100.0,
        closePrice = 110.0, // This triggers profit target exit
        atr = 2.0,
      )

    assertTrue(
      strategy.match(stock, null, quote),
      "DSL builder should create working strategy",
    )
  }

  @Test
  fun `should provide custom description when specified`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions = listOf(PriceBelowEmaExit(10)),
        operator = LogicalOperator.OR,
        strategyDescription = "Custom exit strategy",
      )

    assertEquals("Custom exit strategy", strategy.description())
  }

  @Test
  fun `should generate description from conditions when not specified`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            PriceBelowEmaExit(10),
            EmaCrossExit(10, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val description = strategy.description()
    assertTrue(
      description.contains("Price below 10 EMA"),
      "Description should include first condition",
    )
    assertTrue(
      description.contains("10EMA crosses under 20EMA"),
      "Description should include second condition",
    )
  }

  @Test
  fun `should return false when no conditions provided`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions = emptyList(),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
      )

    assertFalse(
      strategy.match(stock, null, quote),
      "Strategy should return false when no conditions provided",
    )
  }

  // ===== exitProximities aggregation =====

  @Test
  fun `exitProximities collects non-null values from each condition in declaration order`() {
    // Given: three proximity-aware fake conditions, each reporting a distinct value.
    val first = fakeConditionWithProximity(ExitProximity("first", 0.2, "first detail"))
    val second = fakeConditionWithProximity(ExitProximity("second", 0.7, "second detail"))
    val third = fakeConditionWithProximity(ExitProximity("third", 0.9, "third detail"))
    val strategy = CompositeExitStrategy(
      exitConditions = listOf(first, second, third),
      operator = LogicalOperator.OR,
    )
    val quote = StockQuote(date = LocalDate.of(2024, 1, 15))

    // When
    val proximities = strategy.exitProximities(stock, null, quote)

    // Then
    assertEquals(listOf("first", "second", "third"), proximities.map { it.conditionType })
    assertEquals(listOf(0.2, 0.7, 0.9), proximities.map { it.proximity })
  }

  @Test
  fun `exitProximities returns empty list when every condition returns null`() {
    // Given: all conditions use the default proximity() == null.
    val strategy = CompositeExitStrategy(
      exitConditions = listOf(fakeConditionWithProximity(null), fakeConditionWithProximity(null)),
      operator = LogicalOperator.OR,
    )
    val quote = StockQuote(date = LocalDate.of(2024, 1, 15))

    // When
    val proximities = strategy.exitProximities(stock, null, quote)

    // Then
    assertTrue(proximities.isEmpty(), "no proximity data expected when every condition opts out")
  }

  @Test
  fun `exitProximities returns empty list when the operator is NOT to avoid inverted-semantics warnings`() {
    // Given: a NOT-composite wrapping a proximity-aware condition. Under NOT, high
    // inner proximity means "far from the composite triggering" — the opposite of
    // what a "nearing exit" warning should represent. Stay silent rather than mislead.
    val strategy = CompositeExitStrategy(
      exitConditions = listOf(fakeConditionWithProximity(ExitProximity("inner", 0.95, "inner detail"))),
      operator = LogicalOperator.NOT,
    )
    val quote = StockQuote(date = LocalDate.of(2024, 1, 15))

    // When
    val proximities = strategy.exitProximities(stock, null, quote)

    // Then
    assertTrue(proximities.isEmpty(), "NOT-composites must not surface inner proximity")
  }

  @Test
  fun `exitProximities with mixed overriding and non-overriding conditions yields only the overrides`() {
    // Given: proximity-aware first and third conditions, opt-out second.
    val first = fakeConditionWithProximity(ExitProximity("first", 0.3, "first detail"))
    val second = fakeConditionWithProximity(null)
    val third = fakeConditionWithProximity(ExitProximity("third", 0.8, "third detail"))
    val strategy = CompositeExitStrategy(
      exitConditions = listOf(first, second, third),
      operator = LogicalOperator.OR,
    )
    val quote = StockQuote(date = LocalDate.of(2024, 1, 15))

    // When
    val proximities = strategy.exitProximities(stock, null, quote)

    // Then
    assertEquals(listOf("first", "third"), proximities.map { it.conditionType })
  }

  @Test
  fun `nested OR-of-AND-groups exits only when a whole premise is satisfied`() {
    // Given two disjoint AND-premises (A AND B) OR (C AND D), with only A satisfied.
    val a = fixedExit(true)
    val b = fixedExit(false)
    val c = fixedExit(false)
    val d = fixedExit(false)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 15))

    val nested =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            ExitConditionGroup(LogicalOperator.AND, listOf(a, b)),
            ExitConditionGroup(LogicalOperator.AND, listOf(c, d)),
          ),
        operator = LogicalOperator.OR,
      )
    // A flat OR over the same leaves exits on the lone A — the wrong answer.
    val flatOr = CompositeExitStrategy(exitConditions = listOf(a, b, c, d), operator = LogicalOperator.OR)

    // Then nested correctly holds (no complete premise) while flat OR wrongly exits.
    assertFalse(nested.match(stock, null, quote))
    assertTrue(flatOr.match(stock, null, quote))
  }

  @Test
  fun `getConditions returns the flattened leaf set across nested groups`() {
    // Given an exit strategy whose first member is a nested group of two leaves
    val priceBelow = PriceBelowEmaExit(10)
    val emaCross = EmaCrossExit(10, 20)
    val profitTarget = ProfitTargetExit(3.0, 20)
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            ExitConditionGroup(LogicalOperator.AND, listOf(priceBelow, emaCross)),
            profitTarget,
          ),
        operator = LogicalOperator.OR,
      )

    // When
    val leaves = strategy.getConditions()

    // Then the group is expanded so stateful conditions inside it still get reset
    assertEquals(listOf(priceBelow, emaCross, profitTarget), leaves)
  }

  @Test
  fun `exitProximities surfaces proximities of leaves nested inside a group`() {
    // Given a leaf with a proximity wrapped inside an exit group
    val innerProximity = ExitProximity(conditionType = "inner", proximity = 0.5, detail = "halfway")
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            ExitConditionGroup(
              LogicalOperator.AND,
              listOf(fakeConditionWithProximity(innerProximity), fakeConditionWithProximity(null)),
            ),
          ),
        operator = LogicalOperator.OR,
      )

    // When
    val proximities = strategy.exitProximities(stock, null, StockQuote(date = LocalDate.of(2024, 1, 15)))

    // Then the grouped leaf's warning chip is not lost
    assertEquals(listOf("inner"), proximities.map { it.conditionType })
  }

  private fun fixedExit(result: Boolean): ExitCondition =
    object : ExitCondition {
      override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean = result

      override fun exitReason(): String = "fixed"

      override fun description(): String = if (result) "T" else "F"

      override fun getMetadata(): ConditionMetadata = throw UnsupportedOperationException("stub")

      override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this
    }

  private fun fakeConditionWithProximity(result: ExitProximity?): ExitCondition =
    object : ExitCondition {
      override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean = false

      override fun exitReason(): String = "fake"

      override fun description(): String = "fake"

      override fun getMetadata(): ConditionMetadata =
        ConditionMetadata(type = "fake", displayName = "fake", description = "fake", parameters = emptyList(), category = "Test")

      override fun proximity(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): ExitProximity? = result

      override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this
    }
}
