package com.skrymer.udgaard.backtesting.strategy.condition.entry
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.OrderBlockType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BelowOrderBlockConditionTest {
  // Helper to create quotes between two dates (trading days only - Mon-Fri)
  private fun createTradingDayQuotes(startDate: LocalDate, endDate: LocalDate): List<StockQuote> {
    val quotes = mutableListOf<StockQuote>()
    var currentDate = startDate.plusDays(1)
    while (!currentDate.isAfter(endDate)) {
      // Skip weekends (Sat=6, Sun=7)
      if (currentDate.dayOfWeek.value < 6) {
        quotes.add(StockQuote(date = currentDate, closePrice = 100.0))
      }
      currentDate = currentDate.plusDays(1)
    }
    return quotes
  }

  @Test
  fun `should return true when price is 2 percent below order block`() {
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = com.skrymer.udgaard.data.model.OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    // Price at 98.0 is 2% below order block low of 100.0
    val quote =
      StockQuote(
        date = testDate, // ~42 trading days after order block started
        closePrice = 98.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when price is at least 2% below order block",
    )
  }

  @Test
  fun `should return true when price is more than 2 percent below order block`() {
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    // Price at 95.0 is 5% below order block low of 100.0
    val quote =
      StockQuote(
        date = testDate,
        closePrice = 95.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when price is more than 2% below order block",
    )
  }

  @Test
  fun `should return false when price is not 2 percent below order block`() {
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    // Price at 99.0 is only 1% below order block low of 100.0
    val quote =
      StockQuote(
        date = testDate,
        closePrice = 98.1,
      )

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be false when price is less than 2% below order block",
    )
  }

  @Test
  fun `should return false when price is within order block`() {
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    // Price at 102.0 is within the order block range (100-105)
    val quote =
      StockQuote(
        date = testDate,
        closePrice = 102.0,
      )

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be false when price is within order block (should block entry)",
    )
  }

  @Test
  fun `should return true when order block is too young`() {
    val startDate = LocalDate.of(2024, 2, 15)
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = startDate,
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Only ~10 trading days old (less than 30)
    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(startDate, testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    val quote =
      StockQuote(
        date = testDate,
        closePrice = 98.0, // 2% below order block
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when order block is not old enough (no valid OB to filter against)",
    )
  }

  @Test
  fun `should return true when order block is bullish`() {
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BULLISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    val quote =
      StockQuote(
        date = testDate,
        closePrice = 98.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true for bullish order blocks (no valid bearish OB to filter against)",
    )
  }

  @Test
  fun `should return true when order block has ended`() {
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 2, 1), // Ended before quote date
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    val quote =
      StockQuote(
        date = testDate,
        closePrice = 98.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when order block has already ended (no active OB to filter against)",
    )
  }

  @Test
  fun `should work with multiple order blocks`() {
    val orderBlock1 =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val orderBlock2 =
      OrderBlock(
        low = 110.0,
        high = 115.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock1, orderBlock2), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    // Price at 107.5 is ~2.3% below orderBlock2's low (110.0)
    val quote =
      StockQuote(
        date = testDate,
        closePrice = 107.5,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when price is below any qualifying order block",
    )
  }

  @Test
  fun `should return true when stock has no order blocks`() {
    val stock = Stock(orderBlocks = emptyList())

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 3, 1),
        closePrice = 98.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when stock has no order blocks (no filter to apply)",
    )
  }

  @Test
  fun `should return true when no valid order blocks exist`() {
    val startDate1 = LocalDate.of(2024, 2, 20)
    // All order blocks are too young (less than 30 trading days old)
    val orderBlock1 =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = startDate1, // Only ~6 trading days old
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // This order block has ended
    val orderBlock2 =
      OrderBlock(
        low = 110.0,
        high = 115.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 2, 15), // Already ended
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock1, orderBlock2), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)

    val quote =
      StockQuote(
        date = testDate,
        closePrice = 98.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when no valid order blocks exist (all filtered out)",
    )
  }

  @Test
  fun `should work with custom percent below value`() {
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes)

    val condition = BelowOrderBlockCondition(percentBelow = 5.0, ageInDays = 30)

    // Price at 95.0 is 5% below order block low of 100.0
    val quote =
      StockQuote(
        date = testDate,
        closePrice = 95.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should work with custom percent below value",
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 30)
    assertEquals("Price at least 2.0% below order block (age >= 30d)", condition.description())
  }

  @Test
  fun `should consider all order blocks when ageInDays is 0`() {
    // Order block created on March 1st
    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 3, 1),
        endDate = null,
        orderBlockType = com.skrymer.udgaard.data.model.OrderBlockType.BEARISH,
      )

    val testDate1 = LocalDate.of(2024, 3, 1)
    val quotes1 = createTradingDayQuotes(LocalDate.of(2024, 2, 1), testDate1)
    val stock = Stock(orderBlocks = listOf(orderBlock), quotes = quotes1)

    val condition = BelowOrderBlockCondition(percentBelow = 2.0, ageInDays = 0)

    // Quote on the same day the order block was created - should return true (no valid blocks)
    val quote =
      StockQuote(
        date = testDate1,
        closePrice = 98.0, // 2% below order block
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when order block hasn't started yet (same day means no valid blocks)",
    )

    // Quote 1 trading day after order block was created (block is now 1 trading day old)
    val testDate2 = LocalDate.of(2024, 3, 4) // Monday (skip weekend)
    val quotes2 = createTradingDayQuotes(LocalDate.of(2024, 2, 1), testDate2)
    val stock2 = Stock(orderBlocks = listOf(orderBlock), quotes = quotes2)

    val quote2 =
      StockQuote(
        date = testDate2,
        closePrice = 98.0, // 2% below order block
      )

    assertTrue(
      condition.evaluate(stock2, quote2, BacktestContext.EMPTY),
      "Condition should consider order blocks that are >= 0 trading days old",
    )

    // Verify it would be false if price wasn't far enough below
    val quote3 =
      StockQuote(
        date = testDate2,
        closePrice = 98.5, // Only 1.5% below order block
      )

    assertFalse(
      condition.evaluate(stock2, quote3, BacktestContext.EMPTY),
      "Condition should be false when price is not below enough and not within block",
    )
  }
}
