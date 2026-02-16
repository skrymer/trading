package com.skrymer.udgaard.backtesting.strategy.condition.entry
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ValueZoneConditionTest {
  private val stock = Stock()

  @Test
  fun `should return true when price is within value zone`() {
    val condition = ValueZoneCondition(atrMultiplier = 2.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 105.0,
        closePriceEMA20 = 100.0,
        atr = 2.0, // Value zone upper bound: 100 + (2 * 2) = 104
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when price is above value zone",
    )
  }

  @Test
  fun `should return true when price is just below upper bound`() {
    val condition = ValueZoneCondition(atrMultiplier = 2.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 103.0,
        closePriceEMA20 = 100.0,
        atr = 2.0, // Value zone upper bound: 100 + (2 * 2) = 104
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when price is just below value zone upper bound",
    )
  }

  @Test
  fun `should return false when price equals upper bound`() {
    val condition = ValueZoneCondition(atrMultiplier = 2.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 104.0,
        closePriceEMA20 = 100.0,
        atr = 2.0, // Value zone upper bound: 100 + (2 * 2) = 104
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when price equals value zone upper bound",
    )
  }

  @Test
  fun `should return false when price is at EMA`() {
    val condition = ValueZoneCondition(atrMultiplier = 2.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 100.0,
        closePriceEMA20 = 100.0,
        atr = 2.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when price equals EMA (must be above EMA)",
    )
  }

  @Test
  fun `should work with different ATR multiplier`() {
    val condition = ValueZoneCondition(atrMultiplier = 3.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 105.0,
        closePriceEMA20 = 100.0,
        atr = 2.0, // Value zone upper bound: 100 + (3 * 2) = 106
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should work with custom ATR multiplier",
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = ValueZoneCondition(atrMultiplier = 2.0)
    assertEquals("Price within value zone (20EMA < price < 20EMA + 2.0ATR)", condition.description())
  }

  @Test
  fun `should return false when price is below EMA`() {
    val condition = ValueZoneCondition(atrMultiplier = 2.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 95.0,
        closePriceEMA20 = 100.0,
        atr = 2.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when price is below 20 EMA",
    )
  }
}
