package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SellSignalExitTest {
  private val condition = SellSignalExit()
  private val stock = StockDomain()

  @Test
  fun `should exit when sell signal is present`() {
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
        signal = "Sell",
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should exit when sell signal is present",
    )
  }

  @Test
  fun `should not exit when sell signal is absent`() {
    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 1, 15),
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when sell signal is absent",
    )
  }

  @Test
  fun `should provide correct exit reason`() {
    assertEquals("Sell signal", condition.exitReason())
  }

  @Test
  fun `should provide correct description`() {
    assertEquals("Sell signal", condition.description())
  }
}
