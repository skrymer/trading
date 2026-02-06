package com.skrymer.udgaard.model.strategy.condition.exit
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EmaCrossExitTest {
  @Test
  fun `should exit when 10 EMA crosses under 20 EMA`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            // Yesterday: 10 EMA was above 20 EMA
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA10 = 105.0,
              closePriceEMA20 = 100.0,
            ),
            // Today: 10 EMA crossed below 20 EMA
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 15),
              closePriceEMA10 = 95.0,
              closePriceEMA20 = 100.0,
            ),
          )
      )

    val quote = stock.quotes[1] // Today's quote

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should exit when 10 EMA crosses from above to below 20 EMA",
    )
  }

  @Test
  fun `should not exit when 10 EMA is above 20 EMA`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA10 = 100.0,
              closePriceEMA20 = 95.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 15),
              closePriceEMA10 = 105.0,
              closePriceEMA20 = 100.0,
            ),
          )
      )

    val quote = stock.quotes[1]

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when 10 EMA is still above 20 EMA",
    )
  }

  @Test
  fun `should not exit when 10 EMA was already below 20 EMA (no new crossover)`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            // Yesterday: 10 EMA was already below 20 EMA
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA10 = 95.0,
              closePriceEMA20 = 100.0,
            ),
            // Today: Still below (no crossover)
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 15),
              closePriceEMA10 = 93.0,
              closePriceEMA20 = 100.0,
            ),
          )
      )

    val quote = stock.quotes[1]

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when 10 EMA was already below 20 EMA (no new crossover)",
    )
  }

  @Test
  fun `should exit when 10 EMA crosses from equal to below`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            // Yesterday: EMAs were equal
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA10 = 100.0,
              closePriceEMA20 = 100.0,
            ),
            // Today: 10 EMA dropped below 20 EMA
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 15),
              closePriceEMA10 = 95.0,
              closePriceEMA20 = 100.0,
            ),
          )
      )

    val quote = stock.quotes[1]

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should exit when 10 EMA crosses from equal to below",
    )
  }

  @Test
  fun `should work with 5 EMA and 10 EMA`() {
    val condition = EmaCrossExit(fastEma = 5, slowEma = 10)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            // Yesterday: 5 EMA was above 10 EMA
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA5 = 105.0,
              closePriceEMA10 = 100.0,
            ),
            // Today: 5 EMA crossed below 10 EMA
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 15),
              closePriceEMA5 = 95.0,
              closePriceEMA10 = 100.0,
            ),
          )
      )

    val quote = stock.quotes[1]

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should work with different EMA periods",
    )
  }

  @Test
  fun `should not exit when insufficient historical data`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            // Only one quote, no previous data
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 15),
              closePriceEMA10 = 95.0,
              closePriceEMA20 = 100.0,
            ),
          )
      )

    val quote = stock.quotes[0]

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when there is no previous quote to check for crossover",
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
