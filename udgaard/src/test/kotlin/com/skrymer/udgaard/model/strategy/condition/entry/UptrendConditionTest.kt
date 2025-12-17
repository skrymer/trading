package com.skrymer.udgaard.model.strategy.condition.entry
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UptrendConditionTest {
  private val condition = UptrendCondition()
  private val stock = StockDomain()

  @Test
  fun `should return true when 10 EMA is above 20 EMA and price is above 50 EMA`() {
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 100.0,
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when 10 EMA > 20 EMA and price > 50 EMA",
    )
  }

  @Test
  fun `should return false when 10 EMA is below 20 EMA`() {
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 100.0,
        closePriceEMA10 = 85.0, // Below 20 EMA
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 80.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when 10 EMA < 20 EMA",
    )
  }

  @Test
  fun `should return false when price is below 50 EMA`() {
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 80.0, // Below 50 EMA
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 85.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when price < 50 EMA",
    )
  }

  @Test
  fun `should return false when both conditions fail`() {
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 80.0, // Below 50 EMA
        closePriceEMA10 = 85.0, // Below 20 EMA
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 95.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when both conditions fail",
    )
  }

  @Test
  fun `should return true at exact boundary when 10 EMA equals 20 EMA plus epsilon and price equals 50 EMA plus epsilon`() {
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 90.01, // Just above 50 EMA
        closePriceEMA10 = 90.01, // Just above 20 EMA
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 90.0,
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when values are just above thresholds",
    )
  }

  @Test
  fun `should return false at exact boundary when values are equal`() {
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 90.0, // Equal to 50 EMA
        closePriceEMA10 = 90.0, // Equal to 20 EMA
        closePriceEMA20 = 90.0,
        closePriceEMA50 = 90.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when values are equal (not greater than)",
    )
  }

  @Test
  fun `should provide correct description`() {
    assertEquals("Stock is in uptrend (10 EMA > 20 EMA and price > 50 EMA)", condition.description())
  }
}
