package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit tests for [SectorEdgeWithTightnessRanker].
 *
 * Tightness is a tie-breaker within a sector — not a primary axis. The sector priority list
 * dominates ordering; tightness only resolves which stocks within the same sector go first.
 * "Tighter" means lower `ATR / close` (a smaller volatility envelope around current price),
 * aligned with Minervini-style VCP setups where contraction precedes expansion.
 */
class SectorEdgeWithTightnessRankerTest {
  private val sectorRanking = listOf("XLC", "XLU", "XLI", "XLK", "XLE", "XLB", "XLV", "XLF", "XLY", "XLP", "XLRE")
  private val ranker = SectorEdgeWithTightnessRanker(sectorRanking)
  private val date = LocalDate.of(2026, 4, 1)

  @Test
  fun `within the same sector, the tighter base ranks higher`() {
    // Given: two stocks both in XLC (top sector), priced identically, but A has a smaller
    // ATR-to-price ratio — the textbook VCP "tighter base" setup.
    val tighter = Stock("AAA", sectorSymbol = "XLC")
    val looser = Stock("BBB", sectorSymbol = "XLC")
    val tighterQuote = StockQuote(date = date, closePrice = 30.0, atr = 0.60) // ATR/close = 0.02
    val looserQuote = StockQuote(date = date, closePrice = 30.0, atr = 3.00) // ATR/close = 0.10

    // When
    val tighterScore = ranker.score(tighter, tighterQuote)
    val looserScore = ranker.score(looser, looserQuote)

    // Then: tighter wins the tie-break
    assertTrue(
      tighterScore > looserScore,
      "expected tighter base to outrank looser (tighter=$tighterScore, looser=$looserScore)",
    )
    // Sanity: both stocks share the same sector priority, so the *only* differentiator is tightness
    assertEquals("XLC", tighter.sectorSymbol)
    assertEquals(tighter.sectorSymbol, looser.sectorSymbol)
  }

  @Test
  fun `non-finite or negative ATR does not produce a phantom tightness boost`() {
    // Given: a quote with NaN ATR (corrupted) and another with negative ATR (stale
    // ingestion artefact). Without a guard, `-ATR/close` would yield NaN or a *positive*
    // bonus — the latter would inflate the stock above its healthy peers in the same sector,
    // which is the actual bug we're guarding against.
    val nanQuote = StockQuote(date = date, closePrice = 30.0, atr = Double.NaN)
    val negativeAtrQuote = StockQuote(date = date, closePrice = 30.0, atr = -10.0)
    val xlc = Stock("CORRUPT-XLC", sectorSymbol = "XLC")
    val nextSectorRanked = ranker.score(Stock("CLEAN-XLU", sectorSymbol = "XLU"), StockQuote(date = date, closePrice = 30.0, atr = 0.01))

    // When
    val nanScore = ranker.score(xlc, nanQuote)
    val negativeScore = ranker.score(xlc, negativeAtrQuote)

    // Then
    assertTrue(nanScore.isFinite(), "NaN ATR must not propagate to score (would break sort ordering)")
    assertTrue(negativeScore.isFinite(), "negative ATR must not produce a non-finite score")
    // Phantom-boost guard: a negative ATR must NOT inflate the score beyond the sector ceiling.
    // The sector ceiling is sectorScore × SECTOR_SCALE = 11 × 10 = 110 for XLC (top sector).
    val xlcCeiling = 110.0
    assertTrue(
      negativeScore <= xlcCeiling + 1e-9,
      "negative ATR must not lift score above sector ceiling $xlcCeiling, got $negativeScore",
    )
    assertTrue(
      nanScore <= xlcCeiling + 1e-9,
      "NaN ATR must not lift score above sector ceiling $xlcCeiling, got $nanScore",
    )
    // Sector ordering still holds: even with bad data, XLC stock outranks an XLU stock
    assertTrue(nanScore > nextSectorRanked, "sector ordering must hold even when ATR is corrupted")
    assertTrue(negativeScore > nextSectorRanked, "sector ordering must hold even when ATR is negative")
  }

  @Test
  fun `higher-priority sector dominates tightness — a loose top-sector stock outranks a tight lower-sector stock`() {
    // Given: a pathologically loose stock in the top sector (XLC) vs a pathologically tight
    // stock in the second sector (XLU). Tightness is a tie-breaker, not a replacement for
    // sector priority — XLC must still win.
    val looseTopSector = Stock("LOOSE-XLC", sectorSymbol = "XLC")
    val tightSecondSector = Stock("TIGHT-XLU", sectorSymbol = "XLU")
    val looseQuote = StockQuote(date = date, closePrice = 30.0, atr = 5.00) // ATR/close = 0.167
    val tightQuote = StockQuote(date = date, closePrice = 30.0, atr = 0.30) // ATR/close = 0.010

    // When
    val looseTopScore = ranker.score(looseTopSector, looseQuote)
    val tightSecondScore = ranker.score(tightSecondSector, tightQuote)

    // Then: the top-sector stock wins regardless of how loose its base is
    assertTrue(
      looseTopScore > tightSecondScore,
      "expected loose XLC to outrank tight XLU (loose XLC=$looseTopScore, tight XLU=$tightSecondScore)",
    )
  }
}
