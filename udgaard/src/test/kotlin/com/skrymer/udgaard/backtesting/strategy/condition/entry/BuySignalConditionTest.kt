package com.skrymer.udgaard.backtesting.strategy.condition.entry
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BuySignalConditionTest {
  private val stock = Stock()

  @Test
  fun `should return true when has buy signal and daysOld is -1`() {
    val condition = BuySignalCondition(daysOld = -1)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        lastBuySignal = LocalDate.of(2024, 1, 10),
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when buy signal exists",
    )
  }

  @Test
  fun `should return false when no buy signal and daysOld is -1`() {
    val condition = BuySignalCondition(daysOld = -1)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when no buy signal",
    )
  }

  @Test
  fun `should return true when has current buy signal and daysOld is 1`() {
    val condition = BuySignalCondition(daysOld = 1)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        lastBuySignal = LocalDate.of(2024, 1, 14),
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when current buy signal exists",
    )
  }

  @Test
  fun `should return false when no current buy signal and daysOld is 1`() {
    val condition = BuySignalCondition(daysOld = 1)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when no current buy signal",
    )
  }

  @Test
  fun `should provide correct description for regular buy signal`() {
    val condition = BuySignalCondition(daysOld = -1)
    assertEquals("Has buy signal", condition.description())
  }

  @Test
  fun `should provide correct description for current buy signal`() {
    val condition = BuySignalCondition(daysOld = 1)
    assertEquals("Has buy signal (≤ 1 day old)", condition.description())
  }
}
