package com.skrymer.udgaard.scanner.model

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import java.time.LocalDate

/**
 * Result of scanning a single stock for entry signals
 */
data class ScanResult(
  val symbol: String,
  val sectorSymbol: String?,
  val closePrice: Double,
  val date: LocalDate,
  val entrySignalDetails: EntrySignalDetails?,
  val atr: Double,
  val trend: String?,
  val rankScore: Double? = null,
)

/**
 * Response from a scan operation
 */
data class ScanResponse(
  val scanDate: LocalDate,
  val entryStrategyName: String,
  val exitStrategyName: String,
  val results: List<ScanResult>,
  val totalStocksScanned: Int,
  val executionTimeMs: Long,
  val nearMissCandidates: List<NearMissCandidate> = emptyList(),
  val conditionFailureSummary: List<ConditionFailureSummary> = emptyList(),
  val rankerName: String? = null,
)

/**
 * A stock that nearly matched all entry conditions — useful for watchlists.
 */
data class NearMissCandidate(
  val symbol: String,
  val sectorSymbol: String?,
  val closePrice: Double,
  val date: LocalDate,
  val entrySignalDetails: EntrySignalDetails,
  val atr: Double,
  val trend: String?,
  val conditionsPassed: Int,
  val conditionsTotal: Int,
  val rankScore: Double? = null,
)

/**
 * Aggregated view of how often a specific condition blocks entry across all evaluated stocks.
 */
data class ConditionFailureSummary(
  val conditionType: String,
  val description: String,
  val stocksBlocked: Int,
  val totalStocksEvaluated: Int,
)

/**
 * Result of checking exit conditions for a single scanner trade
 */
data class ExitCheckResult(
  val tradeId: Long,
  val symbol: String,
  val exitTriggered: Boolean,
  val exitReason: String?,
  val currentPrice: Double,
  val unrealizedPnlPercent: Double,
  val unrealizedPnlDollars: Double,
  val usedLiveData: Boolean = false,
)

/**
 * Response from checking exits on all scanner trades
 */
data class ExitCheckResponse(
  val results: List<ExitCheckResult>,
  val checksPerformed: Int,
  val exitsTriggered: Int,
)

data class EntryValidationResult(
  val symbol: String,
  val entryStillValid: Boolean,
  val exitWouldTrigger: Boolean,
  val exitReason: String?,
  val currentPrice: Double,
  val usedLiveData: Boolean,
  val entrySignalDetails: EntrySignalDetails?,
)

data class EntryValidationResponse(
  val results: List<EntryValidationResult>,
  val validCount: Int,
  val invalidCount: Int,
  val doaCount: Int,
)
