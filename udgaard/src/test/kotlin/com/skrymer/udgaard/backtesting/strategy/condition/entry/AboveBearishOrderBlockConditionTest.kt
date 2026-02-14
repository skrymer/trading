package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.OrderBlockType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AboveBearishOrderBlockConditionTest {
  // Helper to create quotes between two dates (trading days only - Mon-Fri)
  // Default close price is ABOVE the typical OB range [95-105] to avoid interfering with cooldown
  private fun createTradingDayQuotes(
    startDate: LocalDate,
    endDate: LocalDate,
    defaultClosePrice: Double = 110.0,
  ): List<StockQuote> {
    val quotes = mutableListOf<StockQuote>()
    var currentDate = startDate.plusDays(1)
    while (!currentDate.isAfter(endDate)) {
      // Skip weekends (Sat=6, Sun=7)
      if (currentDate.dayOfWeek.value < 6) {
        quotes.add(StockQuote(date = currentDate, closePrice = defaultClosePrice))
      }
      currentDate = currentDate.plusDays(1)
    }
    return quotes
  }

  @Test
  fun `should return true when price above bearish order block for 3 consecutive days`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    // Order block at 95-105, created on 2024-01-01
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1) // ~42 trading days after order block started
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days with prices above order block
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 106.0),
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 107.0),
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 108.0),
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should return true when price has been above bearish order block for 3 consecutive days",
    )
  }

  @Test
  fun `should return false when price was inside OB within cooldown period`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days: one inside OB (breaks cooldown), two above
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 102.0), // Inside OB
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 106.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 108.0), // Above
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should return false when price was inside OB only 1 bar ago (need 3 bars cooldown)",
    )
  }

  @Test
  fun `should return true when no relevant bearish order blocks exist`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes =
          listOf(
            StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 106.0),
            StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 107.0),
            StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 108.0),
          ),
        orderBlocks = emptyList(),
      )

    assertTrue(
      condition.evaluate(stock, stock.quotes.last()),
      "Should return true when no order blocks exist (safe to enter, no resistance)",
    )
  }

  @Test
  fun `should return true when order block is too young`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    // Order block created on 2024-02-15, only ~10 trading days old (too young)
    val startDate = LocalDate.of(2024, 2, 15)
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = startDate,
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1) // Only ~10 trading days after order block started
    val quotes = createTradingDayQuotes(startDate, testDate)

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should return true when order block is younger than ageInDays threshold (not relevant)",
    )
  }

  @Test
  fun `should return true when order block is bullish not bearish`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    // BULLISH order block (support, not resistance)
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BULLISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should return true when order block is bullish (not relevant, only check bearish blocks)",
    )
  }

  @Test
  fun `should return false when insufficient cooldown bars since inside OB`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 5, ageInDays = 0)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // 3 quotes: inside OB, then 2 above - need 5 bars cooldown but only 1 since inside
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 100.0), // Inside OB
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 107.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 108.0), // Above
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should return false when only 1 bar since inside OB (need 5 bars cooldown)",
    )
  }

  @Test
  fun `should work with different consecutive day parameters`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 5, ageInDays = 30)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 5 days with prices above order block
    val lastFiveDates = listOf(
      LocalDate.of(2024, 2, 25),
      LocalDate.of(2024, 2, 26),
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastFiveDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 25), closePrice = 106.0),
        StockQuote(date = LocalDate.of(2024, 2, 26), closePrice = 107.0),
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 108.0),
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 109.0),
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 110.0),
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should work with 5 consecutive days parameter",
    )
  }

  @Test
  fun `should work with different age parameters`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 60)

    // Order block from 2024-01-01, test date needs to be ~60+ trading days later
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 4, 1) // ~65 trading days after order block started
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days with prices above order block
    val lastThreeDates = listOf(
      LocalDate.of(2024, 3, 28),
      LocalDate.of(2024, 3, 29),
      LocalDate.of(2024, 4, 1),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 3, 28), closePrice = 106.0),
        StockQuote(date = LocalDate.of(2024, 3, 29), closePrice = 107.0),
        StockQuote(date = LocalDate.of(2024, 4, 1), closePrice = 108.0),
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should work with custom age threshold (60 trading days)",
    )
  }

  @Test
  fun `should return true when order block ended before current quote`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    // Order block ended on Feb 1st
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 2, 1), // Ended before test date
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate)

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should return true when order block has ended (not currently active, not relevant)",
    )
  }

  @Test
  fun `should handle multiple order blocks and block when currently inside any`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val orderBlock1 =
      OrderBlock(
        low = 95.0,
        high = 105.0, // Price inside this one
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val orderBlock2 =
      OrderBlock(
        low = 85.0,
        high = 95.0, // Price above this one
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days - price is inside orderBlock1
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 100.0), // Inside OB1
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 102.0), // Inside OB1
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 103.0), // Inside OB1
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock1, orderBlock2),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should return false when current price is inside a bearish order block",
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)
    assertEquals(
      "Price above bearish order block for 3 consecutive days (age >= 30d, proximity 2.0%)",
      condition.description(),
    )
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = AboveBearishOrderBlockCondition()
    val metadata = condition.getMetadata()

    assertEquals("aboveBearishOrderBlock", metadata.type)
    assertEquals("Above Bearish Order Block", metadata.displayName)
    assertEquals(
      "Price has been above a bearish order block (resistance) for X consecutive days",
      metadata.description,
    )
    assertEquals("OrderBlock", metadata.category)
    assertEquals(3, metadata.parameters.size)

    val consecutiveDaysParam = metadata.parameters.find { it.name == "consecutiveDays" }
    assertNotNull(consecutiveDaysParam)
    assertEquals("number", consecutiveDaysParam?.type)
    assertEquals(3, consecutiveDaysParam?.defaultValue)
    assertEquals(1, consecutiveDaysParam?.min)
    assertEquals(10, consecutiveDaysParam?.max)

    val ageParam = metadata.parameters.find { it.name == "ageInDays" }
    assertNotNull(ageParam)
    assertEquals("number", ageParam?.type)
    assertEquals(30, ageParam?.defaultValue)
    assertEquals(1, ageParam?.min)
    assertEquals(365, ageParam?.max)

    val proximityParam = metadata.parameters.find { it.name == "proximityPercent" }
    assertNotNull(proximityParam)
    assertEquals("number", proximityParam?.type)
    assertEquals(2.0, proximityParam?.defaultValue)
    assertEquals(0.0, proximityParam?.min)
    assertEquals(10.0, proximityParam?.max)
  }

  @Test
  fun `should provide detailed evaluation when passing - never inside`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days with prices above order block
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 106.0),
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 107.0),
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 108.0),
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    val result = condition.evaluateWithDetails(stock, quotes.last())

    assertTrue(result.passed)
    assertEquals("AboveBearishOrderBlockCondition", result.conditionType)
    assertEquals("Never inside/near", result.actualValue)
    assertTrue(result.message?.contains("✓") ?: false)
  }

  @Test
  fun `should provide detailed evaluation when failing due to recent cooldown`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days - one inside OB, two above
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 102.0), // Inside OB
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 107.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 108.0), // Above
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    val result = condition.evaluateWithDetails(stock, quotes.last())

    assertFalse(result.passed)
    assertTrue(result.message?.contains("only") ?: false)
    assertTrue(result.message?.contains("95.00") ?: false)
    assertTrue(result.message?.contains("105.00") ?: false)
    assertTrue(result.message?.contains("✗") ?: false)
  }

  @Test
  fun `should provide detailed evaluation when no relevant order blocks`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes =
          listOf(
            StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 106.0),
            StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 107.0),
            StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 108.0),
          ),
        orderBlocks = emptyList(),
      )

    val result = condition.evaluateWithDetails(stock, stock.quotes.last())

    assertTrue(result.passed)
    assertEquals("No blocks", result.actualValue)
    assertEquals(">= 30d old", result.threshold)
    assertTrue(result.message?.contains("No relevant bearish order blocks found") ?: false)
    assertTrue(result.message?.contains("safe to enter") ?: false)
    assertTrue(result.message?.contains("✓") ?: false)
  }

  @Test
  fun `should provide detailed evaluation when insufficient cooldown`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 5, ageInDays = 0)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // 3 quotes: inside OB, then 2 above - not enough cooldown for 5 bars
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 100.0), // Inside OB
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 107.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 108.0), // Above
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    val result = condition.evaluateWithDetails(stock, quotes.last())

    assertFalse(result.passed)
    assertTrue(result.message?.contains("only") ?: false)
    assertTrue(result.message?.contains("✗") ?: false)
  }

  @Test
  fun `should return false when any day was inside order block recently`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    // Order block at 95-105
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days - above, inside, below
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 110.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 100.0), // Inside
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 92.0), // Below (current)
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should return false when price was inside OB only 0 bars ago (need 3 bars cooldown)",
    )
  }

  @Test
  fun `should return false when current day is inside order block`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    // Order block at 95-105
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days - above, above, inside (current)
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 110.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 108.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 100.0), // Inside (current)
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should return false when current day is inside order block",
    )
  }

  @Test
  fun `should return true when price never entered order block`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    // Order block at 95-105
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days - below, above, above (never inside OB)
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 90.0), // Below (not inside)
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 107.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 110.0), // Above (current)
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should return true when price was never inside OB (cooldown not applicable)",
    )
  }

  @Test
  fun `should provide detailed evaluation when recently inside OB`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days - above, inside, below
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 110.0),
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 100.0), // Inside
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 92.0),
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    val result = condition.evaluateWithDetails(stock, quotes.last())

    assertFalse(result.passed)
    assertEquals("AboveBearishOrderBlockCondition", result.conditionType)
    assertTrue(result.message?.contains("only") ?: false)
    assertTrue(result.message?.contains("95.00") ?: false)
    assertTrue(result.message?.contains("105.00") ?: false)
    assertTrue(result.message?.contains("✗") ?: false)
  }

  @Test
  fun `should provide detailed evaluation when current day is inside order block`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val testDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), testDate).toMutableList()

    // Override last 3 days - above, above, inside
    val lastThreeDates = listOf(
      LocalDate.of(2024, 2, 27),
      LocalDate.of(2024, 2, 28),
      LocalDate.of(2024, 2, 29),
    )
    quotes.removeIf { it.date in lastThreeDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 27), closePrice = 110.0),
        StockQuote(date = LocalDate.of(2024, 2, 28), closePrice = 108.0),
        StockQuote(date = LocalDate.of(2024, 2, 29), closePrice = 100.0), // Inside
      ),
    )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    val result = condition.evaluateWithDetails(stock, quotes.last())

    assertFalse(result.passed)
    assertEquals("Currently inside", result.actualValue)
    assertTrue(result.message?.contains("Currently inside OB") ?: false)
    assertTrue(result.message?.contains("✗") ?: false)
  }

  @Test
  fun `should block entry when previous day was inside order block even if order block ends on entry date`() {
    // This test covers the TSLA July 2, 2024 scenario:
    // - June 28: inside order block
    // - July 1: above order block
    // - July 2: above order block (order block ends on this day)
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 30)

    val orderBlock =
      OrderBlock(
        low = 184.54,
        high = 198.87,
        startDate = LocalDate.of(2024, 4, 29),
        endDate = LocalDate.of(2024, 7, 2), // Ends on entry date!
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Entry date is July 2, 2024
    val testDate = LocalDate.of(2024, 7, 2)
    val quotes =
      createTradingDayQuotes(LocalDate.of(2024, 4, 29), testDate).toMutableList()

    // Override the 3 consecutive days before entry
    val keyDates = listOf(
      LocalDate.of(2024, 6, 28),
      LocalDate.of(2024, 7, 1),
      LocalDate.of(2024, 7, 2),
    )
    quotes.removeIf { it.date in keyDates }
    quotes.addAll(
      listOf(
        StockQuote(date = LocalDate.of(2024, 6, 28), closePrice = 197.88), // INSIDE OB
        StockQuote(date = LocalDate.of(2024, 7, 1), closePrice = 209.86), // ABOVE OB
        StockQuote(date = LocalDate.of(2024, 7, 2), closePrice = 231.26), // ABOVE OB
      ),
    )

    val stock =
      Stock(
        symbol = "TSLA",
        sectorSymbol = "XLY",
        quotes = quotes.sortedBy { it.date },
        orderBlocks = listOf(orderBlock),
      )

    // Test the evaluate method
    val passed = condition.evaluate(stock, quotes.sortedBy { it.date }.last())

    assertFalse(passed, "Entry should be BLOCKED because June 28 was inside OB")

    // Test the evaluateWithDetails method
    val result = condition.evaluateWithDetails(stock, quotes.sortedBy { it.date }.last())

    assertFalse(result.passed)
    assertEquals("AboveBearishOrderBlockCondition", result.conditionType)
    assertTrue(result.message?.contains("✗") ?: false, "Message should indicate failure")
  }

  // ============================================================================================
  // PROXIMITY TESTS - Matching TradingView's bearNear logic
  // ============================================================================================

  @Test
  fun `should return false when price is near OB within proximity percent`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 0)

    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 110.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Price at 99.0: ((100 - 99) / 99) * 100 = 1.01% below OB bottom -> within 2% proximity
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 99.0), // Near OB (blocked)
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 115.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 115.0), // Above (current)
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should return false when price was near OB (within 2%) only 1 bar ago (need 3 bars cooldown)",
    )
  }

  @Test
  fun `should not treat price as near when beyond proximity percent`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 0)

    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 110.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Price at 95.0: ((100 - 95) / 95) * 100 = 5.26% below OB bottom -> beyond 2% proximity
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 95.0), // Beyond proximity (NOT blocked)
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 115.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 115.0), // Above (current)
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should return true when price was 5.26% below OB (beyond 2% proximity threshold)",
    )
  }

  @Test
  fun `should respect custom proximity percent parameter`() {
    // Use 5% proximity -- even 96.0 should be blocked
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 0, proximityPercent = 5.0)

    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 110.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Price at 96.0: ((100 - 96) / 96) * 100 = 4.17% below OB bottom -> within 5% proximity
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 96.0), // Near OB with 5% threshold
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 115.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 115.0), // Above (current)
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should return false when price was within 5% proximity of OB (custom threshold)",
    )
  }

  @Test
  fun `should only check inside when proximity percent is zero`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 0, proximityPercent = 0.0)

    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 110.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Price at 99.0 is below OB bottom but with 0% proximity it should NOT be blocked
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 99.0), // Below but not inside
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 115.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 115.0), // Above (current)
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should return true when proximityPercent=0 and price was below (not inside) OB",
    )
  }

  @Test
  fun `proximity near OB should reset cooldown like TradingView`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 0)

    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 110.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Sequence: inside OB, 3 bars above (cooldown would expire), then near OB resets cooldown,
    // then only 2 bars above -> should still be blocked
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 12), closePrice = 105.0), // Inside OB
        StockQuote(date = LocalDate.of(2024, 2, 13), closePrice = 115.0), // Above (1)
        StockQuote(date = LocalDate.of(2024, 2, 14), closePrice = 115.0), // Above (2)
        StockQuote(date = LocalDate.of(2024, 2, 15), closePrice = 115.0), // Above (3) - cooldown would expire here without proximity
        StockQuote(date = LocalDate.of(2024, 2, 16), closePrice = 99.0), // Near OB (1.01% below) - RESETS cooldown
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 115.0), // Above (1 since near)
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 115.0), // Above (2 since near) - current
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should return false because proximity to OB reset the cooldown, only 1 bar since near (need 3)",
    )
  }

  @Test
  fun `barsSince should match TradingView barssince - blocked exactly N bars ago allows entry with cooldown N`() {
    // Regression test for off-by-one fix.
    // TV's ta.barssince(obEntryBlocked) counts bars FROM the blocked bar TO the current bar.
    // If blocked on bar X, and current bar is X+3, barssince = 3.
    // With cooldown = 3, 3 >= 3 = true → entry allowed.
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 0)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Bar 0: inside OB (blocked), then exactly 3 bars above → should pass cooldown
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 15), closePrice = 100.0), // Inside OB (blocked)
        StockQuote(date = LocalDate.of(2024, 2, 16), closePrice = 115.0), // Above (1)
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 115.0), // Above (2)
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 115.0), // Above (3) - current, barsSince=3
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertTrue(
      condition.evaluate(stock, quotes.last()),
      "Should allow entry when blocked bar was exactly consecutiveDays (3) bars ago, matching TV's barssince semantics",
    )
  }

  @Test
  fun `barsSince should block when blocked bar was only N-1 bars ago with cooldown N`() {
    // With cooldown = 3, if blocked 2 bars ago, barssince = 2 < 3 → blocked
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 0)

    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 16), closePrice = 100.0), // Inside OB (blocked)
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 115.0), // Above (1)
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 115.0), // Above (2) - current, barsSince=2
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    assertFalse(
      condition.evaluate(stock, quotes.last()),
      "Should block entry when blocked bar was only 2 bars ago (need 3)",
    )
  }

  @Test
  fun `should provide detailed evaluation showing near status when price is near OB`() {
    val condition = AboveBearishOrderBlockCondition(consecutiveDays = 3, ageInDays = 0)

    val orderBlock =
      OrderBlock(
        low = 100.0,
        high = 110.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Current price is near OB
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 2, 18), closePrice = 115.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 19), closePrice = 115.0), // Above
        StockQuote(date = LocalDate.of(2024, 2, 20), closePrice = 99.0), // Near OB (current)
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    val result = condition.evaluateWithDetails(stock, quotes.last())

    assertFalse(result.passed)
    assertTrue(result.actualValue?.contains("near") ?: false)
    assertTrue(result.message?.contains("near") ?: false)
    assertTrue(result.message?.contains("✗") ?: false)
  }
}
