package com.skrymer.udgaard.model.strategy.condition.entry
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EmaBullishCrossConditionTest {
  @Test
  fun `should detect bullish crossover when 10 EMA crosses above 20 EMA`() {
    val condition = EmaBullishCrossCondition(fastEma = 10, slowEma = 20)

    // Previous quote: 10 EMA below 20 EMA
    val previousQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 100.0
      )

    // Current quote: 10 EMA above 20 EMA (crossover!)
    val currentQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 2),
        closePriceEMA10 = 101.0,
        closePriceEMA20 = 100.0
      )

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = listOf(previousQuote, currentQuote),
        orderBlocks = mutableListOf(),
      )

    assertTrue(
      condition.evaluate(stock, currentQuote),
      "Should detect bullish crossover when fast EMA crosses above slow EMA",
    )
  }

  @Test
  fun `should not trigger when already in bullish position without crossover`() {
    val condition = EmaBullishCrossCondition(fastEma = 10, slowEma = 20)

    // Previous quote: 10 EMA already above 20 EMA
    val previousQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePriceEMA10 = 101.0,
        closePriceEMA20 = 100.0
      )

    // Current quote: 10 EMA still above 20 EMA (no crossover)
    val currentQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 2),
        closePriceEMA10 = 102.0,
        closePriceEMA20 = 100.0
      )

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = listOf(previousQuote, currentQuote),
        orderBlocks = mutableListOf(),
      )

    assertFalse(
      condition.evaluate(stock, currentQuote),
      "Should not trigger when fast EMA was already above slow EMA",
    )
  }

  @Test
  fun `should not trigger when 10 EMA is below 20 EMA`() {
    val condition = EmaBullishCrossCondition(fastEma = 10, slowEma = 20)

    val currentQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 2),
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 100.0
      )

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = mutableListOf(currentQuote),
        orderBlocks = mutableListOf(),
      )

    assertFalse(
      condition.evaluate(stock, currentQuote),
      "Should not trigger when fast EMA is below slow EMA",
    )
  }

  @Test
  fun `should work with different EMA periods`() {
    val condition = EmaBullishCrossCondition(fastEma = 5, slowEma = 10)

    val previousQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePriceEMA5 = 95.0,
        closePriceEMA10 = 100.0
      )

    val currentQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 2),
        closePriceEMA5 = 101.0,
        closePriceEMA10 = 100.0
      )

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = mutableListOf(previousQuote, currentQuote),
        orderBlocks = mutableListOf(),
      )

    assertTrue(
      condition.evaluate(stock, currentQuote),
      "Should work with 5 EMA and 10 EMA",
    )
  }

  @Test
  fun `should detect crossover at exact equal point`() {
    val condition = EmaBullishCrossCondition(fastEma = 10, slowEma = 20)

    // Previous quote: 10 EMA equal to 20 EMA (touching)
    val previousQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePriceEMA10 = 100.0,
        closePriceEMA20 = 100.0
      )

    // Current quote: 10 EMA above 20 EMA (crossed)
    val currentQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 2),
        closePriceEMA10 = 101.0,
        closePriceEMA20 = 100.0
      )

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = mutableListOf(previousQuote, currentQuote),
        orderBlocks = mutableListOf(),
      )

    assertTrue(
      condition.evaluate(stock, currentQuote),
      "Should detect crossover when previous values were equal",
    )
  }

  @Test
  fun `should return correct description`() {
    val condition = EmaBullishCrossCondition(fastEma = 10, slowEma = 20)
    assertEquals("10EMA crosses above 20EMA", condition.description())
  }

  @Test
  fun `should handle missing EMA values gracefully`() {
    val condition = EmaBullishCrossCondition(fastEma = 10, slowEma = 20)

    val currentQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 2),
        closePriceEMA10 = 0.0, // Missing data
        closePriceEMA20 = 100.0
      )

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = mutableListOf(currentQuote),
        orderBlocks = mutableListOf(),
      )

    assertFalse(
      condition.evaluate(stock, currentQuote),
      "Should return false when EMA values are missing (0)",
    )
  }

  @Test
  fun `should work when no previous quote exists`() {
    val condition = EmaBullishCrossCondition(fastEma = 10, slowEma = 20)

    // Only one quote (first day) with bullish setup
    val currentQuote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 1),
        closePriceEMA10 = 101.0,
        closePriceEMA20 = 100.0
      )

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = mutableListOf(currentQuote),
        orderBlocks = mutableListOf(),
      )

    assertTrue(
      condition.evaluate(stock, currentQuote),
      "Should return true when no previous quote and currently bullish",
    )
  }
}
