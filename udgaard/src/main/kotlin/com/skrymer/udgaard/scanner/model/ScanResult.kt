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
)

/**
 * Response from checking exits on all scanner trades
 */
data class ExitCheckResponse(
  val results: List<ExitCheckResult>,
  val checksPerformed: Int,
  val exitsTriggered: Int,
)
