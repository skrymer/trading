package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PriceBelowEmaForDaysExitTest {
  @Test
  fun `should exit when price is below EMA for consecutive days`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 4)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, closePriceEMA10 = 102.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 99.0, closePriceEMA10 = 101.0),
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 98.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 4), closePrice = 97.0, closePriceEMA10 = 99.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())
    val currentQuote = quotes[3] // 4th day

    assertTrue(
      condition.shouldExit(stock, null, currentQuote),
      "Should exit when price has been below 10 EMA for 4 consecutive days",
    )
  }

  @Test
  fun `should not exit when streak is broken`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 4)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, closePriceEMA10 = 102.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 99.0, closePriceEMA10 = 101.0),
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 103.0, closePriceEMA10 = 100.0), // Above EMA
        StockQuote(date = LocalDate.of(2024, 1, 4), closePrice = 97.0, closePriceEMA10 = 99.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())
    val currentQuote = quotes[3]

    assertFalse(
      condition.shouldExit(stock, null, currentQuote),
      "Should not exit when consecutive days streak is broken",
    )
  }

  @Test
  fun `should work with single day requirement`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 1)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertTrue(
      condition.shouldExit(stock, null, quotes[0]),
      "Should exit on first day when requirement is 1 day",
    )
  }

  @Test
  fun `should not exit when current price is above EMA`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 3)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 102.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA10 = 101.0),
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 105.0, closePriceEMA10 = 100.0), // Above EMA
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertFalse(
      condition.shouldExit(stock, null, quotes[2]),
      "Should not exit when current price is above EMA",
    )
  }

  @Test
  fun `should not exit when not enough historical data`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 5)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertFalse(
      condition.shouldExit(stock, null, quotes[1]),
      "Should not exit when insufficient historical data for 5 days",
    )
  }

  @Test
  fun `should work with EMA 5`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 5, consecutiveDays = 2)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA5 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA5 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertTrue(
      condition.shouldExit(stock, null, quotes[1]),
      "Should work with EMA 5",
    )
  }

  @Test
  fun `should work with EMA 20`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 20, consecutiveDays = 2)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA20 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA20 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertTrue(
      condition.shouldExit(stock, null, quotes[1]),
      "Should work with EMA 20",
    )
  }

  @Test
  fun `should work with EMA 50`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 50, consecutiveDays = 2)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA50 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA50 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertTrue(
      condition.shouldExit(stock, null, quotes[1]),
      "Should work with EMA 50",
    )
  }

  @Test
  fun `should not exit when price equals EMA`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 2)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 100.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertFalse(
      condition.shouldExit(stock, null, quotes[1]),
      "Should not exit when price equals EMA (not below)",
    )
  }

  @Test
  fun `should handle zero or negative consecutive days`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 0)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertFalse(
      condition.shouldExit(stock, null, quotes[0]),
      "Should not exit when consecutive days is 0",
    )
  }

  @Test
  fun `should handle long consecutive streaks`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 10)

    val quotes =
      (1..10)
        .map { day ->
          StockQuote(
            date = LocalDate.of(2024, 1, day),
            closePrice = 95.0,
            closePriceEMA10 = 100.0,
          )
        }.toMutableList()

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertTrue(
      condition.shouldExit(stock, null, quotes[9]),
      "Should exit when price has been below EMA for 10 consecutive days",
    )
  }

  @Test
  fun `should not exit when only recent days are below EMA`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 5)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 105.0, closePriceEMA10 = 100.0), // Above
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 99.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 98.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 4), closePrice = 97.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 5), closePrice = 96.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertFalse(
      condition.shouldExit(stock, null, quotes[4]),
      "Should not exit when only 4 consecutive days below EMA (need 5)",
    )
  }

  @Test
  fun `should provide correct exit reason`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 4)
    assertEquals("Price has been under the 10 EMA for 4 consecutive days", condition.exitReason())
  }

  @Test
  fun `should provide correct description`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 4)
    assertEquals("Price below 10 EMA for 4 days", condition.description())
  }

  @Test
  fun `should use default values`() {
    val condition = PriceBelowEmaForDaysExit()
    assertEquals("Price below 10 EMA for 3 days", condition.description())
  }

  @Test
  fun `should exit when quotes are sorted and consecutive days below EMA`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 3)

    // Quotes sorted by date ascending (Stock enforces this invariant)
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 99.5, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 98.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertTrue(
      condition.shouldExit(stock, null, quotes[2]),
      "Should exit after 3 consecutive days below EMA",
    )
  }

  @Test
  fun `should handle zero EMA value gracefully`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 2)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 0.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA10 = 0.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    // When EMA is 0, price (98 or 99) is above it, so should not exit
    assertFalse(
      condition.shouldExit(stock, null, quotes[1]),
      "Should not exit when EMA value is zero (price is above zero)",
    )
  }

  @Test
  fun `should handle mixed valid and zero EMA values`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 3)

    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA10 = 0.0), // Zero EMA breaks streak
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 97.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())

    assertFalse(
      condition.shouldExit(stock, null, quotes[2]),
      "Should not exit when streak is broken by zero EMA day",
    )
  }

  @Test
  fun `should exclude entry quote from consecutive days count`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 4)

    // Entry on Jan 1, all days below EMA
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 100.0), // Entry day
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 97.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 4), closePrice = 96.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())
    val entryQuote = quotes[0]

    // On day 4, we have: day 1 (entry, excluded), day 2, day 3, day 4
    // Only 3 consecutive days after entry, not 4
    assertFalse(
      condition.shouldExit(stock, entryQuote, quotes[3]),
      "Should not exit when entry quote is in lookback period (only 3 days after entry)",
    )
  }

  @Test
  fun `should exit when entry quote is excluded and enough days passed`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 3)

    // Entry on Jan 1, need 3 consecutive days AFTER entry
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 100.0), // Entry day
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 98.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 3), closePrice = 97.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 4), closePrice = 96.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())
    val entryQuote = quotes[0]

    // On day 4, we have: day 2, day 3, day 4 = 3 consecutive days after entry
    assertTrue(
      condition.shouldExit(stock, entryQuote, quotes[3]),
      "Should exit when 3 consecutive days after entry are below EMA",
    )
  }

  @Test
  fun `should work correctly when entry quote is not in lookback period`() {
    val condition = PriceBelowEmaForDaysExit(emaPeriod = 10, consecutiveDays = 3)

    // Entry on Jan 1, but checking on Jan 10
    val quotes =
      listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 99.0, closePriceEMA10 = 100.0), // Entry day
        StockQuote(date = LocalDate.of(2024, 1, 2), closePrice = 105.0, closePriceEMA10 = 100.0), // Above EMA
        StockQuote(date = LocalDate.of(2024, 1, 8), closePrice = 98.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 9), closePrice = 97.0, closePriceEMA10 = 100.0),
        StockQuote(date = LocalDate.of(2024, 1, 10), closePrice = 96.0, closePriceEMA10 = 100.0),
      )

    val stock = Stock("TEST", null, quotes = quotes, orderBlocks = listOf())
    val entryQuote = quotes[0]

    // On day 10, lookback only includes days 8, 9, 10 (entry is not in lookback)
    assertTrue(
      condition.shouldExit(stock, entryQuote, quotes[4]),
      "Should exit when entry quote is not in lookback period and 3 consecutive days are below EMA",
    )
  }
}
