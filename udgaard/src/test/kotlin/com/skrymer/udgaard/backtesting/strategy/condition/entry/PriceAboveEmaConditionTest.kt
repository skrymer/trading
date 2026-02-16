package com.skrymer.udgaard.backtesting.strategy.condition.entry
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PriceAboveEmaConditionTest {
  private val stock = Stock()

  @Test
  fun `should return true when price is above 5 EMA`() {
    val condition = PriceAboveEmaCondition(emaPeriod = 5)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 105.0,
        closePriceEMA5 = 100.0,
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when price is above 5 EMA",
    )
  }

  @Test
  fun `should return false when price is below 5 EMA`() {
    val condition = PriceAboveEmaCondition(emaPeriod = 5)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 95.0,
        closePriceEMA5 = 100.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when price is below 5 EMA",
    )
  }

  @Test
  fun `should return true when price is above 10 EMA`() {
    val condition = PriceAboveEmaCondition(emaPeriod = 10)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 105.0,
        closePriceEMA10 = 100.0,
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when price is above 10 EMA",
    )
  }

  @Test
  fun `should return true when price is above 20 EMA`() {
    val condition = PriceAboveEmaCondition(emaPeriod = 20)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 105.0,
        closePriceEMA20 = 100.0,
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when price is above 20 EMA",
    )
  }

  @Test
  fun `should return true when price is above 50 EMA`() {
    val condition = PriceAboveEmaCondition(emaPeriod = 50)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 105.0,
        closePriceEMA50 = 100.0,
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when price is above 50 EMA",
    )
  }

  @Test
  fun `should return false when price equals EMA`() {
    val condition = PriceAboveEmaCondition(emaPeriod = 20)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 100.0,
        closePriceEMA20 = 100.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when price equals EMA",
    )
  }

  @Test
  fun `should return false for unsupported EMA period`() {
    val condition = PriceAboveEmaCondition(emaPeriod = 30)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 105.0,
        closePriceEMA20 = 100.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false for unsupported EMA period",
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = PriceAboveEmaCondition(emaPeriod = 20)
    assertEquals("Price > 20EMA", condition.description())
  }
}
