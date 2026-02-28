package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.OrderBlockType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OrderBlockBreakoutConditionTest {
  private fun createQuotes(
    startDate: LocalDate = LocalDate.of(2024, 1, 2),
    count: Int = 20,
    closePrice: Double = 110.0,
    symbol: String = "TEST",
  ): MutableList<StockQuote> {
    val quotes = mutableListOf<StockQuote>()
    var date = startDate
    repeat(count) {
      if (date.dayOfWeek.value >= 6) date = date.plusDays((8 - date.dayOfWeek.value).toLong())
      quotes.add(
        StockQuote(
          date = date,
          closePrice = closePrice,
          openPrice = closePrice,
          high = closePrice + 1.0,
          low = closePrice - 1.0,
          symbol = symbol,
        ),
      )
      date = date.plusDays(1)
      if (date.dayOfWeek.value >= 6) date = date.plusDays((8 - date.dayOfWeek.value).toLong())
    }
    return quotes
  }

  @Test
  fun `should pass when price is above recently mitigated OB high`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    // OB with high at 105, mitigated on Jan 26 (endDate set)
    val ob = OrderBlock(
      low = 95.0,
      high = 105.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BEARISH,
    )

    // Quotes start Jan 2, price at 108 (above OB high of 105)
    val quotes = createQuotes(closePrice = 108.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    // Last quote is around Jan 29 — 1-2 trading days after mitigation
    val quote = quotes.last()

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should pass when price is above a recently mitigated OB high",
    )
  }

  @Test
  fun `should fail when no mitigated bearish OBs exist`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    val quotes = createQuotes(closePrice = 108.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = emptyList())
    val quote = quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should fail when no mitigated bearish OBs exist",
    )
  }

  @Test
  fun `should fail when only active OBs exist`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    // Active OB (endDate = null, not mitigated)
    val ob = OrderBlock(
      low = 95.0,
      high = 105.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = null,
      orderBlockType = OrderBlockType.BEARISH,
    )

    val quotes = createQuotes(closePrice = 108.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should fail when only active (non-mitigated) OBs exist — they haven't been broken yet",
    )
  }

  @Test
  fun `should fail when price is inside an active bearish OB`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    // Active OB (not mitigated), price is inside its zone
    val activeOB = OrderBlock(
      low = 105.0,
      high = 115.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = null,
      orderBlockType = OrderBlockType.BEARISH,
    )

    // A mitigated OB that would otherwise trigger a breakout
    val mitigatedOB = OrderBlock(
      low = 95.0,
      high = 100.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BEARISH,
    )

    // Price at 110 — inside active OB [105, 115], above mitigated OB high (100)
    val quotes = createQuotes(closePrice = 110.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(activeOB, mitigatedOB))
    val quote = quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should fail when price is inside an active bearish OB, even if a mitigated OB breakout exists",
    )
  }

  @Test
  fun `should pass when OB endDate equals quote date`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    val quotes = createQuotes(closePrice = 108.0)
    val quoteDate = quotes.last().date

    // OB mitigated on the same day as the quote — should still be considered
    val ob = OrderBlock(
      low = 95.0,
      high = 105.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = quoteDate,
      orderBlockType = OrderBlockType.BEARISH,
    )

    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should pass when OB endDate equals quote date (same-day breakout)",
    )
  }

  @Test
  fun `should fail when price is below mitigated OB high`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    val ob = OrderBlock(
      low = 95.0,
      high = 115.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BEARISH,
    )

    // Price at 110, below OB high of 115
    val quotes = createQuotes(closePrice = 110.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should fail when price is below mitigated OB high",
    )
  }

  @Test
  fun `should fail when OB was mitigated too long ago`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 3)

    // OB mitigated on Jan 5 — much earlier than quotes ending around Jan 29
    val ob = OrderBlock(
      low = 95.0,
      high = 105.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 5),
      orderBlockType = OrderBlockType.BEARISH,
    )

    val quotes = createQuotes(closePrice = 108.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should fail when OB was mitigated more than maxDaysSinceBreakout ago",
    )
  }

  @Test
  fun `should use most recently mitigated OB when multiple exist`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    // Older mitigation — should be ignored in favor of more recent one
    val olderOB = OrderBlock(
      low = 100.0,
      high = 115.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 25),
      orderBlockType = OrderBlockType.BEARISH,
    )
    // More recently mitigated — should be selected
    val newerOB = OrderBlock(
      low = 95.0,
      high = 105.0,
      startDate = LocalDate.of(2024, 1, 5),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BEARISH,
    )

    // Price at 108, above newerOB high (105) — should pass because nearest OB is newerOB
    val quotes = createQuotes(closePrice = 108.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(olderOB, newerOB))
    val quote = quotes.last()

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should check against most recently mitigated OB (high=105), not older one (high=115)",
    )
  }

  @Test
  fun `should fail when price below nearest mitigated OB high`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    // Older OB with lower high — price is above it
    val olderOB = OrderBlock(
      low = 90.0,
      high = 95.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 25),
      orderBlockType = OrderBlockType.BEARISH,
    )
    // More recently mitigated — price is below its high
    val newerOB = OrderBlock(
      low = 100.0,
      high = 105.0,
      startDate = LocalDate.of(2024, 1, 5),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BEARISH,
    )

    // Price at 100, above olderOB (95) but below newerOB (105)
    val quotes = createQuotes(closePrice = 100.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(olderOB, newerOB))
    val quote = quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should fail when price is below the nearest mitigated OB high",
    )
  }

  @Test
  fun `should ignore bullish OBs`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    val bullishOB = OrderBlock(
      low = 95.0,
      high = 105.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BULLISH,
    )

    val quotes = createQuotes(closePrice = 108.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(bullishOB))
    val quote = quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should fail — only mitigated bearish OBs count, bullish OBs should be ignored",
    )
  }

  @Test
  fun `should require consecutive days above when specified`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 3, maxDaysSinceBreakout = 10)

    val ob = OrderBlock(
      low = 95.0,
      high = 100.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 16),
      orderBlockType = OrderBlockType.BEARISH,
    )

    // Create quotes where only the last 2 days are above OB high (need 3)
    val quotes = mutableListOf<StockQuote>()
    var date = LocalDate.of(2024, 1, 2)
    // 10 days below OB high
    repeat(10) {
      if (date.dayOfWeek.value >= 6) date = date.plusDays((8 - date.dayOfWeek.value).toLong())
      quotes.add(StockQuote(date = date, closePrice = 98.0, openPrice = 98.0, high = 99.0, low = 97.0, symbol = "TEST"))
      date = date.plusDays(1)
    }
    // 2 days above (not enough for consecutiveDays = 3)
    repeat(2) {
      if (date.dayOfWeek.value >= 6) date = date.plusDays((8 - date.dayOfWeek.value).toLong())
      quotes.add(StockQuote(date = date, closePrice = 103.0, openPrice = 103.0, high = 104.0, low = 102.0, symbol = "TEST"))
      date = date.plusDays(1)
    }

    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should fail when only 2 consecutive days above but 3 required",
    )
  }

  @Test
  fun `should pass when enough consecutive days above`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 3, maxDaysSinceBreakout = 10)

    val ob = OrderBlock(
      low = 95.0,
      high = 100.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 15),
      orderBlockType = OrderBlockType.BEARISH,
    )

    val quotes = mutableListOf<StockQuote>()
    var date = LocalDate.of(2024, 1, 2)
    // 10 days below OB high
    repeat(10) {
      if (date.dayOfWeek.value >= 6) date = date.plusDays((8 - date.dayOfWeek.value).toLong())
      quotes.add(StockQuote(date = date, closePrice = 98.0, openPrice = 98.0, high = 99.0, low = 97.0, symbol = "TEST"))
      date = date.plusDays(1)
    }
    // 3 days above (meets consecutiveDays = 3)
    repeat(3) {
      if (date.dayOfWeek.value >= 6) date = date.plusDays((8 - date.dayOfWeek.value).toLong())
      quotes.add(StockQuote(date = date, closePrice = 103.0, openPrice = 103.0, high = 104.0, low = 102.0, symbol = "TEST"))
      date = date.plusDays(1)
    }

    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Should pass when 3 consecutive days above OB high (meets minimum of 3)",
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 2, maxDaysSinceBreakout = 5)
    assertEquals(
      "OB breakout (above mitigated OB high, breakout within 5 days, held 2 days)",
      condition.description(),
    )
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = OrderBlockBreakoutCondition()
    val metadata = condition.getMetadata()

    assertEquals("orderBlockBreakout", metadata.type)
    assertEquals("Order Block Breakout", metadata.displayName)
    assertEquals("OrderBlock", metadata.category)
    assertEquals(3, metadata.parameters.size)

    val daysParam = metadata.parameters.find { it.name == "consecutiveDays" }
    assertNotNull(daysParam)
    assertEquals(1, daysParam?.defaultValue)

    val breakoutParam = metadata.parameters.find { it.name == "maxDaysSinceBreakout" }
    assertNotNull(breakoutParam)
    assertEquals(3, breakoutParam?.defaultValue)

    val ageParam = metadata.parameters.find { it.name == "ageInDays" }
    assertNotNull(ageParam)
    assertEquals(0, ageParam?.defaultValue)
  }

  @Test
  fun `should provide detailed result when price below nearest OB high`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    // OB zone [105, 115], price at 110 is below OB high
    val ob = OrderBlock(
      low = 105.0,
      high = 115.0,
      startDate = LocalDate.of(2024, 1, 5),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BEARISH,
    )

    val quotes = createQuotes(closePrice = 110.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    val result = condition.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    assertFalse(result.passed)
    assertEquals("OrderBlockBreakoutCondition", result.conditionType)
    assertEquals("Below OB high", result.actualValue)
    assertTrue(result.message!!.contains("below"))
    assertTrue(result.message!!.contains("\u2717"))
  }

  @Test
  fun `should provide detailed result when no mitigated OBs`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    val quotes = createQuotes(closePrice = 108.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = emptyList())
    val quote = quotes.last()

    val result = condition.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    assertFalse(result.passed)
    assertEquals("OrderBlockBreakoutCondition", result.conditionType)
    assertEquals("No mitigated OBs", result.actualValue)
    assertTrue(result.message!!.contains("\u2717"))
  }

  @Test
  fun `should provide detailed result when price below mitigated OB`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    val ob = OrderBlock(
      low = 115.0,
      high = 120.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BEARISH,
    )

    val quotes = createQuotes(closePrice = 110.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    val result = condition.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    assertFalse(result.passed)
    assertTrue(result.message!!.contains("below"))
    assertTrue(result.message!!.contains("\u2717"))
  }

  @Test
  fun `should provide detailed result when breakout confirmed`() {
    val condition = OrderBlockBreakoutCondition(consecutiveDays = 1, maxDaysSinceBreakout = 5)

    val ob = OrderBlock(
      low = 95.0,
      high = 105.0,
      startDate = LocalDate.of(2024, 1, 2),
      endDate = LocalDate.of(2024, 1, 26),
      orderBlockType = OrderBlockType.BEARISH,
    )

    val quotes = createQuotes(closePrice = 108.0)
    val stock = Stock(symbol = "TEST", quotes = quotes, orderBlocks = listOf(ob))
    val quote = quotes.last()

    val result = condition.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    assertTrue(result.passed)
    assertEquals("OrderBlockBreakoutCondition", result.conditionType)
    assertTrue(result.message!!.contains("Breakout confirmed"))
    assertTrue(result.message!!.contains("\u2713"))
  }
}
