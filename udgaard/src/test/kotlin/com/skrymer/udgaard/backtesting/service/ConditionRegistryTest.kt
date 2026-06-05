package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionRegistryTest {
  @Test
  fun `duplicate entry condition types throw at construction`() {
    // Given: two entry conditions reporting the same type
    val conditionA = stubEntryCondition("dup", "A")
    val conditionB = stubEntryCondition("dup", "B")

    // When / Then
    val ex =
      assertThrows(IllegalStateException::class.java) {
        ConditionRegistry(entryConditions = listOf(conditionA, conditionB), exitConditions = emptyList())
      }
    assertTrue(ex.message!!.contains("dup"), "expected message to mention duplicate type, got: ${ex.message}")
  }

  @Test
  fun `duplicate exit condition types throw at construction`() {
    // Given: two exit conditions reporting the same type
    val conditionA = stubExitCondition("dup", "A")
    val conditionB = stubExitCondition("dup", "B")

    // When / Then
    val ex =
      assertThrows(IllegalStateException::class.java) {
        ConditionRegistry(entryConditions = emptyList(), exitConditions = listOf(conditionA, conditionB))
      }
    assertTrue(ex.message!!.contains("dup"), "expected message to mention duplicate type, got: ${ex.message}")
  }

  @Test
  fun `unknown entry type throws with offending type in message`() {
    // Given: a registry with no entry conditions
    val registry = ConditionRegistry(entryConditions = emptyList(), exitConditions = emptyList())

    // When / Then
    val ex =
      assertThrows(IllegalArgumentException::class.java) {
        registry.buildEntryCondition(ConditionConfig(type = "unknownEntryType"))
      }
    assertTrue(
      ex.message!!.contains("unknownEntryType"),
      "expected message to include offending type, got: ${ex.message}",
    )
  }

  @Test
  fun `unknown exit type throws with offending type in message`() {
    // Given: a registry with no exit conditions
    val registry = ConditionRegistry(entryConditions = emptyList(), exitConditions = emptyList())

    // When / Then
    val ex =
      assertThrows(IllegalArgumentException::class.java) {
        registry.buildExitCondition(ConditionConfig(type = "unknownExitType"))
      }
    assertTrue(
      ex.message!!.contains("unknownExitType"),
      "expected message to include offending type, got: ${ex.message}",
    )
  }

  @Test
  fun `buildEntryCondition routes by lowercase type and delegates to parseConfig`() {
    // Given: a registry with one stub entry condition
    val stub = stubEntryCondition("mytype", "X")
    val registry = ConditionRegistry(entryConditions = listOf(stub), exitConditions = emptyList())

    // When: build with mixed-case type
    val built = registry.buildEntryCondition(ConditionConfig(type = "MyType"))

    // Then: stub.parseConfig was called → returns same instance
    assertSame(stub, built)
  }

  @Test
  fun `buildExitCondition routes by lowercase type and delegates to parseConfig`() {
    // Given: a registry with one stub exit condition
    val stub = stubExitCondition("mytype", "X")
    val registry = ConditionRegistry(entryConditions = emptyList(), exitConditions = listOf(stub))

    // When: build with mixed-case type
    val built = registry.buildExitCondition(ConditionConfig(type = "MyType"))

    // Then
    assertSame(stub, built)
  }

  // ── helpers ──

  private fun stubEntryCondition(type: String, label: String): EntryCondition =
    object : EntryCondition {
      override fun evaluate(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean = false

      override fun description(): String = "$label-$type"

      override fun getMetadata(): ConditionMetadata =
        ConditionMetadata(type = type, displayName = label, description = label, parameters = emptyList(), category = "Test")

      override fun evaluateWithDetails(
        stock: Stock,
        quote: StockQuote,
        context: BacktestContext,
      ): ConditionEvaluationResult =
        ConditionEvaluationResult(conditionType = type, description = description(), passed = false)

      override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this
    }

  private fun stubExitCondition(type: String, label: String): ExitCondition =
    object : ExitCondition {
      override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean = false

      override fun exitReason(): String = "n/a"

      override fun description(): String = "$label-$type"

      override fun getMetadata(): ConditionMetadata =
        ConditionMetadata(type = type, displayName = label, description = label, parameters = emptyList(), category = "Test")

      override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this
    }
}
