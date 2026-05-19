package com.skrymer.udgaard.scanner.model

import com.skrymer.udgaard.portfolio.model.InstrumentType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Unit tests for the [CohortWindow] aggregate. The window owns its own metric math per
 * ADR 0001: services orchestrate; aggregates compute. Fixtures are constructed in-memory
 * — no DB, no Spring, no mocks.
 */
class CohortWindowTest {
  @Test
  fun `signalsTakenOn returns the count of trades whose signalDate equals that date`() {
    // Given: two trades anchored to a 2026-05-19 scan, one trade anchored elsewhere
    val window = CohortWindow(
      scanRuns = listOf(scanRun(LocalDate.of(2026, 5, 19), matchedSymbols = listOf("AAPL", "NFLX", "XOM"))),
      tradesEntered = listOf(
        trade("AAPL", signalDate = LocalDate.of(2026, 5, 19)),
        trade("NFLX", signalDate = LocalDate.of(2026, 5, 19)),
        trade("KGS", signalDate = LocalDate.of(2026, 5, 12)),
      ),
      windowStart = LocalDate.of(2026, 4, 29),
      windowEnd = LocalDate.of(2026, 5, 19),
    )

    // When / Then
    assertEquals(2, window.signalsTakenOn(LocalDate.of(2026, 5, 19)))
    assertEquals(1, window.signalsTakenOn(LocalDate.of(2026, 5, 12)))
    assertEquals(0, window.signalsTakenOn(LocalDate.of(2026, 5, 1)), "no trades anchored here = 0")
  }

  @Test
  fun `rollingJaccard unions matched symbols across scan runs and intersects with taken trades`() {
    // Given: two scan runs in the window — day 1 emits AAPL, NFLX; day 2 emits NFLX, XOM.
    // Two trades taken: AAPL on day 1, XOM on day 2. NFLX was emitted both days but never taken.
    //   A (union) = { AAPL, NFLX, XOM }
    //   B (taken) = { AAPL, XOM }
    //   |A ∩ B| / |A ∪ B| = 2 / 3
    val day1 = LocalDate.of(2026, 5, 18)
    val day2 = LocalDate.of(2026, 5, 19)
    val window = CohortWindow(
      scanRuns = listOf(
        scanRun(day1, matchedSymbols = listOf("AAPL", "NFLX")),
        scanRun(day2, matchedSymbols = listOf("NFLX", "XOM")),
      ),
      tradesEntered = listOf(
        trade("AAPL", signalDate = day1),
        trade("XOM", signalDate = day2),
      ),
      windowStart = LocalDate.of(2026, 4, 29),
      windowEnd = day2,
    )

    // When
    val jaccard = window.rollingJaccard()

    // Then
    assertEquals(2.0 / 3.0, jaccard, 1e-9)
  }

  @Test
  fun `jaccardBelowThresholdConsecutiveDays counts the trailing run of below-threshold scan-run days, ignoring skipped days`() {
    // Given: 6 scan-run days (with gaps between them; calendar dates non-consecutive).
    // Per-day daily Jaccard pattern (emitted ∩ taken / emitted ∪ taken on each scan day):
    //   2026-05-01: emitted [AAA], taken []        → Jaccard 0     — below 0.5
    //   2026-05-04: emitted [BBB], taken [BBB]     → Jaccard 1     — above (resets)
    //   2026-05-07: emitted [CCC], taken []        → Jaccard 0     — below
    //   2026-05-09: emitted [DDD], taken []        → Jaccard 0     — below
    //   2026-05-13: emitted [EEE], taken []        → Jaccard 0     — below   (calendar gap May 10–12 has no scan runs)
    //   2026-05-19: emitted [FFF], taken [FFF]     → Jaccard 1     — above (resets counter to 0)
    // Final counter value at end-of-window: 0 (the above-threshold day on 5/19 resets it).
    val d = LocalDate.parse("2026-05-01")
    val window = CohortWindow(
      scanRuns = listOf(
        scanRun(d, matchedSymbols = listOf("AAA")),
        scanRun(d.plusDays(3), matchedSymbols = listOf("BBB")),
        scanRun(d.plusDays(6), matchedSymbols = listOf("CCC")),
        scanRun(d.plusDays(8), matchedSymbols = listOf("DDD")),
        scanRun(d.plusDays(12), matchedSymbols = listOf("EEE")),
        scanRun(d.plusDays(18), matchedSymbols = listOf("FFF")),
      ),
      tradesEntered = listOf(
        trade("BBB", signalDate = d.plusDays(3)),
        trade("FFF", signalDate = d.plusDays(18)),
      ),
      windowStart = d,
      windowEnd = d.plusDays(18),
    )

    // When
    val counter = window.jaccardBelowThresholdConsecutiveDays(threshold = 0.5)

    // Then: the last scan-run day (5/19) is at-threshold so the trailing run is 0
    assertEquals(0, counter)
  }

  @Test
  fun `jaccardBelowThresholdConsecutiveDays accumulates across skipped calendar days but resets on a good scan-run day`() {
    // Given: trailing run of 3 below-threshold scan-run days (with a calendar gap in the middle).
    // The gap should NOT reset the counter — per option c, skipped days are no-ops.
    val d = LocalDate.parse("2026-05-01")
    val window = CohortWindow(
      scanRuns = listOf(
        scanRun(d, matchedSymbols = listOf("AAA")), // taken — resets
        scanRun(d.plusDays(2), matchedSymbols = listOf("BBB")), // below — counter 1
        // Calendar gap days 5/4 - 5/9 (no scan runs)
        scanRun(d.plusDays(10), matchedSymbols = listOf("CCC")), // below — counter 2 (gap was no-op)
        scanRun(d.plusDays(14), matchedSymbols = listOf("DDD")), // below — counter 3
      ),
      tradesEntered = listOf(
        trade("AAA", signalDate = d),
      ),
      windowStart = d,
      windowEnd = d.plusDays(14),
    )

    // When
    val counter = window.jaccardBelowThresholdConsecutiveDays(threshold = 0.5)

    // Then: 3 below-threshold scan days in a row, calendar gaps treated as no-ops
    assertEquals(3, counter)
  }

  @Test
  fun `skipRateAboveThresholdScannerRichDays counts trailing scanner-rich days above threshold, thin days no-op`() {
    // Given: 5 scan runs. Pattern of (match_count, taken_count):
    //   day 1: (12, 6)  rich, skipRate 6/12=0.5  → at-threshold (not above), resets
    //   day 2: (15, 3)  rich, skipRate 12/15=0.8 → above, counter 1
    //   day 3: (5, 0)   thin (match<10) — NO-OP, counter stays 1
    //   day 4: (12, 1)  rich, skipRate 11/12=0.92→ above, counter 2
    //   day 5: (15, 2)  rich, skipRate 13/15=0.87→ above, counter 3
    // Trailing-run counter = 3.
    val d = LocalDate.parse("2026-05-01")
    val window = CohortWindow(
      scanRuns = listOf(
        scanRun(d.plusDays(0), matchedSymbols = (1..12).map { "S$it" }), // 12 rich
        scanRun(d.plusDays(1), matchedSymbols = (1..15).map { "S$it" }), // 15 rich
        scanRun(d.plusDays(2), matchedSymbols = (1..5).map { "T$it" }), // 5 thin
        scanRun(d.plusDays(3), matchedSymbols = (1..12).map { "U$it" }), // 12 rich
        scanRun(d.plusDays(4), matchedSymbols = (1..15).map { "V$it" }), // 15 rich
      ),
      tradesEntered = listOf(
        // day 1 — 6 takes (50% — at threshold, not above, resets)
        trade("S1", signalDate = d.plusDays(0)),
        trade("S2", signalDate = d.plusDays(0)),
        trade("S3", signalDate = d.plusDays(0)),
        trade("S4", signalDate = d.plusDays(0)),
        trade("S5", signalDate = d.plusDays(0)),
        trade("S6", signalDate = d.plusDays(0)),
        // day 2 — 3 takes (skipRate 0.8 — above)
        trade("S1", signalDate = d.plusDays(1)),
        trade("S2", signalDate = d.plusDays(1)),
        trade("S3", signalDate = d.plusDays(1)),
        // day 4 — 1 take (skipRate 0.92 — above)
        trade("U1", signalDate = d.plusDays(3)),
        // day 5 — 2 takes (skipRate 0.87 — above)
        trade("V1", signalDate = d.plusDays(4)),
        trade("V2", signalDate = d.plusDays(4)),
      ),
      windowStart = d,
      windowEnd = d.plusDays(4),
    )

    // When
    val counter = window.skipRateAboveThresholdScannerRichDays(threshold = 0.5)

    // Then: trailing run of 3 above-threshold scanner-rich days; the thin day on 5/3 was a
    // no-op (didn't reset, didn't advance)
    assertEquals(3, counter)
  }

  @Test
  fun `executionDriftAlert fires only when below-threshold Jaccard sustains for 10 scan-run days`() {
    // Given: 10 consecutive scan-run days with no trades taken — daily Jaccard 0 on each
    val d = LocalDate.parse("2026-05-01")
    val runs = (0..9).map { i ->
      scanRun(d.plusDays(i.toLong()), matchedSymbols = listOf("X$i"))
    }
    val windowAt10 = CohortWindow(scanRuns = runs, tradesEntered = emptyList(), windowStart = d, windowEnd = d.plusDays(9))
    val windowAt9 = CohortWindow(scanRuns = runs.dropLast(1), tradesEntered = emptyList(), windowStart = d, windowEnd = d.plusDays(8))

    // When / Then: alert fires at 10, not at 9
    assertEquals(true, windowAt10.executionDriftAlert())
    assertEquals(false, windowAt9.executionDriftAlert())
  }

  @Test
  fun `traderFilteringAlert fires only when above-threshold skip rate sustains for 5 scanner-rich days`() {
    // Given: 5 consecutive scanner-rich days (match_count 10), 0 trades taken (skip = 1.0)
    val d = LocalDate.parse("2026-05-01")
    val runs = (0..4).map { i ->
      scanRun(d.plusDays(i.toLong()), matchedSymbols = (1..10).map { "Y${i}_$it" })
    }
    val windowAt5 = CohortWindow(scanRuns = runs, tradesEntered = emptyList(), windowStart = d, windowEnd = d.plusDays(4))
    val windowAt4 = CohortWindow(scanRuns = runs.dropLast(1), tradesEntered = emptyList(), windowStart = d, windowEnd = d.plusDays(3))

    // When / Then
    assertEquals(true, windowAt5.traderFilteringAlert())
    assertEquals(false, windowAt4.traderFilteringAlert())
  }

  @Test
  fun `scannerRichDayCount counts only scan runs whose match_count meets the threshold`() {
    // Given: 4 scan runs with varying match counts; default threshold = 10
    val window = CohortWindow(
      scanRuns = listOf(
        scanRun(LocalDate.of(2026, 5, 12), matchedSymbols = (1..5).map { "S$it" }), // 5 — thin day
        scanRun(LocalDate.of(2026, 5, 13), matchedSymbols = (1..12).map { "S$it" }), // 12 — rich
        scanRun(LocalDate.of(2026, 5, 14), matchedSymbols = (1..8).map { "S$it" }), // 8 — thin
        scanRun(LocalDate.of(2026, 5, 15), matchedSymbols = (1..15).map { "S$it" }), // 15 — rich
      ),
      tradesEntered = emptyList(),
      windowStart = LocalDate.of(2026, 5, 1),
      windowEnd = LocalDate.of(2026, 5, 19),
    )

    // When / Then
    assertEquals(2, window.scannerRichDayCount(), "only the 12- and 15-match days count as scanner-rich")
  }

  @Test
  fun `rollingJaccard returns 0 when no data in the window`() {
    // Given: empty window (warm-up period)
    val window = CohortWindow(
      scanRuns = emptyList(),
      tradesEntered = emptyList(),
      windowStart = LocalDate.of(2026, 4, 29),
      windowEnd = LocalDate.of(2026, 5, 19),
    )

    // When / Then: union is empty; treat as no-divergence-to-measure, return 0
    assertEquals(0.0, window.rollingJaccard(), 1e-9)
  }

  @Test
  fun `signalsEmittedOn returns the matched-symbol count from the scan run on that date`() {
    // Given: one scan run on 2026-05-19 with three matched symbols
    val window = CohortWindow(
      scanRuns = listOf(
        scanRun(LocalDate.of(2026, 5, 19), matchedSymbols = listOf("AAPL", "NFLX", "XOM")),
      ),
      tradesEntered = emptyList(),
      windowStart = LocalDate.of(2026, 4, 29),
      windowEnd = LocalDate.of(2026, 5, 19),
    )

    // When
    val emitted = window.signalsEmittedOn(LocalDate.of(2026, 5, 19))

    // Then
    assertEquals(3, emitted)
  }

  // ── fixture helpers ─────────────────────────────────────────────

  private fun scanRun(signalDate: LocalDate, matchedSymbols: List<String>) = ScanRun(
    id = null,
    signalDate = signalDate,
    scanTimestamp = signalDate.atStartOfDay().plusHours(18),
    entryStrategyName = "Vcp",
    exitStrategyName = "VcpExitStrategy",
    rankerName = "SectorEdgeWithTightness",
    totalStocksScanned = 3000,
    matchedSymbols = matchedSymbols.map { symbol ->
      MatchedSymbol(symbol = symbol, sectorSymbol = "XLK", closePrice = 100.0, atr = 2.0, rankScore = 100.0)
    },
  )

  private fun trade(symbol: String, signalDate: LocalDate) = ScannerTrade(
    id = null,
    symbol = symbol,
    sectorSymbol = "XLK",
    instrumentType = InstrumentType.STOCK,
    entryPrice = 100.0,
    entryDate = signalDate.plusDays(1),
    quantity = 100,
    optionType = null,
    strikePrice = null,
    expirationDate = null,
    entryStrategyName = "Vcp",
    exitStrategyName = "VcpExitStrategy",
    notes = null,
    createdAt = LocalDateTime.now(),
    signalDate = signalDate,
  )
}
