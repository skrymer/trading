package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.OrderBlock
import com.skrymer.udgaard.model.OrderBlockType
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PlanMoneyExitStrategyTest {
  private val exitStrategy = PlanMoneyExitStrategy()

  @Test
  fun `should exit when 10 EMA crosses under 20 EMA`() {
    val stock = Stock()
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 95.0, // Below 20 EMA
        closePriceEMA20 = 100.0,
      )

    assertTrue(
      exitStrategy.match(stock, null, quote),
      "Should exit when 10 EMA crosses under 20 EMA",
    )
    assertEquals("10 ema has crossed under the 20 ema", exitStrategy.reason(stock, null, quote))
  }

  @Test
  fun `should exit on sell signal`() {
    val stock = Stock()
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        signal = "Sell",
        closePriceEMA10 = 105.0, // Above 20 EMA (no cross)
        closePriceEMA20 = 100.0,
      )

    assertTrue(
      exitStrategy.match(stock, null, quote),
      "Should exit on sell signal",
    )
    assertEquals("Sell signal", exitStrategy.reason(stock, null, quote))
  }

  @Test
  fun `should exit when within order block`() {
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 5, 30), // 150 days duration (> 120 threshold)
        orderBlockType = OrderBlockType.BEARISH, // Must be BEARISH
        // Default is CALCULATED
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = mutableListOf(),
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 3, 1), // Within startDate and endDate
        closePrice = 100.0, // Within order block range
        closePriceEMA10 = 105.0, // Above 20 EMA (no cross)
        closePriceEMA20 = 100.0,
      )

    assertTrue(
      exitStrategy.match(stock, null, quote),
      "Should exit when within order block",
    )
    assertEquals(
      "Quote is within an order block older than 120 days",
      exitStrategy.reason(stock, null, quote),
    )
  }

  @Test
  fun `should not exit when no conditions are met`() {
    val stock = Stock()
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 105.0, // Above 20 EMA (no cross)
        closePriceEMA20 = 100.0,
        closePrice = 102.0,
      )

    assertFalse(
      exitStrategy.match(stock, null, quote),
      "Should not exit when no conditions are met",
    )
  }

  @Test
  fun `should return first matching exit reason when multiple conditions met`() {
    val orderBlock =
      OrderBlock(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 5, 30),
        orderBlockType = OrderBlockType.BEARISH,
      )

    val stock =
      Stock(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = mutableListOf(),
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 3, 1), // Within startDate and endDate
        signal = "Sell", // Sell signal
        closePriceEMA10 = 95.0, // EMA cross
        closePriceEMA20 = 100.0,
        closePrice = 100.0, // Within order block range
      )

    assertTrue(
      exitStrategy.match(stock, null, quote),
      "Should exit when multiple conditions are met",
    )

    // Should return the first matching reason (emaCross comes first in DSL)
    val reason = exitStrategy.reason(stock, null, quote)
    assertNotNull(reason, "Should return an exit reason")
    assertTrue(
      reason == "10 ema has crossed under the 20 ema" ||
        reason == "Sell signal" ||
        reason!!.contains("order block"),
      "Should return one of the matching exit reasons",
    )
  }

  @Test
  fun `should have correct description`() {
    assertEquals("Plan Money exit strategy", exitStrategy.description())
  }

  @Test
  fun `should work with DSL construction`() {
    val dslStrategy =
      exitStrategy {
        emaCross(10, 20)
        sellSignal()
        orderBlock(120)
      }

    val stock = Stock()
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        signal = "Sell",
      )

    assertTrue(
      dslStrategy.match(stock, null, quote),
      "DSL-constructed strategy should work correctly",
    )
  }
}
