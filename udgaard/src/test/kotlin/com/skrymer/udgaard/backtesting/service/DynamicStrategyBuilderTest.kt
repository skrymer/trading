package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.CustomStrategyConfig
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.CompositeEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.CompositeExitStrategy
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class DynamicStrategyBuilderTest {
  private val stock = Stock()
  private val quote = StockQuote()

  private fun stubEntry(type: String, result: Boolean): EntryCondition =
    object : EntryCondition {
      override fun evaluate(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean = result

      override fun description(): String = type

      override fun getMetadata(): ConditionMetadata =
        ConditionMetadata(type = type, displayName = type, description = type, parameters = emptyList(), category = "Test")

      override fun evaluateWithDetails(stock: Stock, quote: StockQuote, context: BacktestContext): ConditionEvaluationResult =
        ConditionEvaluationResult(conditionType = type, description = type, passed = result)

      override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this
    }

  private fun stubExit(type: String, result: Boolean): ExitCondition =
    object : ExitCondition {
      override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean = result

      override fun exitReason(): String = type

      override fun description(): String = type

      override fun getMetadata(): ConditionMetadata =
        ConditionMetadata(type = type, displayName = type, description = type, parameters = emptyList(), category = "Test")

      override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this
    }

  private fun builderWith(
    entry: List<EntryCondition> = emptyList(),
    exit: List<ExitCondition> = emptyList(),
  ): DynamicStrategyBuilder =
    DynamicStrategyBuilder(
      strategyRegistry = mock(StrategyRegistry::class.java),
      conditionRegistry = ConditionRegistry(entryConditions = entry, exitConditions = exit),
    )

  @Test
  fun `builds a nested OR-of-AND entry tree that a flat list cannot express`() {
    // Given (a AND b) OR (c AND d) with only `a` satisfied
    val builder =
      builderWith(
        entry = listOf(stubEntry("a", true), stubEntry("b", false), stubEntry("c", false), stubEntry("d", false)),
      )
    val config =
      CustomStrategyConfig(
        operator = "OR",
        conditions =
          listOf(
            ConditionConfig(operator = "AND", conditions = listOf(ConditionConfig("a"), ConditionConfig("b"))),
            ConditionConfig(operator = "AND", conditions = listOf(ConditionConfig("c"), ConditionConfig("d"))),
          ),
      )

    // When
    val strategy = builder.buildEntryStrategy(config) as CompositeEntryStrategy

    // Then no whole premise is satisfied → does not fire
    assertFalse(strategy.test(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `builds a nested OR-of-AND exit tree`() {
    // Given (a AND b) OR (c AND d) with a and b both satisfied → first premise complete
    val builder =
      builderWith(
        exit = listOf(stubExit("a", true), stubExit("b", true), stubExit("c", false), stubExit("d", false)),
      )
    val config =
      CustomStrategyConfig(
        operator = "OR",
        conditions =
          listOf(
            ConditionConfig(operator = "AND", conditions = listOf(ConditionConfig("a"), ConditionConfig("b"))),
            ConditionConfig(operator = "AND", conditions = listOf(ConditionConfig("c"), ConditionConfig("d"))),
          ),
      )

    // When
    val strategy = builder.buildExitStrategy(config) as CompositeExitStrategy

    // Then the completed first premise triggers the exit
    assertTrue(strategy.match(stock, null, quote))
  }

  @Test
  fun `flat entry config without groups still builds and evaluates as before`() {
    // Given a legacy flat config: [a AND b], both satisfied
    val builder = builderWith(entry = listOf(stubEntry("a", true), stubEntry("b", true)))
    val config = CustomStrategyConfig(operator = "AND", conditions = listOf(ConditionConfig("a"), ConditionConfig("b")))

    // When
    val strategy = builder.buildEntryStrategy(config) as CompositeEntryStrategy

    // Then
    assertTrue(strategy.test(stock, quote, BacktestContext.EMPTY))
  }
}
