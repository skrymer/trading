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

/**
 * "Day 1" is the bar before a gap up; "Day 2" is the gap-up bar itself; the failure
 * window is the next three trading bars (Days 3, 4, 5). The pattern is anchored on
 * the gap-up event, not on the trade's entry.
 */
class GapAndCrapExitTest {
  private data class Bar(
    val open: Double,
    val close: Double,
    val low: Double,
  )

  private fun stockOf(
    bars: List<Bar>,
    entryIndex: Int = 0,
    startDate: LocalDate = LocalDate.of(2024, 1, 1),
  ): Pair<Stock, StockQuote> {
    val quotes = bars.mapIndexed { i, b ->
      StockQuote(
        date = startDate.plusDays(i.toLong()),
        openPrice = b.open,
        closePrice = b.close,
        low = b.low,
        high = maxOf(b.open, b.close),
      )
    }
    return Stock(quotes = quotes) to quotes[entryIndex]
  }

  // ===== shouldExit — firing scenarios from the spec =====

  @Test
  fun `scenario 2 - fires on day 1 after gap when close is below gap-up low`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(open = 98.0, close = 100.0, low = 98.0), // Day X — context
        Bar(open = 99.0, close = 100.0, low = 99.0), // Day 1 — predecessor
        Bar(open = 110.0, close = 112.0, low = 105.0), // Day 2 — gap up, low 105
        Bar(open = 110.0, close = 104.0, low = 106.0), // Day 3 — close 104 < 105 → FIRE
      ),
    )

    assertTrue(condition.shouldExit(stock, entry, stock.quotes[3]))
  }

  @Test
  fun `scenario 1 - fires on day 2 after gap when close is below gap-up low`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(98.0, 100.0, 98.0), // Day X
        Bar(99.0, 100.0, 99.0), // Day 1
        Bar(110.0, 112.0, 105.0), // Day 2 — gap up, low 105
        Bar(110.0, 107.0, 106.0), // Day 3 — above (107 > 105)
        Bar(105.0, 104.0, 105.0), // Day 4 — close 104 < 105 → FIRE (g+2)
      ),
    )

    assertTrue(condition.shouldExit(stock, entry, stock.quotes[4]))
  }

  @Test
  fun `scenario 3 - fires on day 3 after gap when close is below gap-up low`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(98.0, 100.0, 98.0), // Day X
        Bar(99.0, 100.0, 99.0), // Day 1
        Bar(110.0, 112.0, 105.0), // Day 2 — gap up, low 105
        Bar(110.0, 107.0, 106.0), // Day 3 — above
        Bar(105.0, 106.0, 105.0), // Day 4 — above
        Bar(105.0, 104.0, 101.0), // Day 5 — close 104 < 105 → FIRE (g+3)
      ),
    )

    assertTrue(condition.shouldExit(stock, entry, stock.quotes[5]))
  }

  // ===== shouldExit — non-firing scenarios =====

  @Test
  fun `does not fire on day 4 after gap - failure window expired`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(110.0, 112.0, 105.0), // gap day
        Bar(110.0, 107.0, 106.0),
        Bar(105.0, 106.0, 105.0),
        Bar(105.0, 105.5, 105.0),
        Bar(105.0, 104.0, 101.0), // g+4 — too late, even though close 104 < 105
      ),
    )

    assertFalse(condition.shouldExit(stock, entry, stock.quotes[5]))
  }

  @Test
  fun `does not fire when gap is below the 5pct threshold`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(102.0, 102.5, 101.0), // 2pct gap — does not qualify
        Bar(101.0, 100.0, 99.0), // close 100 < 101 would fire if gap qualified
      ),
    )

    assertFalse(condition.shouldExit(stock, entry, stock.quotes[2]))
  }

  @Test
  fun `does not fire when no day in the window closes below the gap low`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(110.0, 112.0, 105.0), // gap day
        Bar(112.0, 113.0, 111.0),
        Bar(113.0, 114.0, 112.0),
        Bar(114.0, 115.0, 113.0),
      ),
    )

    assertFalse(condition.shouldExit(stock, entry, stock.quotes[2]))
    assertFalse(condition.shouldExit(stock, entry, stock.quotes[3]))
    assertFalse(condition.shouldExit(stock, entry, stock.quotes[4]))
  }

  @Test
  fun `does not fire when close equals the gap low - strict less than`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(110.0, 112.0, 105.0),
        Bar(110.0, 105.0, 104.0), // close exactly 105 — not strictly below
      ),
    )

    assertFalse(condition.shouldExit(stock, entry, stock.quotes[2]))
  }

  @Test
  fun `does not fire when there is no predecessor bar to compute the gap from`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(110.0, 112.0, 105.0), // would-be gap, but no predecessor
        Bar(110.0, 104.0, 103.0),
      ),
    )

    assertFalse(condition.shouldExit(stock, entry, stock.quotes[1]))
  }

  @Test
  fun `does not fire when entry quote is null`() {
    val condition = GapAndCrapExit()
    val stock = Stock(
      quotes = listOf(
        StockQuote(date = LocalDate.of(2024, 1, 1), openPrice = 99.0, closePrice = 100.0, low = 99.0),
        StockQuote(date = LocalDate.of(2024, 1, 2), openPrice = 110.0, closePrice = 112.0, low = 105.0),
        StockQuote(date = LocalDate.of(2024, 1, 3), openPrice = 110.0, closePrice = 104.0, low = 103.0),
      ),
    )

    assertFalse(condition.shouldExit(stock, null, stock.quotes[2]))
  }

  @Test
  fun `respects a custom gap threshold parameter`() {
    val condition = GapAndCrapExit(gapPercent = 3.0)
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(104.0, 105.0, 103.0), // 4pct gap — above 3pct threshold
        Bar(104.0, 102.0, 101.0), // close 102 < 103 → FIRE
      ),
    )

    assertTrue(condition.shouldExit(stock, entry, stock.quotes[2]))
  }

  @Test
  fun `monitors a fresh qualifying gap independently after the previous one expired`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(110.0, 112.0, 108.0), // first gap, no failure
        Bar(112.0, 113.0, 111.0),
        Bar(113.0, 114.0, 112.0),
        Bar(114.0, 115.0, 113.0), // first window expires
        Bar(125.0, 126.0, 122.0), // fresh gap (115→125 = +8.7pct), low 122
        Bar(122.0, 121.0, 120.0), // close 121 < 122 → FIRE
      ),
    )

    assertTrue(condition.shouldExit(stock, entry, stock.quotes[6]))
  }

  // ===== metadata / strings =====

  @Test
  fun `provides correct exit reason`() {
    assertEquals(
      "Gap and Crap (close below the low of a 5.0% gap-up bar within 3 trading days)",
      GapAndCrapExit().exitReason(),
    )
  }

  @Test
  fun `provides correct description`() {
    assertEquals("Gap and Crap (5.0%)", GapAndCrapExit().description())
  }

  @Test
  fun `provides correct metadata`() {
    val metadata = GapAndCrapExit().getMetadata()
    assertEquals("gapandcrap", metadata.type)
    assertEquals("Gap and Crap", metadata.displayName)
    assertEquals("Signal", metadata.category)
    assertEquals(1, metadata.parameters.size)
    assertEquals("gapPercent", metadata.parameters[0].name)
  }

  // ===== proximity =====

  @Test
  fun `proximity is 1_0 when the condition fires`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(110.0, 112.0, 105.0),
        Bar(110.0, 104.0, 103.0),
      ),
    )

    val proximity = condition.proximity(stock, entry, stock.quotes[2])

    assertNotNull(proximity)
    assertEquals(1.0, proximity!!.proximity, 1e-9)
    assertEquals("gapandcrap", proximity.conditionType)
  }

  @Test
  fun `proximity is null when no qualifying gap is in the lookback window`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(100.0, 101.0, 99.5),
        Bar(101.0, 102.0, 100.0),
        Bar(102.0, 103.0, 101.0),
      ),
    )

    assertNull(condition.proximity(stock, entry, stock.quotes[3]))
  }

  @Test
  fun `proximity is null past the failure window`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(110.0, 112.0, 105.0),
        Bar(110.0, 107.0, 106.0),
        Bar(105.0, 106.0, 105.0),
        Bar(105.0, 105.5, 105.0),
        Bar(105.0, 104.0, 101.0), // g+4 — past window
      ),
    )

    assertNull(condition.proximity(stock, entry, stock.quotes[5]))
  }

  @Test
  fun `proximity is 0_0 when a qualifying gap is in the window but close is above the gap low`() {
    val condition = GapAndCrapExit()
    val (stock, entry) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(110.0, 112.0, 105.0),
        Bar(112.0, 110.0, 108.0), // close 110 > 105
      ),
    )

    val proximity = condition.proximity(stock, entry, stock.quotes[2])

    assertNotNull(proximity)
    assertEquals(0.0, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity is null when entry quote is null`() {
    val condition = GapAndCrapExit()
    val (stock, _) = stockOf(
      listOf(
        Bar(99.0, 100.0, 99.0),
        Bar(110.0, 112.0, 105.0),
        Bar(110.0, 104.0, 103.0),
      ),
    )

    assertNull(condition.proximity(stock, null, stock.quotes[2]))
  }
}
