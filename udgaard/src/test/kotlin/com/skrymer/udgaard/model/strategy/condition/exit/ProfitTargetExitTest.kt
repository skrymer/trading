package com.skrymer.udgaard.model.strategy.condition.exit
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ProfitTargetExitTest {
  private val stock = StockDomain()

  @Test
  fun `should exit when price exceeds profit target`() {
    val condition = ProfitTargetExit(atrMultiplier = 3.0, emaPeriod = 20)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 110.0,
        closePriceEMA20 = 100.0,
        atr = 2.0, // Target: 100 + (3 * 2) = 106
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should exit when price is 3 ATR above 20 EMA",
    )
  }

  @Test
  fun `should not exit when price is below profit target`() {
    val condition = ProfitTargetExit(atrMultiplier = 3.0, emaPeriod = 20)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 104.0,
        closePriceEMA20 = 100.0,
        atr = 2.0, // Target: 100 + (3 * 2) = 106
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when price is below profit target",
    )
  }

  @Test
  fun `should not exit when price equals profit target`() {
    val condition = ProfitTargetExit(atrMultiplier = 3.0, emaPeriod = 20)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 106.0,
        closePriceEMA20 = 100.0,
        atr = 2.0, // Target: 100 + (3 * 2) = 106
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when price exactly equals profit target",
    )
  }

  @Test
  fun `should work with different ATR multiplier`() {
    val condition = ProfitTargetExit(atrMultiplier = 2.0, emaPeriod = 20)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 105.0,
        closePriceEMA20 = 100.0,
        atr = 2.0, // Target: 100 + (2 * 2) = 104
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should work with different ATR multiplier",
    )
  }

  @Test
  fun `should work with 50 EMA`() {
    val condition = ProfitTargetExit(atrMultiplier = 3.0, emaPeriod = 50)
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        closePrice = 110.0,
        closePriceEMA50 = 100.0,
        atr = 2.0, // Target: 100 + (3 * 2) = 106
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should work with different EMA period",
    )
  }

  @Test
  fun `should provide correct exit reason`() {
    val condition = ProfitTargetExit(atrMultiplier = 3.0, emaPeriod = 20)
    assertEquals("Price is 3.0 ATR above 20 EMA", condition.exitReason())
  }

  @Test
  fun `should provide correct description`() {
    val condition = ProfitTargetExit(atrMultiplier = 3.0, emaPeriod = 20)
    assertEquals("Price > 20EMA + 3.0ATR", condition.description())
  }
}
