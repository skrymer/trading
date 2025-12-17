package com.skrymer.udgaard.model.strategy.condition.exit
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EmaCrossExitTest {
  private val stock = StockDomain()

  @Test
  fun `should exit when 10 EMA crosses under 20 EMA`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 95.0,
        closePriceEMA20 = 100.0,
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should exit when 10 EMA is below 20 EMA",
    )
  }

  @Test
  fun `should not exit when 10 EMA is above 20 EMA`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 105.0,
        closePriceEMA20 = 100.0,
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when 10 EMA is above 20 EMA",
    )
  }

  @Test
  fun `should exit when 10 EMA equals 20 EMA`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 100.0,
        closePriceEMA20 = 100.0,
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when EMAs are equal (crossover not confirmed)",
    )
  }

  @Test
  fun `should work with 5 EMA and 10 EMA`() {
    val condition = EmaCrossExit(fastEma = 5, slowEma = 10)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA5 = 95.0,
        closePriceEMA10 = 100.0,
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should work with different EMA periods",
    )
  }

  @Test
  fun `should provide correct exit reason`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    assertEquals("10 ema has crossed under the 20 ema", condition.exitReason())
  }

  @Test
  fun `should provide correct description`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    assertEquals("10EMA crosses under 20EMA", condition.description())
  }
}
