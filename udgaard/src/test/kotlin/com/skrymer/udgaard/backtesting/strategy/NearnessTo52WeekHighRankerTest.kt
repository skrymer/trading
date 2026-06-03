package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NearnessTo52WeekHighRankerTest {
  private val anyStock = Stock(symbol = "T", quotes = emptyList())

  private fun quoteWith(
    close: Double,
    high52Week: Double?,
  ) = StockQuote(symbol = "T", date = LocalDate.of(2024, 1, 2), closePrice = close, high52Week = high52Week)

  @Test
  fun `score is the ratio of close to the 52-week high`() {
    // Given a quote sitting at half its 52-week high
    val quote = quoteWith(close = 50.0, high52Week = 100.0)

    // When scored
    val score = NearnessTo52WeekHighRanker().score(anyStock, quote)

    // Then the score is the nearness ratio 50 / 100 = 0.5
    assertEquals(0.5, score, 1e-9)
  }

  @Test
  fun `a quote sitting exactly at its 52-week high scores the maximum nearness of one`() {
    // Given a quote whose close equals its 52-week high
    val quote = quoteWith(close = 100.0, high52Week = 100.0)

    // When scored
    val score = NearnessTo52WeekHighRanker().score(anyStock, quote)

    // Then it is maximally near the high
    assertEquals(1.0, score, 1e-9)
  }

  @Test
  fun `a quote above its 52-week high is capped at the maximum nearness of one`() {
    // Given a quote that has printed above its prior 52-week high (ratio would be 1.08)
    val quote = quoteWith(close = 108.0, high52Week = 100.0)

    // When scored
    val score = NearnessTo52WeekHighRanker().score(anyStock, quote)

    // Then it collapses to 1.0 — an overshoot is no more "near the high" than sitting at it,
    // so it must not rank above a quote exactly at its high (this keeps it a nearness ranker,
    // not a breakout/overshoot ranker)
    assertEquals(1.0, score, 1e-9)
  }

  @Test
  fun `a quote nearer its high ranks above one further from its high`() {
    // Given one quote at 90% of its high and another at 50%
    val nearer = quoteWith(close = 90.0, high52Week = 100.0)
    val further = quoteWith(close = 50.0, high52Week = 100.0)
    val ranker = NearnessTo52WeekHighRanker()

    // When both are scored
    // Then the nearer quote ranks higher (higher = better)
    assertTrue(
      ranker.score(anyStock, nearer) > ranker.score(anyStock, further),
      "nearer-to-high should outrank further-from-high",
    )
  }

  @Test
  fun `a quote with no 52-week high yet ranks below any real nearness reading`() {
    // Given a quote whose 52-week high is undefined (fewer than 252 bars of history)
    val quote = quoteWith(close = 50.0, high52Week = null)

    // When scored
    val score = NearnessTo52WeekHighRanker().score(anyStock, quote)

    // Then it falls below any real nearness reading (which is strictly positive), so it sorts last
    assertTrue(score < 0.0, "no-52-week-high score $score should sort last")
  }

  @Test
  fun `a non-positive 52-week high ranks below any real nearness reading`() {
    // Given a pathological zero 52-week high (would divide to infinity)
    val quote = quoteWith(close = 50.0, high52Week = 0.0)

    // When scored
    val score = NearnessTo52WeekHighRanker().score(anyStock, quote)

    // Then the unscoreable reading collapses to the sentinel, sorting last (never to a top score)
    assertTrue(score < 0.0, "zero-52-week-high score $score should sort last")
  }

  @Test
  fun `a non-positive close ranks below any real nearness reading`() {
    // Given a pathological zero close
    val quote = quoteWith(close = 0.0, high52Week = 100.0)

    // When scored
    val score = NearnessTo52WeekHighRanker().score(anyStock, quote)

    // Then it collapses to the sentinel, sorting last (a 0.0 ratio would otherwise masquerade as a
    // legitimately-far reading)
    assertTrue(score < 0.0, "zero-close score $score should sort last")
  }

  @Test
  fun `RankerFactory resolves the nearness-to-52-week-high ranker by type`() {
    // Given the registered ranker type
    // When created from the factory
    val ranker = RankerFactory.create("nearness52WeekHigh")

    // Then it resolves to the nearness ranker
    assertNotNull(ranker, "factory should resolve nearness52WeekHigh")
    assertTrue(
      ranker is NearnessTo52WeekHighRanker,
      "expected NearnessTo52WeekHighRanker, got ${ranker?.javaClass?.simpleName}",
    )
  }
}
