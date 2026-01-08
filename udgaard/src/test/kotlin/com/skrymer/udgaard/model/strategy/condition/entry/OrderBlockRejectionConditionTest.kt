package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.domain.OrderBlockDomain
import com.skrymer.udgaard.domain.OrderBlockType
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OrderBlockRejectionConditionTest {
  @Test
  fun `should return true when price has been rejected 2 times`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Create quotes showing 2 rejections
    val quotes =
      listOf(
        // First approach and rejection
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 95.0, high = 95.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 98.5, high = 99.0), // Approach (within 2%)
        StockQuoteDomain(date = LocalDate.of(2024, 1, 7), closePrice = 96.0, high = 96.5), // Rejection #1
        // Second approach and rejection
        StockQuoteDomain(date = LocalDate.of(2024, 1, 10), closePrice = 97.0, high = 97.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 11), closePrice = 98.5, high = 99.5), // Approach
        StockQuoteDomain(date = LocalDate.of(2024, 1, 12), closePrice = 95.0, high = 96.0), // Rejection #2
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertTrue(
      condition.evaluate(stock, testQuote),
      "Condition should be true when price has been rejected 2 times",
    )
  }

  @Test
  fun `should return false when price has been rejected only once`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Create quotes showing only 1 rejection
    val quotes =
      listOf(
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 95.0, high = 95.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 98.5, high = 99.0), // Approach
        StockQuoteDomain(date = LocalDate.of(2024, 1, 7), closePrice = 96.0, high = 96.5), // Rejection #1
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertFalse(
      condition.evaluate(stock, testQuote),
      "Condition should be false when price has been rejected only once (need 2)",
    )
  }

  @Test
  fun `should return false when price broke through order block`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Create quotes showing approach then breakthrough
    val quotes =
      listOf(
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 95.0, high = 95.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 98.5, high = 99.0), // Approach
        StockQuoteDomain(date = LocalDate.of(2024, 1, 7), closePrice = 101.0, high = 101.5), // Breakthrough!
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertFalse(
      condition.evaluate(stock, testQuote),
      "Condition should be false when price broke through the order block",
    )
  }

  @Test
  fun `should return false when order block is too young`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 2, 1), // Only 14 days old
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quotes =
      listOf(
        StockQuoteDomain(date = LocalDate.of(2024, 2, 5), closePrice = 98.5, high = 99.0),
        StockQuoteDomain(date = LocalDate.of(2024, 2, 6), closePrice = 96.0, high = 96.5),
        StockQuoteDomain(date = LocalDate.of(2024, 2, 10), closePrice = 98.5, high = 99.5),
        StockQuoteDomain(date = LocalDate.of(2024, 2, 11), closePrice = 95.0, high = 96.0),
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertFalse(
      condition.evaluate(stock, testQuote),
      "Condition should be false when order block is not old enough (14 days < 30 days)",
    )
  }

  @Test
  fun `should return false when order block has ended`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 1, 31), // Ended
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quotes =
      listOf(
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 98.5, high = 99.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 96.0, high = 96.5),
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertFalse(
      condition.evaluate(stock, testQuote),
      "Condition should be false when order block has already ended",
    )
  }

  @Test
  fun `should return false when order block is bullish`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BULLISH, // Bullish, not bearish
      )

    val quotes =
      listOf(
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 98.5, high = 99.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 96.0, high = 96.5),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 10), closePrice = 98.5, high = 99.5),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 11), closePrice = 95.0, high = 96.0),
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertFalse(
      condition.evaluate(stock, testQuote),
      "Condition should be false for bullish order blocks (only checks bearish)",
    )
  }

  @Test
  fun `should return false when stock has no order blocks`() {
    val quotes =
      listOf(
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 95.0, high = 95.0),
      )

    val stock = StockDomain(orderBlocks = emptyList(), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertFalse(
      condition.evaluate(stock, testQuote),
      "Condition should be false when stock has no order blocks",
    )
  }

  @Test
  fun `should work with custom rejection threshold`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // With 5% threshold, rejection zone is 95.0-100.0 (instead of 98.0-100.0 with 2%)
    val quotes =
      listOf(
        // Rejection 1
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 90.0, high = 90.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 96.0, high = 96.0), // Approach (within 5%)
        StockQuoteDomain(date = LocalDate.of(2024, 1, 7), closePrice = 92.0, high = 92.0), // Rejection #1
        // Rejection 2
        StockQuoteDomain(date = LocalDate.of(2024, 1, 10), closePrice = 95.5, high = 97.0), // Approach
        StockQuoteDomain(date = LocalDate.of(2024, 1, 11), closePrice = 90.0, high = 91.0), // Rejection #2
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 5.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertTrue(
      condition.evaluate(stock, testQuote),
      "Condition should work with custom rejection threshold (5%)",
    )
  }

  @Test
  fun `should work with custom minimum rejections`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // 3 rejections
    val quotes =
      listOf(
        // Rejection 1
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 98.5, high = 99.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 96.0, high = 96.5),
        // Rejection 2
        StockQuoteDomain(date = LocalDate.of(2024, 1, 10), closePrice = 98.5, high = 99.5),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 11), closePrice = 95.0, high = 96.0),
        // Rejection 3
        StockQuoteDomain(date = LocalDate.of(2024, 1, 15), closePrice = 98.0, high = 99.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 16), closePrice = 94.0, high = 95.0),
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 3, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertTrue(
      condition.evaluate(stock, testQuote),
      "Condition should work with custom minimum rejections (3)",
    )
  }

  @Test
  fun `should work with multiple order blocks`() {
    val orderBlock1 =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val orderBlock2 =
      OrderBlockDomain(
        low = 108.0,
        high = 110.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // orderBlock1 has only 1 rejection, orderBlock2 has 2 rejections
    val quotes =
      listOf(
        // orderBlock1: 1 rejection
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 98.5, high = 99.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 96.0, high = 96.5),
        // orderBlock2: 2 rejections
        StockQuoteDomain(date = LocalDate.of(2024, 1, 10), closePrice = 108.5, high = 109.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 11), closePrice = 106.0, high = 106.5),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 15), closePrice = 108.0, high = 109.5),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 16), closePrice = 105.0, high = 106.0),
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock1, orderBlock2), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertTrue(
      condition.evaluate(stock, testQuote),
      "Condition should be true when any order block has enough rejections",
    )
  }

  @Test
  fun `should not count as rejection if currently in rejection zone`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Price is currently in rejection zone - shouldn't count as rejection yet
    val quotes =
      listOf(
        // Rejection 1 (completed)
        StockQuoteDomain(date = LocalDate.of(2024, 1, 5), closePrice = 98.5, high = 99.0),
        StockQuoteDomain(date = LocalDate.of(2024, 1, 6), closePrice = 96.0, high = 96.5),
        // Still in rejection zone on test date
        StockQuoteDomain(date = LocalDate.of(2024, 2, 14), closePrice = 98.5, high = 99.0),
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = quotes)
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 98.5, high = 99.0)

    assertFalse(
      condition.evaluate(stock, testQuote),
      "Condition should be false when currently in rejection zone (only 1 completed rejection)",
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = OrderBlockRejectionCondition(minRejections = 3, ageInDays = 60, rejectionThreshold = 2.5)
    assertEquals("Order block rejected price 3+ times (age >= 60d)", condition.description())
  }

  @Test
  fun `should return false when no quotes available for analysis`() {
    val orderBlock =
      OrderBlockDomain(
        low = 98.0,
        high = 100.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val stock = StockDomain(orderBlocks = listOf(orderBlock), quotes = emptyList())
    val condition = OrderBlockRejectionCondition(minRejections = 2, ageInDays = 30, rejectionThreshold = 2.0)

    val testQuote = StockQuoteDomain(date = LocalDate.of(2024, 2, 15), closePrice = 95.0, high = 95.0)

    assertFalse(
      condition.evaluate(stock, testQuote),
      "Condition should be false when no quotes available for analysis",
    )
  }
}
