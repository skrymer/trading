package com.skrymer.udgaard.scanner.model

import java.time.LocalDate

/**
 * Aggregate root for cohort-divergence diagnostic computations over a rolling window.
 *
 * The window bundles both the scanner's offered signals (`scanRuns`) and the trader's
 * taken trades (`tradesEntered`) and owns the metric math that compares them. Per ADR 0001
 * the service that constructs this aggregate is orchestration only — it asks the aggregate
 * questions and never branches on its state.
 *
 * Constructed per-request from query results; not itself persisted.
 *
 * `tradesEntered` should be pre-filtered to trades whose `signalDate` is within
 * `[windowStart, windowEnd]` — the repository query enforces this, and the metric math
 * assumes it.
 */
data class CohortWindow(
  val scanRuns: List<ScanRun>,
  val tradesEntered: List<ScannerTrade>,
  val windowStart: LocalDate,
  val windowEnd: LocalDate,
  val scannerRichThreshold: Int = 10,
) {
  /**
   * Number of distinct symbols the scanner offered on [date]. Returns 0 if no scan run
   * happened on that date (skipped day).
   */
  fun signalsEmittedOn(date: LocalDate): Int =
    scanRuns.firstOrNull { it.signalDate == date }?.matchedSymbols?.size ?: 0

  /**
   * Number of scanner trades anchored to a scan on [date] (via `signalDate`). Excludes
   * legacy trades with a null `signalDate` since they pre-date snapshot persistence and
   * cannot be matched to a scan run.
   */
  fun signalsTakenOn(date: LocalDate): Int =
    tradesEntered.count { it.signalDate == date }

  /**
   * |A ∩ B| / |A ∪ B| where A = union of all matched symbols emitted across `scanRuns`,
   * B = symbols of `tradesEntered` whose `signalDate` falls in the window. The window's
   * primary metric for execution-vs-signal divergence.
   *
   * Returns 0 when the union is empty (no scan runs, no trades — warm-up period). The
   * caller's alert threshold treats this as no-divergence-to-measure, not a false alarm.
   */
  fun rollingJaccard(): Double {
    val emitted: Set<String> = scanRuns.flatMap { it.matchedSymbols.map(MatchedSymbol::symbol) }.toSet()
    val taken: Set<String> = tradesEntered.mapNotNull { it.symbol.takeIf { _ -> it.signalDate != null } }.toSet()
    val union = emitted union taken
    if (union.isEmpty()) return 0.0
    val intersect = emitted intersect taken
    return intersect.size.toDouble() / union.size.toDouble()
  }

  /**
   * Number of scan runs in the window whose `matchCount >= scannerRichThreshold`. Used as
   * the denominator/filter for the skip-rate trigger — thin-signal days can't produce a
   * meaningful "trader filtered too hard" assessment.
   */
  fun scannerRichDayCount(): Int =
    scanRuns.count { it.matchCount >= scannerRichThreshold }

  /**
   * Trailing count of consecutive scan-run days whose per-day Jaccard fell below [threshold].
   * Walks scan runs in chronological order; a day at-or-above-threshold resets the counter,
   * a below-threshold day advances it. Calendar days without a scan run are no-ops — they
   * don't reset the counter and don't advance it. The returned value is the counter as of
   * the last scan run in the window.
   *
   * Returns 0 when the window has no scan runs or the latest scan run is at-or-above the
   * threshold (no trailing run).
   */
  fun jaccardBelowThresholdConsecutiveDays(threshold: Double): Int {
    var counter = 0
    scanRuns.sortedBy { it.signalDate }.forEach { run ->
      counter = if (dailyJaccardForRun(run) < threshold) counter + 1 else 0
    }
    return counter
  }

  /**
   * Trailing count of consecutive scanner-rich scan-run days whose skip rate exceeded
   * [threshold]. Thin days (match_count below [scannerRichThreshold]) are no-ops — they
   * neither advance nor reset the counter, since a 100% skip rate on a 3-match day carries
   * no operational signal. Mirrors the structure of [jaccardBelowThresholdConsecutiveDays]
   * but on the rich-day-filtered subset.
   */
  fun skipRateAboveThresholdScannerRichDays(threshold: Double): Int {
    var counter = 0
    scanRuns
      .sortedBy { it.signalDate }
      .filter { it.matchCount >= scannerRichThreshold }
      .forEach { run ->
        counter = if (skipRateForRun(run) > threshold) counter + 1 else 0
      }
    return counter
  }

  /**
   * Execution-drift alert: the rolling Jaccard fell below 0.5 on 10 consecutive scan-run
   * days. The trader's executed cohort has diverged structurally from the offered cohort
   * — they're not taking what the scanner says, or are taking different names entirely.
   */
  fun executionDriftAlert(): Boolean =
    jaccardBelowThresholdConsecutiveDays(JACCARD_ALERT_THRESHOLD) >= JACCARD_ALERT_CONSECUTIVE_DAYS

  /**
   * Trader-filtering alert: skip rate exceeded 50% on 5 consecutive scanner-rich days.
   * On every day the scanner offered ≥10 candidates, the trader took less than half. Hints
   * the trader is filtering signals discretionarily instead of letting the leverage cap
   * bind.
   */
  fun traderFilteringAlert(): Boolean =
    skipRateAboveThresholdScannerRichDays(SKIP_RATE_ALERT_THRESHOLD) >= SKIP_RATE_ALERT_CONSECUTIVE_DAYS

  private fun skipRateForRun(run: ScanRun): Double {
    if (run.matchCount == 0) return 0.0
    val taken = tradesEntered.count { it.signalDate == run.signalDate }
    return 1.0 - taken.toDouble() / run.matchCount.toDouble()
  }

  private fun dailyJaccardForRun(run: ScanRun): Double {
    val emitted: Set<String> = run.matchedSymbols.map(MatchedSymbol::symbol).toSet()
    val taken: Set<String> = tradesEntered.filter { it.signalDate == run.signalDate }.map { it.symbol }.toSet()
    val union = emitted union taken
    if (union.isEmpty()) return 0.0
    val intersect = emitted intersect taken
    return intersect.size.toDouble() / union.size.toDouble()
  }

  companion object {
    private const val JACCARD_ALERT_THRESHOLD = 0.5
    private const val JACCARD_ALERT_CONSECUTIVE_DAYS = 10
    private const val SKIP_RATE_ALERT_THRESHOLD = 0.5
    private const val SKIP_RATE_ALERT_CONSECUTIVE_DAYS = 5
  }
}
