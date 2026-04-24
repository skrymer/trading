package com.skrymer.udgaard.backtesting.strategy.condition.exit
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EmaCrossExitTest {
  @Test
  fun `should exit when 10 EMA crosses under 20 EMA`() {
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            // Yesterday: 10 EMA was above 20 EMA
            StockQuote(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA10 = 105.0,
              closePriceEMA20 = 100.0,
            ),
            // Today: 10 EMA crossed below 20 EMA
            StockQuote(
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
      Stock(
        quotes =
          mutableListOf(
            StockQuote(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA10 = 100.0,
              closePriceEMA20 = 95.0,
            ),
            StockQuote(
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
      Stock(
        quotes =
          mutableListOf(
            // Yesterday: 10 EMA was already below 20 EMA
            StockQuote(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA10 = 95.0,
              closePriceEMA20 = 100.0,
            ),
            // Today: Still below (no crossover)
            StockQuote(
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
      Stock(
        quotes =
          mutableListOf(
            // Yesterday: EMAs were equal
            StockQuote(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA10 = 100.0,
              closePriceEMA20 = 100.0,
            ),
            // Today: 10 EMA dropped below 20 EMA
            StockQuote(
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
      Stock(
        quotes =
          mutableListOf(
            // Yesterday: 5 EMA was above 10 EMA
            StockQuote(
              date = LocalDate.of(2024, 1, 14),
              closePriceEMA5 = 105.0,
              closePriceEMA10 = 100.0,
            ),
            // Today: 5 EMA crossed below 10 EMA
            StockQuote(
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
      Stock(
        quotes =
          mutableListOf(
            // Only one quote, no previous data
            StockQuote(
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

  // ===== proximity =====

  @Test
  fun `proximity is 1_0 when fast ema equals slow ema`() {
    // Given: fast and slow are the same — cross is imminent.
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val quote = StockQuote(
      date = LocalDate.of(2024, 1, 5),
      closePriceEMA10 = 100.0,
      closePriceEMA20 = 100.0,
    )

    // When
    val proximity = condition.proximity(Stock(), entryQuote, quote)

    // Then: invariant — at the moment of the cross, proximity must be >= 1.0.
    assertNotNull(proximity)
    assertEquals(1.0, proximity!!.proximity, 1e-9)
    assertEquals("emaCross", proximity.conditionType)
  }

  @Test
  fun `proximity is 1_0 when fast ema is below slow ema`() {
    // Given: fast has already crossed under slow — clamp at 1.0.
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val quote = StockQuote(
      date = LocalDate.of(2024, 1, 5),
      closePriceEMA10 = 95.0,
      closePriceEMA20 = 100.0,
    )

    // When
    val proximity = condition.proximity(Stock(), entryQuote, quote)

    // Then
    assertNotNull(proximity)
    assertEquals(1.0, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity is about 0_5 when fast is half an ATR above slow`() {
    // Given: fast is 1.0 above slow, ATR is 2.0 → proximity = 1 - 0.5 = 0.5.
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val quote = StockQuote(
      date = LocalDate.of(2024, 1, 5),
      closePriceEMA10 = 101.0,
      closePriceEMA20 = 100.0,
    )

    // When
    val proximity = condition.proximity(Stock(), entryQuote, quote)

    // Then
    assertNotNull(proximity)
    assertEquals(0.5, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity is 0_0 when fast is more than one ATR above slow`() {
    // Given: fast is 3.0 above slow, ATR is 2.0 → formula -> -0.5, clamped to 0.
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val quote = StockQuote(
      date = LocalDate.of(2024, 1, 5),
      closePriceEMA10 = 103.0,
      closePriceEMA20 = 100.0,
    )

    // When
    val proximity = condition.proximity(Stock(), entryQuote, quote)

    // Then
    assertNotNull(proximity)
    assertEquals(0.0, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity returns null when entryQuote is null`() {
    // Given: no entry reference — ATR scale unavailable.
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val quote = StockQuote(
      date = LocalDate.of(2024, 1, 5),
      closePriceEMA10 = 101.0,
      closePriceEMA20 = 100.0,
    )

    // When / Then
    assertNull(condition.proximity(Stock(), null, quote))
  }

  @Test
  fun `proximity returns null when the cross already happened on a previous bar`() {
    // Given: EMAs have been inverted for at least two bars — the crossover event is
    // in the past, not imminent. Reporting proximity 1.0 every subsequent bar would
    // spam the UI with a stale warning; instead the condition opts out (null).
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 2.0)
    val previousQuote = StockQuote(
      date = LocalDate.of(2024, 1, 4),
      closePriceEMA10 = 95.0,
      closePriceEMA20 = 100.0,
    )
    val currentQuote = StockQuote(
      date = LocalDate.of(2024, 1, 5),
      closePriceEMA10 = 94.0,
      closePriceEMA20 = 100.0,
    )
    val stock = Stock(quotes = listOf(previousQuote, currentQuote))

    // When / Then
    assertNull(condition.proximity(stock, entryQuote, currentQuote))
  }

  @Test
  fun `proximity returns null when entry atr is zero`() {
    // Given: entry with atr=0 — normalization scale is undefined.
    val condition = EmaCrossExit(fastEma = 10, slowEma = 20)
    val entryQuote = StockQuote(date = LocalDate.of(2024, 1, 1), closePrice = 100.0, atr = 0.0)
    val quote = StockQuote(
      date = LocalDate.of(2024, 1, 5),
      closePriceEMA10 = 101.0,
      closePriceEMA20 = 100.0,
    )

    // When / Then
    assertNull(condition.proximity(Stock(), entryQuote, quote))
  }
}
