package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TrailingReturnRankerTest {
  private fun quote(day: Int, close: Double) =
    StockQuote(symbol = "T", date = LocalDate.of(2024, 1, day), closePrice = close)

  private fun stockWith(vararg closes: Pair<Int, Double>) =
    Stock(symbol = "T", quotes = closes.map { quote(it.first, it.second) })

  @Test
  fun `score is the trailing return over the lookback window ending skipDays before entry`() {
    // Given closes on days 1-4, scored as a 3-1 trailing return:
    // start = entry(idx3) - 3 = day1 close 100, end = entry - 1 = day3 close 120
    val stock = stockWith(1 to 100.0, 2 to 110.0, 3 to 120.0, 4 to 130.0)
    val entry = stock.quotes.last()

    // When scored
    val score = TrailingReturnRanker(lookbackDays = 3, skipDays = 1).score(stock, entry)

    // Then score = close[end]/close[start] - 1 = 120/100 - 1 = 0.20
    assertEquals(0.20, score, 1e-9)
  }

  @Test
  fun `a stock without enough history ranks below any real return`() {
    // Given only 2 bars but a 3-day lookback (start index would be negative)
    val stock = stockWith(1 to 100.0, 2 to 110.0)
    val entry = stock.quotes.last()

    // When scored
    val score = TrailingReturnRanker(lookbackDays = 3, skipDays = 1).score(stock, entry)

    // Then it falls below the -1.0 floor of any real return, so it sorts last
    assertTrue(score < -1.0, "insufficient-history score $score should be < -1.0")
  }

  @Test
  fun `a stronger trailing return scores higher than a weaker one`() {
    // Given one stock rising (+20%) and one falling (-20%) over the same 3-1 window
    val rising = stockWith(1 to 100.0, 2 to 110.0, 3 to 120.0, 4 to 130.0)
    val falling = stockWith(1 to 100.0, 2 to 90.0, 3 to 80.0, 4 to 70.0)
    val ranker = TrailingReturnRanker(lookbackDays = 3, skipDays = 1)

    // When both are scored
    val risingScore = ranker.score(rising, rising.quotes.last())
    val fallingScore = ranker.score(falling, falling.quotes.last())

    // Then the riser ranks higher (higher = better)
    assertTrue(risingScore > fallingScore, "rising $risingScore should beat falling $fallingScore")
  }

  @Test
  fun `the skipped recent bars do not affect the score`() {
    // Given two stocks identical except the most recent (skipped) bar — one spikes to 999
    val calm = stockWith(1 to 100.0, 2 to 110.0, 3 to 120.0, 4 to 130.0)
    val spiked = stockWith(1 to 100.0, 2 to 110.0, 3 to 120.0, 4 to 999.0)
    val ranker = TrailingReturnRanker(lookbackDays = 3, skipDays = 1)

    // When scored (the day-4 bar is excluded by skipDays = 1)
    // Then the spike in the skipped bar is invisible to the score
    assertEquals(
      ranker.score(calm, calm.quotes.last()),
      ranker.score(spiked, spiked.quotes.last()),
      1e-9,
    )
  }

  @Test
  fun `lookbackDays must exceed skipDays`() {
    // Given a lookback that does not extend past the skipped window
    // When constructed
    // Then construction is rejected
    assertThrows(IllegalArgumentException::class.java) {
      TrailingReturnRanker(lookbackDays = 5, skipDays = 5)
    }
  }

  @Test
  fun `skipDays must be at least one so the window ends before the entry bar`() {
    // Given skipDays = 0 (window end would coincide with the entry bar)
    // When constructed
    // Then construction is rejected (no entry-or-future bar may be read)
    assertThrows(IllegalArgumentException::class.java) {
      TrailingReturnRanker(lookbackDays = 10, skipDays = 0)
    }
  }

  @Test
  fun `a non-positive base price ranks below any real return`() {
    // Given the lookback-start bar has a zero close (bad/missing base price)
    val stock = stockWith(1 to 0.0, 2 to 110.0, 3 to 120.0, 4 to 130.0)
    val entry = stock.quotes.last()

    // When scored as a 3-1 trailing return (start bar = day1 close 0.0)
    val score = TrailingReturnRanker(lookbackDays = 3, skipDays = 1).score(stock, entry)

    // Then the unscoreable base collapses to the sentinel, sorting last
    assertTrue(score < -1.0, "zero-base score $score should be < -1.0")
  }

  @Test
  fun `declares its lookback window as the trailing warmup history the engine must load`() {
    // Given the production-default 252-21 momentum ranker, whose window reaches 252 bars back
    val ranker = TrailingReturnRanker(lookbackDays = 252, skipDays = 21)

    // When the engine asks how much pre-window history to load so this ranker is scoreable
    // Then it is the lookback window — without it early in-window entries are unscoreable
    assertEquals(252, ranker.warmupTradingDays())
  }
}
