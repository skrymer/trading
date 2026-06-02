package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NarrowingRangeConditionTest {
  /** Build consecutive daily bars from (high, low, close) triples, oldest first. */
  private fun stockOf(bars: List<Triple<Double, Double, Double>>): Stock {
    val start = LocalDate.of(2024, 1, 1)
    val quotes =
      bars.mapIndexed { i, (high, low, close) ->
        StockQuote(date = start.plusDays(i.toLong()), high = high, low = low, closePrice = close)
      }
    return Stock(quotes = quotes.toMutableList())
  }

  @Test
  fun `passes when normalized range narrows strictly across the three consecutive windows`() {
    // Given three 2-bar windows whose normalized range narrows toward the present: 0.20 → 0.10 → 0.04
    val condition = NarrowingRangeCondition(stepWindow = 2)
    val stock =
      stockOf(
        listOf(
          Triple(110.0, 90.0, 100.0),
          Triple(108.0, 92.0, 100.0), // oldest window r3: range 20 → 0.20
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0), // middle window r2: range 10 → 0.10
          Triple(102.0, 98.0, 100.0),
          Triple(101.0, 99.0, 100.0), // recent window r1: range 4 → 0.04
        ),
      )
    val quote = stock.quotes.last()

    // When the condition evaluates the current bar
    val result = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then r1 (0.04) < r2 (0.10) < r3 (0.20) → progressive contraction confirmed
    assertTrue(result, "normalized range should be strictly narrowing toward the present")
  }

  @Test
  fun `fails when the most recent window is wider than the middle window`() {
    // Given the recent window range (12) wider than the middle window range (10): not monotone
    val condition = NarrowingRangeCondition(stepWindow = 2)
    val stock =
      stockOf(
        listOf(
          Triple(110.0, 90.0, 100.0),
          Triple(108.0, 92.0, 100.0), // r3: range 20 → 0.20
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0), // r2: range 10 → 0.10
          Triple(106.0, 94.0, 100.0),
          Triple(105.0, 95.0, 100.0), // r1: range 12 → 0.12 (wider)
        ),
      )
    val quote = stock.quotes.last()

    // When the condition evaluates the current bar
    val result = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then r1 (0.12) is not below r2 (0.10) → contraction not confirmed
    assertFalse(result, "should fail when the recent window widens instead of contracting")
  }

  @Test
  fun `fails when fewer than three full step-windows of history are available`() {
    // Given only 5 bars but 3 windows of stepWindow=2 need 6
    val condition = NarrowingRangeCondition(stepWindow = 2)
    val stock =
      stockOf(
        listOf(
          Triple(110.0, 90.0, 100.0),
          Triple(108.0, 92.0, 100.0),
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0),
          Triple(102.0, 98.0, 100.0),
        ),
      )
    val quote = stock.quotes.last()

    // When the condition evaluates the current bar
    val result = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then it fails closed on insufficient history (5 < 6)
    assertFalse(result, "should fail closed when bars (5) < 3 * stepWindow (6)")
  }

  @Test
  fun `future-dated bars after the current bar are excluded from the windows`() {
    // Given a clean contraction ending at an interior bar, followed by wide-range future bars
    // that would break the contraction if they leaked into the most-recent window
    val condition = NarrowingRangeCondition(stepWindow = 2)
    val stock =
      stockOf(
        listOf(
          Triple(110.0, 90.0, 100.0),
          Triple(108.0, 92.0, 100.0), // r3
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0), // r2
          Triple(102.0, 98.0, 100.0),
          Triple(101.0, 99.0, 100.0), // r1 (interior bar at index 5)
          Triple(130.0, 70.0, 100.0),
          Triple(135.0, 65.0, 100.0), // future wide bars (must be excluded)
        ),
      )
    val interiorQuote = stock.quotes[5]

    // When evaluating the interior bar (not the last quote)
    val result = condition.evaluate(stock, interiorQuote, BacktestContext.EMPTY)

    // Then only bars up to and including the interior bar count → contraction still confirmed
    assertTrue(result, "future wide-range bars must not enter the windows when evaluating an earlier bar")
  }

  @Test
  fun `fails when two adjacent windows have equal range (strict contraction required)`() {
    // Given the recent and middle windows with identical range (10) — a flat step, not a contraction
    val condition = NarrowingRangeCondition(stepWindow = 2)
    val stock =
      stockOf(
        listOf(
          Triple(110.0, 90.0, 100.0),
          Triple(108.0, 92.0, 100.0), // r3: range 20 → 0.20
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0), // r2: range 10 → 0.10
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0), // r1: range 10 → 0.10 (equal)
        ),
      )
    val quote = stock.quotes.last()

    // When the condition evaluates the current bar
    val result = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then r1 == r2 fails the strict r1 < r2 contraction
    assertFalse(result, "equal adjacent windows must fail strict contraction")
  }

  @Test
  fun `parseConfig applies the configured stepWindow so the parsed condition evaluates with it`() {
    // Given the default condition (stepWindow 10 → needs 30 bars) which would fail-closed on 6 bars
    val parsed = NarrowingRangeCondition().parseConfig(mapOf("stepWindow" to 2))
    val stock =
      stockOf(
        listOf(
          Triple(110.0, 90.0, 100.0),
          Triple(108.0, 92.0, 100.0),
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0),
          Triple(102.0, 98.0, 100.0),
          Triple(101.0, 99.0, 100.0),
        ),
      )
    val quote = stock.quotes.last()

    // When the parsed condition evaluates the 6-bar narrowing fixture
    val result = parsed.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then the configured stepWindow=2 takes effect and contraction passes
    assertTrue(result, "parseConfig should apply stepWindow=2 so the 6-bar contraction passes")
  }

  @Test
  fun `metadata exposes the routing type and the stepWindow parameter`() {
    // Given the default condition
    val metadata = NarrowingRangeCondition().getMetadata()

    // Then the registry routing type and parameter default are exposed
    assertEquals("narrowingRange", metadata.type)
    assertEquals("Volatility", metadata.category)
    assertEquals(1, metadata.parameters.size)
    assertEquals("stepWindow", metadata.parameters.first().name)
    assertEquals(10, metadata.parameters.first().defaultValue)
  }

  @Test
  fun `evaluateWithDetails verdict mirrors evaluate for pass and fail`() {
    // Given a contracting fixture and a non-contracting fixture
    val condition = NarrowingRangeCondition(stepWindow = 2)
    val contracting =
      stockOf(
        listOf(
          Triple(110.0, 90.0, 100.0),
          Triple(108.0, 92.0, 100.0),
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0),
          Triple(102.0, 98.0, 100.0),
          Triple(101.0, 99.0, 100.0),
        ),
      )
    val widening =
      stockOf(
        listOf(
          Triple(102.0, 98.0, 100.0),
          Triple(101.0, 99.0, 100.0),
          Triple(105.0, 95.0, 100.0),
          Triple(104.0, 96.0, 100.0),
          Triple(110.0, 90.0, 100.0),
          Triple(108.0, 92.0, 100.0),
        ),
      )

    // When evaluating both with the detailed surface
    val pass = condition.evaluateWithDetails(contracting, contracting.quotes.last(), BacktestContext.EMPTY)
    val fail = condition.evaluateWithDetails(widening, widening.quotes.last(), BacktestContext.EMPTY)

    // Then the detailed verdict matches evaluate and carries a human-readable message
    assertTrue(pass.passed)
    assertFalse(fail.passed)
    assertEquals("NarrowingRangeCondition", pass.conditionType)
    assertTrue(pass.message!!.contains("✓"))
    assertTrue(fail.message!!.contains("✗"))
  }

  @Test
  fun `construction rejects a step window below one`() {
    // Given / When / Then a zero step window is rejected at construction (fail-fast on misconfig)
    assertThrows(IllegalArgumentException::class.java) {
      NarrowingRangeCondition(stepWindow = 0)
    }
  }
}
