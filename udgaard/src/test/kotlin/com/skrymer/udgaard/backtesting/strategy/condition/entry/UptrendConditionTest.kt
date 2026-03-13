package com.skrymer.udgaard.backtesting.strategy.condition.entry
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UptrendConditionTest {
  private val condition = UptrendCondition()
  private val stock = Stock()

  @Test
  fun `should return true when 5 EMA above 10 EMA above 20 EMA and price above 50 EMA`() {
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 100.0,
        closePriceEMA5 = 98.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when 5 EMA > 10 EMA > 20 EMA and price > 50 EMA",
    )
  }

  @Test
  fun `should return false when 5 EMA is below 10 EMA`() {
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 100.0,
        closePriceEMA5 = 93.0, // Below 10 EMA
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be false when 5 EMA < 10 EMA",
    )
  }

  @Test
  fun `should return false when 10 EMA is below 20 EMA`() {
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 100.0,
        closePriceEMA5 = 88.0,
        closePriceEMA10 = 85.0, // Below 20 EMA
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 80.0,
      )

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be false when 10 EMA < 20 EMA",
    )
  }

  @Test
  fun `should return false when price is below 50 EMA`() {
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 80.0, // Below 50 EMA
        closePriceEMA5 = 98.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be false when price < 50 EMA",
    )
  }

  @Test
  fun `should return false when all conditions fail`() {
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 80.0, // Below 50 EMA
        closePriceEMA5 = 82.0, // Below 10 EMA
        closePriceEMA10 = 85.0, // Below 20 EMA
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 95.0,
      )

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be false when all conditions fail",
    )
  }

  @Test
  fun `should return true at exact boundary when values are just above thresholds`() {
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 90.01, // Just above 50 EMA
        closePriceEMA5 = 90.02, // Just above 10 EMA
        closePriceEMA10 = 90.01, // Just above 20 EMA
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 90.0,
      )

    assertTrue(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be true when values are just above thresholds",
    )
  }

  @Test
  fun `should return false at exact boundary when values are equal`() {
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 90.0, // Equal to 50 EMA
        closePriceEMA5 = 90.0, // Equal to 10 EMA
        closePriceEMA10 = 90.0, // Equal to 20 EMA
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 90.0,
      )

    assertFalse(
      condition.evaluate(stock, quote, BacktestContext.EMPTY),
      "Condition should be false when values are equal (not greater than)",
    )
  }

  @Test
  fun `should provide correct description`() {
    assertEquals("Stock is in uptrend (5 EMA > 10 EMA > 20 EMA and price > 50 EMA)", condition.description())
  }
}
