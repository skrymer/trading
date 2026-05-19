package com.skrymer.udgaard.scanner.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One persisted invocation of the entry-candidate scan. Distinct from `ScannerTrade` —
 * a scan run records what the scanner *offered* on a given day; `ScannerTrade` records
 * what the trader *took*. The two together drive the cohort-divergence diagnostic.
 *
 * Uniqueness is by `(signalDate, entryStrategyName, exitStrategyName, rankerName)`:
 * multiple scans with the same config on the same trading day upsert (last write wins).
 * The trader's operating cadence is one scan per trading day after US market close.
 */
data class ScanRun(
  val id: Long?,
  val signalDate: LocalDate,
  val scanTimestamp: LocalDateTime,
  val entryStrategyName: String,
  val exitStrategyName: String,
  val rankerName: String,
  val totalStocksScanned: Int,
  val matchedSymbols: List<MatchedSymbol>,
) {
  val matchCount: Int get() = matchedSymbols.size
}

/**
 * Lean ScanResult retained for the diagnostic. Drops the per-condition
 * `EntrySignalDetails` (already persisted per-trade on `scanner_trades.signalSnapshot`
 * for taken trades) in favour of the minimal viable fields:
 *   - `symbol` — required for Jaccard set comparison
 *   - `sectorSymbol` — supports concentration-risk follow-ups (Herfindahl, rank-1 share)
 *   - `closePrice` / `atr` — supports a possible future shadow-book simulation
 *   - `rankScore` — encodes priority within the matched cohort
 */
data class MatchedSymbol(
  val symbol: String,
  val sectorSymbol: String?,
  val closePrice: Double,
  val atr: Double,
  val rankScore: Double?,
)
