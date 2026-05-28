package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class Pullback2of3ConditionTest {
  private val condition = Pullback2of3Condition()
  private val baseDate = LocalDate.of(2024, 6, 15)

  @Test
  fun `permits entry when all three sub-conditions hold`() {
    // Given: low ladder where today (95) > the bar exactly 10 trading days ago (90),
    // EMA20 rising (99 -> 100), in-zone (close = EMA20)
    val stock = stockWith(
      quotes = ladderQuotes(lookbackBarLow = 90.0, lowToday = 95.0, ema20Today = 100.0, ema20Prev = 99.0),
    )
    val quote = stock.quotes.last()

    // When
    val passed = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then
    assertTrue(passed, "Entry should be permitted when all three sub-conditions hold")
  }

  @Test
  fun `higher-low check uses the exact lookbackDays trading-day-ago bar`() {
    // Given: lookbackDays = 5, so the comparison must read the bar 5 trading days ago, not
    // 4 (off-by-one too small) or 6 (off-by-one too large). The ladder sets the 5-days-ago
    // bar to low=100; both immediate neighbors (4d ago and 6d ago) are very low (50). Today
    // is 99 — below the 5d-ago bar, above the neighbors. Higher-low MUST fail because of
    // the 5d-ago position; if the implementation reads either neighbor it would pass.
    val five = Pullback2of3Condition(lookbackDays = 5)
    val quotes = (0..10)
      .map { i ->
        val daysBack = 10 - i // i=0 → 10d ago, i=10 → today
        val low = when (daysBack) {
          5 -> 100.0 // the 5d-ago bar (the one that MUST be read)
          4, 6 -> 50.0 // immediate neighbors — would pass if we off-by-one
          0 -> 99.0 // today's low
          else -> 50.0
        }
        sampleQuote(
          date = baseDate.minusDays(daysBack.toLong()),
          low = low,
          closePrice = if (daysBack == 0) 100.0 else 90.0,
          closePriceEMA20 = if (daysBack == 0) 100.0 else 99.0,
        )
      }.toMutableList()
    val stock = stockWith(quotes = quotes)
    val quote = stock.quotes.last()

    // When
    val passed = five.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then: higher-low fails (today's 99 < 5d-ago 100); in-zone passes (close=EMA20);
    // EMA-rising fails (today 100 vs yesterday 99 → rising; wait actually rising YES).
    // 2 of 3 (in-zone + EMA rising) — passes at default minSubConditions=2.
    // To prove the lookback specifically, we need a 3-of-3 variant test.
    assertTrue(passed, "Default 2-of-3 permits when 2 sub-conditions hold")

    // Tighten to 3-of-3 to force higher-low to matter
    val strict = Pullback2of3Condition(lookbackDays = 5, minSubConditions = 3)
    val strictPassed = strict.evaluate(stock, quote, BacktestContext.EMPTY)
    assertFalse(
      strictPassed,
      "3-of-3 must reject: higher-low (today 99 vs 5d-ago 100) fails — proves index reads exact position 5 days back",
    )
  }

  @Test
  fun `lookbackDays equals 1 compares today's low to yesterday's low`() {
    // Given: lookbackDays = 1 — the most off-by-one-sensitive setting. Today's low MUST
    // be compared against yesterday's low, not today's own low (which would be trivially false).
    val oneDay = Pullback2of3Condition(lookbackDays = 1, minSubConditions = 1)
    val quotes = mutableListOf(
      sampleQuote(date = baseDate.minusDays(2), low = 50.0),
      sampleQuote(date = baseDate.minusDays(1), low = 80.0), // yesterday
      sampleQuote(date = baseDate, low = 90.0, closePrice = 200.0, closePriceEMA20 = 100.0), // today, far from EMA20
    )
    val stock = stockWith(quotes = quotes)
    val quote = stock.quotes.last()

    // When (in-zone fails because |200-100|=100 > 1.5×ATR=1.5; EMA-rising depends on yesterday; higher-low: 90 > 80)
    val passed = oneDay.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then: with minSubConditions=1, higher-low alone is enough; today (90) > yesterday (80) → pass
    assertTrue(passed, "lookbackDays=1 must compare today's low against yesterday's low, not against itself")
  }

  @Test
  fun `suppresses entry when only 1 of 3 sub-conditions holds`() {
    // Given: in-zone holds; higher-low fails (today < 10d-ago); EMA flat
    val stock = stockWith(
      quotes = ladderQuotes(lookbackBarLow = 100.0, lowToday = 80.0, ema20Today = 100.0, ema20Prev = 100.0),
    )
    val quote = stock.quotes.last()

    // When
    val passed = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then
    assertFalse(passed, "Entry should be suppressed when only 1 of 3 sub-conditions holds")
  }

  @Test
  fun `suppresses entry when ATR is non-positive`() {
    // Given: a quote with ATR = 0 — cannot compute the in-zone band
    val stock = stockWith(
      quotes = ladderQuotes(lookbackBarLow = 90.0, lowToday = 95.0, ema20Today = 100.0, ema20Prev = 99.0)
        .map { it.copy(atr = 0.0) }
        .toMutableList(),
    )
    val quote = stock.quotes.last()

    // When
    val passed = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then
    assertFalse(passed, "Entry should be suppressed when ATR is non-positive")
  }

  @Test
  fun `suppresses entry when insufficient history for lookback`() {
    // Given: only 5 quotes total, but lookbackDays = 10 needs >= 11
    val quotes = (0..4)
      .map { i ->
        sampleQuote(date = baseDate.minusDays((4 - i).toLong()), low = 95.0)
      }.toMutableList()
    val stock = stockWith(quotes = quotes)
    val quote = stock.quotes.last()

    // When
    val passed = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then
    assertFalse(passed, "Entry should be suppressed when history < lookbackDays + 1")
  }

  @Test
  fun `lookbackDays equals N requires N+1 bars (today plus N prior)`() {
    // Given: lookbackDays=10, exactly 11 quotes (today + 10 prior). Should succeed if history math is correct.
    val ten = Pullback2of3Condition(lookbackDays = 10, minSubConditions = 1)
    val quotes = (0..10)
      .map { i ->
        val daysBack = 10 - i
        // Set bar 10 trading days ago to low=50, today's low=80 → higher-low passes
        val low = when (daysBack) {
          10 -> 50.0
          0 -> 80.0 // today
          else -> 70.0
        }
        sampleQuote(date = baseDate.minusDays(daysBack.toLong()), low = low)
      }.toMutableList()
    val stock = stockWith(quotes = quotes)
    val quote = stock.quotes.last()

    // When
    val passed = ten.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then: with exactly 11 bars, higher-low test fires correctly (80 > 50)
    assertTrue(passed, "11 bars must satisfy the lookbackDays=10 history requirement")
  }

  @Test
  fun `parseConfig honors all three parameters`() {
    val params = mapOf("atrMultiple" to 2.0, "lookbackDays" to 5, "minSubConditions" to 3)
    val configured = condition.parseConfig(params) as Pullback2of3Condition

    assertEquals("Pullback 3-of-3 (in-zone 2.0×ATR / higher-low 5d / EMA20 rising)", configured.description())
  }

  @Test
  fun `metadata exposes stable identifiers`() {
    val metadata = condition.getMetadata()

    assertEquals("pullback2of3", metadata.type)
    assertEquals("Stock", metadata.category)
    assertEquals(3, metadata.parameters.size)
  }

  /**
   * Build a trailing window of quotes where ONLY the bar exactly `lookbackDays` (=10) trading
   * days before `baseDate` carries `lookbackBarLow`; all other prior bars use distinct
   * placeholder lows so any off-by-one read would fall on a different number. Today's bar gets
   * `lowToday`, `ema20Today`, and the previous bar's `closePriceEMA20 = ema20Prev`.
   */
  private fun ladderQuotes(
    lookbackBarLow: Double,
    lowToday: Double,
    ema20Today: Double,
    ema20Prev: Double,
  ): MutableList<StockQuote> {
    val quotes = mutableListOf<StockQuote>()
    // 10 prior bars: position 0 = 10 days back, position 9 = 1 day back (yesterday)
    for (i in 0..9) {
      val daysBack = 10 - i // 10, 9, 8, ..., 1
      val low = when (daysBack) {
        10 -> lookbackBarLow // the lookback target
        else -> 70.0 + daysBack.toDouble() // 71..79, distinct from lookbackBarLow
      }
      val ema = if (daysBack == 1) ema20Prev else 99.0
      quotes.add(
        sampleQuote(
          date = baseDate.minusDays(daysBack.toLong()),
          low = low,
          closePriceEMA20 = ema,
        ),
      )
    }
    // Today
    quotes.add(
      sampleQuote(
        date = baseDate,
        low = lowToday,
        closePrice = ema20Today,
        closePriceEMA20 = ema20Today,
      ),
    )
    return quotes
  }

  private fun sampleQuote(
    date: LocalDate,
    low: Double = 95.0,
    closePrice: Double = 100.0,
    closePriceEMA20: Double = 100.0,
  ): StockQuote = StockQuote(
    symbol = "TEST",
    date = date,
    openPrice = closePrice,
    closePrice = closePrice,
    high = closePrice + 1.0,
    low = low,
    closePriceEMA20 = closePriceEMA20,
    atr = 1.0,
    volume = 100_000L,
  )

  private fun stockWith(quotes: MutableList<StockQuote>): Stock =
    Stock(symbol = "TEST", quotes = quotes)
}
