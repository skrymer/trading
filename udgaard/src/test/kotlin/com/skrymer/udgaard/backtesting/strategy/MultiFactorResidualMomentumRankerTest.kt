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

class MultiFactorResidualMomentumRankerTest {
  // estimationDays = 7 yields 6 daily returns (bars 1..6); momentumDays = 2 + skipDays = 1 accumulates
  // the last 2 (bars 5,6). Six estimation points give a 3-dim residual space (6 obs − intercept − 2
  // factors), enough to place an idiosyncratic series orthogonal to BOTH factors with a non-zero
  // accumulation sum — a 2-factor setup is degenerate at the single-factor test's 4 points.
  private val estimationDays = 7
  private val momentumDays = 2
  private val skipDays = 1

  // Three mutually orthogonal, mean-zero return series over the 6 estimation bars: market, sector,
  // idiosyncratic. Orthogonality makes the 2-factor OLS recover the residual == idio exactly.
  private val spyReturns = listOf(0.10, -0.10, 0.10, -0.10, 0.0, 0.0)
  private val sectorReturns = listOf(0.10, 0.10, -0.10, -0.10, 0.0, 0.0)
  private val idioReturns = listOf(-0.10, -0.10, -0.10, -0.10, 0.20, 0.20)

  private val sector = "XLK"

  private fun date(day: Int) = LocalDate.of(2024, 1, day)

  /** Build a quote series from a starting price and a list of daily simple returns. */
  private fun closesFromReturns(start: Double, returns: List<Double>): List<Double> {
    val closes = mutableListOf(start)
    returns.forEach { r -> closes.add(closes.last() * (1.0 + r)) }
    return closes
  }

  /**
   * Closes for a stock whose returns load on market + sector by the given betas plus an idiosyncratic
   * scale, over the 6 estimation bars (final 0.0 keeps the entry bar return-neutral).
   */
  private fun factorLoadedCloses(
    marketBeta: Double,
    sectorBeta: Double,
    idioScale: Double = 1.0,
  ): List<Double> {
    val returns =
      (0 until 6).map { marketBeta * spyReturns[it] + sectorBeta * sectorReturns[it] + idioScale * idioReturns[it] }
    return closesFromReturns(100.0, returns + 0.0)
  }

  private fun stock(symbol: String, closes: List<Double>, sectorSymbol: String? = sector) =
    Stock(
      symbol = symbol,
      sectorSymbol = sectorSymbol,
      quotes = closes.mapIndexed { i, c -> StockQuote(symbol = symbol, date = date(i + 1), closePrice = c) },
    )

  private fun quoteSeries(symbol: String, closes: List<Double>) =
    closes
      .mapIndexed { i, c -> date(i + 1) to StockQuote(symbol = symbol, date = date(i + 1), closePrice = c) }
      .toMap()

  /** Context carrying the market (SPY) series and one sector-ETF series, both over the test dates. */
  private fun context(
    spyCloses: List<Double>,
    sectorCloses: List<Double>,
    sectorSymbol: String = sector,
  ) = BacktestContext.EMPTY.copy(
    spyQuoteMap = quoteSeries("SPY", spyCloses),
    sectorEtfQuoteMap = mapOf(sectorSymbol to quoteSeries(sectorSymbol, sectorCloses)),
  )

  @Test
  fun `idiosyncratic outperformance after stripping market AND sector scores positive residual momentum`() {
    // Given a stock whose returns load on both the market (beta 2.0) and its sector (beta 1.5) plus a
    // mean-zero idiosyncratic component orthogonal to both factors in-sample, so the 2-factor OLS
    // residuals equal that idiosyncratic component. Its accumulation-window residuals sum to +0.40.
    val stockReturns = (0 until 6).map { 2.0 * spyReturns[it] + 1.5 * sectorReturns[it] + idioReturns[it] }
    val stock = stock("A", closesFromReturns(100.0, stockReturns + 0.0))
    val context = context(closesFromReturns(100.0, spyReturns + 0.0), closesFromReturns(100.0, sectorReturns + 0.0))
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)

    // When scored at the final (entry) bar
    val score = ranker.score(stock, stock.quotes.last(), context)

    // Then score = sum(accumulation residuals) / stdev(estimation residuals)
    //            = 0.40 / sqrt(0.12 / 5) = 0.40 / sqrt(0.024)
    assertEquals(0.40 / sqrt(0.024), score, 1e-9)
    assertTrue(score > 0.0)
  }

  @Test
  fun `sector loading is stripped — same idiosyncratic returns score equally regardless of sector beta`() {
    // Given two stocks in the same sector sharing the identical mean-zero idiosyncratic returns but
    // loading on market and sector very differently (market 2.0/sector 1.5 vs market 0.4/sector 3.0)
    val context = context(closesFromReturns(100.0, spyReturns + 0.0), closesFromReturns(100.0, sectorReturns + 0.0))
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val stockA = stock("A", factorLoadedCloses(2.0, 1.5))
    val stockB = stock("B", factorLoadedCloses(0.4, 3.0))

    // When both are scored
    val scoreA = ranker.score(stockA, stockA.quotes.last(), context)
    val scoreB = ranker.score(stockB, stockB.quotes.last(), context)

    // Then both the market and the sector loadings are regressed away — only the shared idiosyncratic
    // residual drives the score, so a high-sector-beta name has no advantage over a low-sector-beta one
    assertEquals(scoreA, scoreB, 1e-9)
    assertTrue(scoreB > 0.0, "score should be a real residual-momentum value, not the unscoreable sentinel")
  }

  @Test
  fun `without the stock's sector factor the multi-factor residual cannot score`() {
    // Given a fully-scoreable stock, but (a) its sector ETF series is absent from the context, and
    // (b) a sibling that carries no sector mapping at all — neither can be sector-neutralized
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val closes = factorLoadedCloses(2.0, 1.5)
    val marketOnlyContext = BacktestContext.EMPTY.copy(spyQuoteMap = quoteSeries("SPY", closesFromReturns(100.0, spyReturns + 0.0)))
    val inSector = stock("A", closes, sectorSymbol = "XLK")
    val noSector = stock("B", closes, sectorSymbol = null)

    // When scored without a sector factor available
    val missingSeriesScore = ranker.score(inSector, inSector.quotes.last(), marketOnlyContext)
    val noMappingScore = ranker.score(
      noSector,
      noSector.quotes.last(),
      context(closesFromReturns(100.0, spyReturns + 0.0), closesFromReturns(100.0, sectorReturns + 0.0)),
    )

    // Then a sector-neutral score is meaningless without the sector factor — both are unscoreable
    assertTrue(missingSeriesScore < -1e300, "missing-sector-series score $missingSeriesScore should be the unscoreable sentinel")
    assertTrue(noMappingScore < -1e300, "no-sector-mapping score $noMappingScore should be the unscoreable sentinel")
  }

  @Test
  fun `perfectly collinear market and sector factors leave the fit singular and unscoreable`() {
    // Given a sector ETF series identical to SPY (the degenerate end of the real XLK~SPY collinearity):
    // the design matrix columns for market and sector coincide, so X'X is singular and no unique fit
    // exists
    val spyCloses = closesFromReturns(100.0, spyReturns + 0.0)
    val collinearContext = BacktestContext.EMPTY.copy(
      spyQuoteMap = quoteSeries("SPY", spyCloses),
      sectorEtfQuoteMap = mapOf(sector to quoteSeries(sector, spyCloses)),
    )
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val stock = stock("A", factorLoadedCloses(2.0, 1.5))

    // When scored against collinear factors
    val score = ranker.score(stock, stock.quotes.last(), collinearContext)

    // Then the singular normal equations collapse to the unscoreable sentinel rather than NaN/Infinity
    assertTrue(score < -1e300, "collinear-factor score $score should be the unscoreable sentinel")
  }

  @Test
  fun `score is invariant to idiosyncratic volatility scale — it measures residual momentum, not magnitude`() {
    // Given two stocks with the same residual *shape* (same market+sector loading, same idio sign path)
    // but one with 3x the idiosyncratic volatility
    val context = context(closesFromReturns(100.0, spyReturns + 0.0), closesFromReturns(100.0, sectorReturns + 0.0))
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val calm = stock("CALM", factorLoadedCloses(1.0, 1.0))
    val volatile = stock("VOL", factorLoadedCloses(1.0, 1.0, idioScale = 3.0))

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
    val context = context(closesFromReturns(100.0, spyReturns + 0.0), closesFromReturns(100.0, sectorReturns + 0.0))
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val baseCloses = factorLoadedCloses(2.0, 1.5)
    val base = stock("BASE", baseCloses)
    // Same pre-entry bars; entry bar (index 7 = date 8) tampered to 99999, plus wild future bars.
    val tampered = stock("TAMPERED", baseCloses.dropLast(1) + listOf(99999.0, 0.001, 88888.0))

    // When scored at the same entry date (the bar at date 8)
    val entryDate = date(8)
    val baseScore = ranker.score(base, base.quotes.first { it.date == entryDate }, context)
    val tamperedScore = ranker.score(tampered, tampered.quotes.first { it.date == entryDate }, context)

    // Then the entry-and-future tampering is invisible — only strictly-earlier bars feed the score
    assertEquals(baseScore, tamperedScore, 1e-9)
    assertTrue(baseScore > 0.0)
  }

  @Test
  fun `without a market proxy the multi-factor residual cannot score`() {
    // Given a fully-scoreable stock but no SPY data (the 2-arg overload, and an EMPTY context)
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val stock = stock("A", factorLoadedCloses(2.0, 1.5))

    // When scored without the market proxy
    val noContextScore = ranker.score(stock, stock.quotes.last())
    val emptyContextScore = ranker.score(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then a market-and-sector-residual score is meaningless without the market — both are unscoreable
    assertTrue(noContextScore < -1e300, "2-arg score $noContextScore should be the unscoreable sentinel")
    assertTrue(emptyContextScore < -1e300, "empty-context score $emptyContextScore should be the unscoreable sentinel")
  }

  @Test
  fun `a stock without enough history to fill the estimation window is unscoreable`() {
    // Given only 5 bars but a 7-day estimation window (estimation start index would be negative)
    val context = context(closesFromReturns(100.0, spyReturns + 0.0), closesFromReturns(100.0, sectorReturns + 0.0))
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val tooShort = stock("SHORT", listOf(100.0, 110.0, 99.0, 108.9, 102.0))

    // When scored
    val score = ranker.score(tooShort, tooShort.quotes.last(), context)

    // Then it collapses to the unscoreable sentinel and sorts last
    assertTrue(score < -1e300, "insufficient-history score $score should be the unscoreable sentinel")
  }

  @Test
  fun `a mid-window gap in the sector series drops too many paired returns and is unscoreable`() {
    // Given a stock that is fully scoreable with complete factor data, but the sector ETF is missing a
    // mid-window date (date 4) — voiding both returns that touch it. With 6 estimation returns the
    // 80% floor needs 5 paired; dropping 2 leaves 4, below the floor.
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val stock = stock("A", factorLoadedCloses(2.0, 1.5))
    val spyCloses = closesFromReturns(100.0, spyReturns + 0.0)
    val sectorCloses = closesFromReturns(100.0, sectorReturns + 0.0)
    val fullContext = context(spyCloses, sectorCloses)
    val gappedSectorContext = BacktestContext.EMPTY.copy(
      spyQuoteMap = quoteSeries("SPY", spyCloses),
      sectorEtfQuoteMap = mapOf(sector to quoteSeries(sector, sectorCloses).filterKeys { it != date(4) }),
    )

    // When scored with full sector data vs a sector series gapped mid-window
    val fullScore = ranker.score(stock, stock.quotes.last(), fullContext)
    val gappedScore = ranker.score(stock, stock.quotes.last(), gappedSectorContext)

    // Then the stock is normally scoreable, but the sector gap drops it below the pairing floor — the
    // floor binds on the sector factor, not only on the market factor
    assertTrue(fullScore > -1e300 && fullScore.isFinite(), "full-data score $fullScore should be a real value")
    assertTrue(gappedScore < -1e300, "sector-gapped score $gappedScore should be the unscoreable sentinel")
  }

  @Test
  fun `a pure factor replica has no idiosyncratic signal and is unscoreable`() {
    // Given a stock whose returns are exactly 2x market + 1.5x sector with zero idiosyncratic
    // component — every regression residual is zero, so the residual stdev is zero
    val context = context(closesFromReturns(100.0, spyReturns + 0.0), closesFromReturns(100.0, sectorReturns + 0.0))
    val ranker = MultiFactorResidualMomentumRanker(estimationDays, momentumDays, skipDays)
    val replica = stock("REPLICA", closesFromReturns(100.0, (0 until 6).map { 2.0 * spyReturns[it] + 1.5 * sectorReturns[it] } + 0.0))

    // When scored
    val score = ranker.score(replica, replica.quotes.last(), context)

    // Then there is no idiosyncratic momentum to standardize — it is unscoreable, not a divide-by-zero
    assertTrue(score < -1e300, "zero-residual score $score should be the unscoreable sentinel")
  }

  @Test
  fun `skipDays must be at least one so the accumulation window ends before the entry bar`() {
    // Given skipDays = 0 (accumulation end would coincide with the entry bar)
    // When constructed / Then construction is rejected (no entry-or-future bar may be read)
    assertThrows(IllegalArgumentException::class.java) {
      MultiFactorResidualMomentumRanker(estimationDays = 504, momentumDays = 252, skipDays = 0)
    }
  }

  @Test
  fun `momentumDays must exceed skipDays so the accumulation window is non-empty`() {
    // Given a momentum window no longer than the skip
    // When constructed / Then construction is rejected
    assertThrows(IllegalArgumentException::class.java) {
      MultiFactorResidualMomentumRanker(estimationDays = 504, momentumDays = 21, skipDays = 21)
    }
  }

  @Test
  fun `estimationDays must contain the accumulation window`() {
    // Given an estimation window shorter than momentumDays + skipDays (accumulation would spill out)
    // When constructed / Then construction is rejected
    assertThrows(IllegalArgumentException::class.java) {
      MultiFactorResidualMomentumRanker(estimationDays = 200, momentumDays = 252, skipDays = 21)
    }
  }

  @Test
  fun `declares its estimation window as the trailing warmup history the engine must load`() {
    // Given the production-default ranker, whose regression needs 504 trailing days
    val ranker = MultiFactorResidualMomentumRanker(estimationDays = 504, momentumDays = 252, skipDays = 21)

    // When the engine asks how much pre-window history to load so this ranker is scoreable
    // Then it is the estimation window — without it every in-window entry would be unscoreable
    assertEquals(504, ranker.warmupTradingDays())
  }
}
