package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.sqrt

class MarketResidualMomentumRankerTest {
  // A small window so the regression math can be verified by hand: estimationDays = 5 yields
  // 4 daily returns (at bar indices entry-4..entry-1); momentumDays = 2 + skipDays = 1 accumulates
  // the last 2 of those returns (indices entry-2, entry-1).
  private val estimationDays = 5
  private val momentumDays = 2
  private val skipDays = 1

  // SPY returns over the 4 estimation bars: mean 0, variance > 0.
  private val spyReturns = listOf(0.10, -0.10, 0.10, -0.10)

  // Idiosyncratic returns: orthogonal to SPY returns in-sample and mean 0, so OLS recovers beta
  // exactly and residuals == idio. The accumulation window (last 2) is +0.10, +0.10.
  private val idioReturns = listOf(-0.10, -0.10, 0.10, 0.10)

  private fun date(day: Int) = LocalDate.of(2024, 1, day)

  /** Build a quote series from a starting price and a list of daily simple returns. */
  private fun closesFromReturns(start: Double, returns: List<Double>): List<Double> {
    val closes = mutableListOf(start)
    returns.forEach { r -> closes.add(closes.last() * (1.0 + r)) }
    return closes
  }

  private fun stock(symbol: String, closes: List<Double>) =
    Stock(
      symbol = symbol,
      quotes = closes.mapIndexed { i, c -> StockQuote(symbol = symbol, date = date(i + 1), closePrice = c) },
    )

  private fun spyContext(closes: List<Double>): BacktestContext {
    val spy = closes.mapIndexed { i, c -> date(i + 1) to StockQuote(symbol = "SPY", date = date(i + 1), closePrice = c) }
    return BacktestContext.EMPTY.copy(spyQuoteMap = spy.toMap())
  }

  @Test
  fun `idiosyncratic outperformance in the accumulation window scores positive residual momentum`() {
    // Given a stock whose returns are market beta (2x SPY) plus a mean-zero idiosyncratic component
    // that is orthogonal to SPY in-sample, so the regression residuals equal that idiosyncratic
    // component. Its accumulation-window residuals sum to +0.20; the residual stdev is 0.20/sqrt(3).
    val stockReturns = spyReturns.zip(idioReturns) { m, i -> 2.0 * m + i }
    val stock = stock("A", closesFromReturns(100.0, stockReturns + 0.0)) // +1 entry bar (unused return)
    val context = spyContext(closesFromReturns(100.0, spyReturns + 0.0))
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)

    // When scored at the final (entry) bar
    val score = ranker.score(stock, stock.quotes.last(), context)

    // Then score = sum(accumulation residuals) / stdev(estimation residuals) = 0.20 / (0.20/sqrt(3)) = sqrt(3)
    assertEquals(sqrt(3.0), score, 1e-9)
    assertTrue(score > 0.0)
  }

  @Test
  fun `market beta is stripped — two stocks with the same idiosyncratic returns score equally regardless of beta`() {
    // Given two stocks sharing the same mean-zero, SPY-orthogonal idiosyncratic returns but loading
    // on the market very differently (beta 2.0 vs beta 0.5)
    val context = spyContext(closesFromReturns(100.0, spyReturns + 0.0))
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val highBeta = stock("HIGH", closesFromReturns(100.0, spyReturns.zip(idioReturns) { m, i -> 2.0 * m + i } + 0.0))
    val lowBeta = stock("LOW", closesFromReturns(100.0, spyReturns.zip(idioReturns) { m, i -> 0.5 * m + i } + 0.0))

    // When both are scored
    val highScore = ranker.score(highBeta, highBeta.quotes.last(), context)
    val lowScore = ranker.score(lowBeta, lowBeta.quotes.last(), context)

    // Then the market loading is regressed away — only the idiosyncratic residual drives the score
    assertEquals(highScore, lowScore, 1e-9)
    assertTrue(lowScore > 0.0, "score should be a real residual-momentum value, not the unscoreable sentinel")
  }

  @Test
  fun `score is invariant to idiosyncratic volatility scale — it measures residual momentum, not magnitude`() {
    // Given two stocks with the same residual *shape* but one with 3x the idiosyncratic volatility
    val context = spyContext(closesFromReturns(100.0, spyReturns + 0.0))
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val calm = stock("CALM", closesFromReturns(100.0, spyReturns.zip(idioReturns) { m, i -> m + i } + 0.0))
    val volatile = stock("VOL", closesFromReturns(100.0, spyReturns.zip(idioReturns) { m, i -> m + 3.0 * i } + 0.0))

    // When both are scored
    val calmScore = ranker.score(calm, calm.quotes.last(), context)
    val volatileScore = ranker.score(volatile, volatile.quotes.last(), context)

    // Then standardizing by residual stdev cancels the 3x scale — the score is momentum-per-unit-vol,
    // not raw residual size, so both land on the same value
    assertEquals(calmScore, volatileScore, 1e-9)
    assertTrue(calmScore > 0.0)
  }

  @Test
  fun `the entry bar and later bars are never read`() {
    // Given a base stock and a tampered copy that differs only at the entry bar (a wild 99999 close)
    // and in extra bars appended *after* the entry date
    val context = spyContext(closesFromReturns(100.0, spyReturns + 0.0))
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val baseCloses = closesFromReturns(100.0, spyReturns.zip(idioReturns) { m, i -> 2.0 * m + i } + 0.0)
    val base = stock("BASE", baseCloses)
    // Same pre-entry bars; entry bar (index 5) tampered to 99999, plus wild future bars after entry.
    val tampered = stock("TAMPERED", baseCloses.dropLast(1) + listOf(99999.0, 0.001, 88888.0))

    // When scored at the same entry date (the bar at date 6 = index 5)
    val entryDate = date(6)
    val baseScore = ranker.score(base, base.quotes.first { it.date == entryDate }, context)
    val tamperedScore = ranker.score(tampered, tampered.quotes.first { it.date == entryDate }, context)

    // Then the entry-and-future tampering is invisible — only strictly-earlier bars feed the score
    assertEquals(baseScore, tamperedScore, 1e-9)
    assertTrue(baseScore > 0.0)
  }

  @Test
  fun `a stock without enough history to fill the estimation window is unscoreable`() {
    // Given only 4 bars but a 5-day estimation window (estimation start index would be negative)
    val context = spyContext(closesFromReturns(100.0, spyReturns + 0.0))
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val tooShort = stock("SHORT", listOf(100.0, 110.0, 99.0, 108.9))

    // When scored
    val score = ranker.score(tooShort, tooShort.quotes.last(), context)

    // Then it collapses to the unscoreable sentinel and sorts last
    assertTrue(score < -1e300, "insufficient-history score $score should be the unscoreable sentinel")
  }

  @Test
  fun `without a SPY proxy the market-residual ranker cannot score`() {
    // Given a fully-scoreable stock but no SPY data (the 2-arg overload, and an EMPTY context)
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val stock = stock("A", closesFromReturns(100.0, spyReturns.zip(idioReturns) { m, i -> 2.0 * m + i } + 0.0))

    // When scored without the market proxy
    val noContextScore = ranker.score(stock, stock.quotes.last())
    val emptyContextScore = ranker.score(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then a market-residual score is meaningless without the market — both are unscoreable
    assertTrue(noContextScore < -1e300, "2-arg score $noContextScore should be the unscoreable sentinel")
    assertTrue(emptyContextScore < -1e300, "empty-context score $emptyContextScore should be the unscoreable sentinel")
  }

  @Test
  fun `a flat SPY series leaves beta undefined and is unscoreable`() {
    // Given a SPY proxy with zero return variance (constant price) — beta = cov / var is undefined
    val flatSpy = spyContext(List(6) { 100.0 })
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val stock = stock("A", closesFromReturns(100.0, spyReturns.zip(idioReturns) { m, i -> 2.0 * m + i } + 0.0))

    // When scored against the degenerate market series
    val score = ranker.score(stock, stock.quotes.last(), flatSpy)

    // Then the undefined regression collapses to the unscoreable sentinel
    assertTrue(score < -1e300, "zero-variance-SPY score $score should be the unscoreable sentinel")
  }

  @Test
  fun `a pure market replica has no idiosyncratic signal and is unscoreable`() {
    // Given a stock whose returns are exactly 2x SPY with zero idiosyncratic component — every
    // regression residual is zero, so the residual stdev is zero
    val context = spyContext(closesFromReturns(100.0, spyReturns + 0.0))
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val replica = stock("REPLICA", closesFromReturns(100.0, spyReturns.map { 2.0 * it } + 0.0))

    // When scored
    val score = ranker.score(replica, replica.quotes.last(), context)

    // Then there is no idiosyncratic momentum to standardize — it is unscoreable, not a divide-by-zero
    assertTrue(score < -1e300, "zero-residual score $score should be the unscoreable sentinel")
  }

  @Test
  fun `too few SPY-paired returns leave beta untrustworthy and the stock unscoreable`() {
    // Given a SPY proxy missing a mid-window date — dropping date 3 voids both returns that touch it,
    // leaving fewer than the required 80% of estimation returns paired
    val fullSpy = closesFromReturns(100.0, spyReturns + 0.0)
    val sparseSpy = fullSpy
      .mapIndexed { i, c -> date(i + 1) to StockQuote(symbol = "SPY", date = date(i + 1), closePrice = c) }
      .filterNot { it.first == date(3) }
      .toMap()
    val context = BacktestContext.EMPTY.copy(spyQuoteMap = sparseSpy)
    val ranker = MarketResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val stock = stock("A", closesFromReturns(100.0, spyReturns.zip(idioReturns) { m, i -> 2.0 * m + i } + 0.0))

    // When scored against the sparse market series
    val score = ranker.score(stock, stock.quotes.last(), context)

    // Then the by-date pairing drops too many returns to trust the regression — unscoreable
    assertTrue(score < -1e300, "sparse-SPY score $score should be the unscoreable sentinel")
  }

  @Test
  fun `skipDays must be at least one so the accumulation window ends before the entry bar`() {
    // Given skipDays = 0 (accumulation end would coincide with the entry bar)
    // When constructed / Then construction is rejected (no entry-or-future bar may be read)
    assertThrows(IllegalArgumentException::class.java) {
      MarketResidualMomentumRanker(estimationDays = 504, momentumDays = 252, skipDays = 0)
    }
  }

  @Test
  fun `momentumDays must exceed skipDays so the accumulation window is non-empty`() {
    // Given a momentum window no longer than the skip
    // When constructed / Then construction is rejected
    assertThrows(IllegalArgumentException::class.java) {
      MarketResidualMomentumRanker(estimationDays = 504, momentumDays = 21, skipDays = 21)
    }
  }

  @Test
  fun `estimationDays must contain the accumulation window`() {
    // Given an estimation window shorter than momentumDays + skipDays (accumulation would spill out)
    // When constructed / Then construction is rejected
    assertThrows(IllegalArgumentException::class.java) {
      MarketResidualMomentumRanker(estimationDays = 200, momentumDays = 252, skipDays = 21)
    }
  }

  @Test
  fun `a fully SPY-gapped accumulation window is unscoreable, not a neutral zero`() {
    // Given an 11-day estimation window (10 returns); the accumulation window is the last 2 returns,
    // both of which touch date 10. Dropping SPY on date 10 voids exactly those two accumulation
    // returns while leaving 8 of 10 estimation returns paired — above the 80% minimum.
    val longEstimation = 11
    val ranker = MarketResidualMomentumRanker(estimationDays = longEstimation, momentumDays = 2, skipDays = 1)
    // 11 daily returns (12 closes) with genuine variance so the stock is otherwise scoreable.
    val spyR = listOf(0.02, -0.01, 0.03, -0.02, 0.01, -0.03, 0.02, -0.01, 0.025, -0.015, 0.0)
    val idioR = listOf(0.01, -0.01, 0.01, -0.01, 0.01, -0.01, 0.01, -0.01, 0.01, -0.01, 0.0)
    val stock = stock("A", closesFromReturns(100.0, spyR.zip(idioR) { m, i -> 1.5 * m + i }))
    val spyCloses = closesFromReturns(100.0, spyR)
    val fullSpy = spyContext(spyCloses)
    val gappedSpy = BacktestContext.EMPTY.copy(
      spyQuoteMap = spyCloses
        .mapIndexed { i, c -> date(i + 1) to StockQuote(symbol = "SPY", date = date(i + 1), closePrice = c) }
        .filterNot { it.first == date(10) }
        .toMap(),
    )

    // When scored with full SPY vs SPY missing the entire accumulation window
    val fullScore = ranker.score(stock, stock.quotes.last(), fullSpy)
    val gappedScore = ranker.score(stock, stock.quotes.last(), gappedSpy)

    // Then the stock is normally scoreable, but with no accumulation data it is unscoreable rather
    // than a competitive 0.0 that could win a fill on a missing reading
    assertTrue(fullScore > -1e300 && fullScore.isFinite(), "full-SPY score $fullScore should be a real value")
    assertTrue(gappedScore < -1e300, "empty-accumulation score $gappedScore should be the unscoreable sentinel")
  }
}
