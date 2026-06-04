package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A deterministic stub entry condition so group AND/OR/NOT logic can be asserted
 * independently of any real condition's market semantics.
 */
private class FixedEntryCondition(
  private val result: Boolean,
  private val label: String = "fixed",
) : EntryCondition {
  override fun evaluate(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean = result

  override fun description(): String = label

  override fun getMetadata(): ConditionMetadata = throw UnsupportedOperationException("stub")

  override fun evaluateWithDetails(stock: Stock, quote: StockQuote, context: BacktestContext): ConditionEvaluationResult =
    ConditionEvaluationResult(conditionType = "FixedEntryCondition", description = label, passed = result)

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this
}

class EntryConditionGroupTest {
  private val stock = Stock()
  private val quote = StockQuote()

  @Test
  fun `AND group passes only when every child passes`() {
    // Given
    val passingGroup =
      EntryConditionGroup(
        operator = LogicalOperator.AND,
        members = listOf(FixedEntryCondition(true), FixedEntryCondition(true)),
      )
    val failingGroup =
      EntryConditionGroup(
        operator = LogicalOperator.AND,
        members = listOf(FixedEntryCondition(true), FixedEntryCondition(false)),
      )

    // When / Then
    assertTrue(passingGroup.evaluate(stock, quote, BacktestContext.EMPTY))
    assertFalse(failingGroup.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `OR group passes when any child passes`() {
    // Given
    val group =
      EntryConditionGroup(
        operator = LogicalOperator.OR,
        members = listOf(FixedEntryCondition(false), FixedEntryCondition(true)),
      )
    val allFail =
      EntryConditionGroup(
        operator = LogicalOperator.OR,
        members = listOf(FixedEntryCondition(false), FixedEntryCondition(false)),
      )

    // When / Then
    assertTrue(group.evaluate(stock, quote, BacktestContext.EMPTY))
    assertFalse(allFail.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `NOT group negates its single child`() {
    // Given
    val group =
      EntryConditionGroup(
        operator = LogicalOperator.NOT,
        members = listOf(FixedEntryCondition(true)),
      )

    // When / Then
    assertFalse(group.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `group rejects an empty member list`() {
    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      EntryConditionGroup(operator = LogicalOperator.AND, members = emptyList())
    }
  }

  @Test
  fun `NOT group rejects more than one member`() {
    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      EntryConditionGroup(
        operator = LogicalOperator.NOT,
        members = listOf(FixedEntryCondition(true), FixedEntryCondition(false)),
      )
    }
  }

  @Test
  fun `evaluateWithDetails summarises the group as a single result`() {
    // Given
    val group =
      EntryConditionGroup(
        operator = LogicalOperator.AND,
        members = listOf(FixedEntryCondition(true, "A"), FixedEntryCondition(false, "B")),
      )

    // When
    val result = group.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    // Then
    assertEquals("ConditionGroup", result.conditionType)
    assertFalse(result.passed)
    assertTrue(result.message!!.contains("A: PASS"))
    assertTrue(result.message!!.contains("B: FAIL"))
  }

  @Test
  fun `leaves flattens conditions across nested groups`() {
    // Given
    val leafA = FixedEntryCondition(true, "A")
    val leafB = FixedEntryCondition(true, "B")
    val leafC = FixedEntryCondition(true, "C")
    val nested =
      EntryConditionGroup(
        operator = LogicalOperator.OR,
        members =
          listOf(
            EntryConditionGroup(LogicalOperator.AND, listOf(leafA, leafB)),
            leafC,
          ),
      )

    // When
    val leaves = nested.leaves()

    // Then
    assertEquals(listOf(leafA, leafB, leafC), leaves)
  }
}
