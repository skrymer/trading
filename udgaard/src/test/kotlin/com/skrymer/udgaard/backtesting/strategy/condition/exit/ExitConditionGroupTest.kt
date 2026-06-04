package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A deterministic stub exit condition so group AND/OR/NOT logic can be asserted
 * independently of any real condition's market semantics.
 */
private class FixedExitCondition(
  private val result: Boolean,
  private val reason: String = "fixed",
) : ExitCondition {
  override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean = result

  override fun exitReason(): String = reason

  override fun description(): String = reason

  override fun getMetadata(): ConditionMetadata = throw UnsupportedOperationException("stub")

  override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this
}

class ExitConditionGroupTest {
  private val stock = Stock()
  private val quote = StockQuote()

  @Test
  fun `AND group exits only when every child exits`() {
    // Given
    val allExit =
      ExitConditionGroup(
        operator = LogicalOperator.AND,
        members = listOf(FixedExitCondition(true), FixedExitCondition(true)),
      )
    val oneHolds =
      ExitConditionGroup(
        operator = LogicalOperator.AND,
        members = listOf(FixedExitCondition(true), FixedExitCondition(false)),
      )

    // When / Then
    assertTrue(allExit.shouldExit(stock, quote, quote))
    assertFalse(oneHolds.shouldExit(stock, quote, quote))
  }

  @Test
  fun `OR group exits when any child exits and reports the matched child's reason`() {
    // Given
    val group =
      ExitConditionGroup(
        operator = LogicalOperator.OR,
        members = listOf(FixedExitCondition(false, "stop"), FixedExitCondition(true, "target")),
      )

    // When
    val exited = group.shouldExit(stock, quote, quote)

    // Then
    assertTrue(exited)
    assertEquals("target", group.exitReason())
  }

  @Test
  fun `NOT group negates its single child`() {
    // Given
    val group =
      ExitConditionGroup(
        operator = LogicalOperator.NOT,
        members = listOf(FixedExitCondition(true)),
      )

    // When / Then
    assertFalse(group.shouldExit(stock, quote, quote))
  }

  @Test
  fun `group rejects an empty member list`() {
    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      ExitConditionGroup(operator = LogicalOperator.OR, members = emptyList())
    }
  }

  @Test
  fun `leaves flattens conditions across nested groups`() {
    // Given
    val leafA = FixedExitCondition(true, "A")
    val leafB = FixedExitCondition(true, "B")
    val leafC = FixedExitCondition(true, "C")
    val nested =
      ExitConditionGroup(
        operator = LogicalOperator.OR,
        members =
          listOf(
            ExitConditionGroup(LogicalOperator.AND, listOf(leafA, leafB)),
            leafC,
          ),
      )

    // When
    val leaves = nested.leaves()

    // Then
    assertEquals(listOf(leafA, leafB, leafC), leaves)
  }
}
