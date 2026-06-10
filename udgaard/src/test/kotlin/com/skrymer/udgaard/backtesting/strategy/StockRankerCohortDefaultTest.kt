package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StockRankerCohortDefaultTest {
  @Test
  fun `default rankCohort returns exactly the per-stock scores for a non-cross-sectional ranker`() {
    // Given two stocks a per-stock ranker scores differently (ATR-as-percent-of-price volatility)
    val today = LocalDate.of(2024, 1, 5)
    val q1 = StockQuote(date = today, closePrice = 100.0, atr = 2.0)
    val q2 = StockQuote(date = today, closePrice = 50.0, atr = 2.0)
    val s1 = Stock(symbol = "A", quotes = listOf(q1))
    val s2 = Stock(symbol = "B", quotes = listOf(q2))
    val ranker = VolatilityRanker()

    // When ranked via the cohort hook (which VolatilityRanker does not override)
    val cohortScores = ranker.rankCohort(listOf(s1 to q1, s2 to q2), BacktestContext.EMPTY)

    // Then the default delegates to per-stock score exactly — byte-identical, and still per-stock-distinct
    assertEquals(ranker.score(s1, q1, BacktestContext.EMPTY), cohortScores[0], 0.0)
    assertEquals(ranker.score(s2, q2, BacktestContext.EMPTY), cohortScores[1], 0.0)
    assertTrue(cohortScores[0] != cohortScores[1])
  }
}
