package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Fundamental
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FundamentalQualityRankerTest {
  private val entryDate = LocalDate.of(2024, 6, 1)

  // For a leg with exactly two defined cohort values a>b, the sample-stdev z-scores are ±1/√2,
  // independent of the magnitudes — the fixed reference the blended-score assertions lean on.
  private val zTwo = 1.0 / Math.sqrt(2.0)

  /**
   * Builds a stock with eight visible quarterly filings such that, as of [entryDate]:
   *  - grossProfit_TTM / totalAssets = [level]
   *  - operatingMargin_TTM (latest four) = [trendCurrent], prior-year (fifth-to-eighth) = [trendPrior]
   * so the margin-trend leg is `trendCurrent - trendPrior`.
   */
  private fun qualityStock(
    symbol: String,
    level: Double,
    trendCurrent: Double,
    trendPrior: Double,
  ): Stock {
    val fiscals = listOf("2024-03-31", "2023-12-31", "2023-09-30", "2023-06-30", "2023-03-31", "2022-12-31", "2022-09-30", "2022-06-30")
    val filings = listOf("2024-05-01", "2024-02-01", "2023-11-01", "2023-08-01", "2023-05-01", "2023-02-01", "2022-11-01", "2022-08-01")
    val funds = (0 until 8).map { i ->
      val current = i < 4
      Fundamental(
        symbol = symbol,
        fiscalDateEnding = LocalDate.parse(fiscals[i]),
        filingDate = LocalDate.parse(filings[i]),
        grossProfit = if (current) level * 250.0 else 0.0, // current four sum to level * 1000
        totalAssets = 1000.0,
        operatingIncome = (if (current) trendCurrent else trendPrior) * 100.0,
        totalRevenue = 100.0, // each TTM revenue sums to 400 → opMargin = trend
      )
    }
    return Stock(symbol = symbol, fundamentals = funds, quotes = listOf(StockQuote(date = entryDate, closePrice = 100.0)))
  }

  private fun cohortOf(vararg stocks: Stock) = stocks.map { it to it.quotes.last() }

  @Test
  fun `blends the two standardized legs with equal weight`() {
    // Given two fully-defined candidates, A stronger than B on both legs
    val stockA = qualityStock("A", level = 0.20, trendCurrent = 0.10, trendPrior = 0.05)
    val stockB = qualityStock("B", level = 0.10, trendCurrent = 0.05, trendPrior = 0.05)

    // When ranked as a cohort
    val scores = FundamentalQualityRanker().rankCohort(cohortOf(stockA, stockB), BacktestContext.EMPTY)

    // Then each leg's z is ±1/√2 and the 0.5/0.5 blend carries them through
    assertEquals(zTwo, scores[0], 1e-6)
    assertEquals(-zTwo, scores[1], 1e-6)
  }

  @Test
  fun `leg weights select which leg dominates`() {
    // Given A leads on level but trails on margin trend, ranked with level-only weights
    val stockA = qualityStock("A", level = 0.20, trendCurrent = 0.05, trendPrior = 0.10) // marginTrend -0.05
    val stockB = qualityStock("B", level = 0.10, trendCurrent = 0.15, trendPrior = 0.05) // marginTrend +0.10

    // When the trend leg is weighted to zero
    val scores = FundamentalQualityRanker(levelWeight = 1.0, trendWeight = 0.0).rankCohort(cohortOf(stockA, stockB), BacktestContext.EMPTY)

    // Then A still wins — the level leg alone decides, the (higher) trend of B is ignored
    assertEquals(zTwo, scores[0], 1e-6)
    assertTrue(scores[0] > scores[1])
  }

  @Test
  fun `a candidate missing fundamentals scores neutral, not last`() {
    // Given two ranked candidates and one with no fundamentals at all
    val stockA = qualityStock("A", level = 0.20, trendCurrent = 0.10, trendPrior = 0.05)
    val stockB = qualityStock("B", level = 0.10, trendCurrent = 0.05, trendPrior = 0.05)
    val missing = Stock(symbol = "C", quotes = listOf(StockQuote(date = entryDate, closePrice = 100.0)))

    // When ranked together
    val scores = FundamentalQualityRanker().rankCohort(cohortOf(stockA, stockB, missing), BacktestContext.EMPTY)

    // Then the missing name is neutral (0.0) — between A and B, never sunk below the loser
    assertEquals(0.0, scores[2], 1e-9)
    assertTrue(scores[0] > scores[2])
    assertTrue(scores[2] > scores[1])
  }

  @Test
  fun `a single-candidate cohort is neutral`() {
    // Given only one candidate fires that day — no cross-section to standardize against
    val stockA = qualityStock("A", level = 0.20, trendCurrent = 0.10, trendPrior = 0.05)

    // When ranked alone
    val scores = FundamentalQualityRanker().rankCohort(cohortOf(stockA), BacktestContext.EMPTY)

    // Then the degenerate cohort yields a neutral score
    assertEquals(0.0, scores[0], 1e-9)
  }

  @Test
  fun `uses the year-over-year operating-margin change for the trend leg`() {
    // Given two candidates with identical quality level but A's margin rising faster year-over-year
    val stockA = qualityStock("A", level = 0.15, trendCurrent = 0.20, trendPrior = 0.05) // trend +0.15
    val stockB = qualityStock("B", level = 0.15, trendCurrent = 0.06, trendPrior = 0.05) // trend +0.01

    // When ranked on the trend leg only
    val scores = FundamentalQualityRanker(levelWeight = 0.0, trendWeight = 1.0).rankCohort(cohortOf(stockA, stockB), BacktestContext.EMPTY)

    // Then the faster-improving margin ranks higher (and the flat level leg, being degenerate, is neutral)
    assertEquals(zTwo, scores[0], 1e-6)
    assertTrue(scores[0] > scores[1])
  }

  @Test
  fun `reads point-in-time fundamentals so needs no warmup`() {
    assertEquals(0, FundamentalQualityRanker().warmupTradingDays())
  }
}
