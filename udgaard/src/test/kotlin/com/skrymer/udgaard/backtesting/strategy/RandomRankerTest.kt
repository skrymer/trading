package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RandomRankerTest {
  private fun quote(symbol: String) =
    StockQuote(symbol = symbol, date = LocalDate.of(2020, 6, 1), closePrice = 100.0)

  private fun stock(symbol: String) = Stock(symbol = symbol, quotes = listOf(quote(symbol)))

  @Test
  fun `a seeded random ranker is reproducible — same seed gives the same score for the same name and date`() {
    // Given two independent ranker instances built with the same seed
    val rankerA = RandomRanker(seed = 42L)
    val rankerB = RandomRanker(seed = 42L)
    val stock = stock("AAPL")
    val entry = stock.quotes.first()

    // When the same (stock, date) is scored by each
    // Then the scores are identical — the baseline can be a byte-identical, reproducible comparison
    assertEquals(rankerA.score(stock, entry), rankerB.score(stock, entry), 0.0)
  }

  @Test
  fun `different seeds produce different orderings`() {
    // Given the same name and date scored under several different seeds
    val stock = stock("AAPL")
    val entry = stock.quotes.first()
    val scores = (1L..30L).map { RandomRanker(seed = it).score(stock, entry) }

    // When the distinct values are counted
    // Then the seeds genuinely vary the score (a multi-seed sweep yields a real distribution, not one number)
    assertTrue(scores.toSet().size > 25, "expected ~30 distinct scores across 30 seeds, got ${scores.toSet().size}")
  }

  @Test
  fun `within one seed different names get different scores`() {
    // Given two different symbols scored under the same seed
    val ranker = RandomRanker(seed = 7L)
    val appl = stock("AAPL")
    val msft = stock("MSFT")

    // When each is scored
    // Then the ranker actually orders names (it is not a constant) so it can break ties between candidates
    assertNotEquals(
      ranker.score(appl, appl.quotes.first()),
      ranker.score(msft, msft.quotes.first()),
    )
  }

  @Test
  fun `scores fall in the zero to one hundred range`() {
    // Given a seeded and an unseeded ranker
    val seeded = RandomRanker(seed = 99L)
    val unseeded = RandomRanker()
    val stock = stock("AAPL")
    val entry = stock.quotes.first()

    // When scored
    val seededScore = seeded.score(stock, entry)
    val unseededScore = unseeded.score(stock, entry)

    // Then both land in [0, 100), consistent with the other rankers' score scale
    assertTrue(seededScore in 0.0..<100.0, "seeded score $seededScore out of range")
    assertTrue(unseededScore in 0.0..<100.0, "unseeded score $unseededScore out of range")
  }

  @Test
  fun `the factory threads the random seed so a built Random ranker is reproducible`() {
    // Given two Random rankers built through the factory with the same seed
    val rankerA = RankerFactory.create("Random", null, 42L)
    val rankerB = RankerFactory.create("Random", null, 42L)
    val stock = stock("AAPL")
    val entry = stock.quotes.first()

    // When the same name is scored by each
    // Then the factory-built baseline is reproducible — the request seed actually reaches the ranker
    assertEquals(rankerA!!.score(stock, entry), rankerB!!.score(stock, entry), 0.0)
  }

  @Test
  fun `the description records the seed so a persisted report attests which baseline was used`() {
    // Given a seeded and an unseeded Random ranker
    // When their descriptions are read
    // Then the seeded one names its seed (reproducible provenance) and the unseeded one is marked as such
    assertTrue(RandomRanker(seed = 42L).description().contains("42"), "seeded description should name the seed")
    assertTrue(
      RandomRanker().description().contains("unseeded", ignoreCase = true),
      "unseeded description should flag non-reproducibility",
    )
  }

  @Test
  fun `a name's score is independent of scoring order on the same instance`() {
    // Given one seeded ranker instance scoring two names
    val ranker = RandomRanker(seed = 5L)
    val appl = stock("AAPL")
    val msft = stock("MSFT")

    // When the same instance scores them in one order, then the reverse order
    val applFirst = ranker.score(appl, appl.quotes.first())
    val msftSecond = ranker.score(msft, msft.quotes.first())
    val msftFirst = ranker.score(msft, msft.quotes.first())
    val applSecond = ranker.score(appl, appl.quotes.first())

    // Then each name's score is unchanged by call order — there is no shared mutable RNG state (the
    // exact defect of the old global-RNG ranker)
    assertEquals(applFirst, applSecond, 0.0)
    assertEquals(msftSecond, msftFirst, 0.0)
  }

  @Test
  fun `the same name scores differently on different dates`() {
    // Given one seeded ranker and the same symbol quoted on two different dates
    val ranker = RandomRanker(seed = 11L)
    val symbol = "AAPL"
    val day1 = StockQuote(symbol = symbol, date = LocalDate.of(2020, 6, 1), closePrice = 100.0)
    val day2 = StockQuote(symbol = symbol, date = LocalDate.of(2020, 6, 2), closePrice = 100.0)
    val stock = Stock(symbol = symbol, quotes = listOf(day1, day2))

    // When each bar is scored
    // Then the date participates in the ordering — the same name does not get a single fixed rank
    assertNotEquals(ranker.score(stock, day1), ranker.score(stock, day2))
  }
}
