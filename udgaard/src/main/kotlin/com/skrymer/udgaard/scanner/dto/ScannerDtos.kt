package com.skrymer.udgaard.scanner.dto

/**
 * Request to run a scan for entry signals
 */
data class ScanRequest(
  val entryStrategyName: String,
  val exitStrategyName: String,
  val stockSymbols: List<String>? = null,
  val assetTypes: List<String>? = null,
  val includeSectors: List<String>? = null,
  val excludeSectors: List<String>? = null,
  val nearMissLimit: Int? = null,
  val rankerName: String? = null,
)

/**
 * Request to add a new scanner trade
 */
data class AddScannerTradeRequest(
  val symbol: String,
  val sectorSymbol: String? = null,
  val instrumentType: String,
  val entryPrice: Double,
  val entryDate: String,
  val quantity: Int,
  val optionType: String? = null,
  val strikePrice: Double? = null,
  val expirationDate: String? = null,
  val multiplier: Int? = null,
  val optionPrice: Double? = null,
  val delta: Double? = null,
  val entryStrategyName: String,
  val exitStrategyName: String,
  val notes: String? = null,
)

/**
 * Request to roll a scanner trade (close old + create new with updated option details)
 */
data class RollScannerTradeRequest(
  val closePrice: Double,
  val newStrikePrice: Double,
  val newExpirationDate: String,
  val newOptionType: String? = null,
  val newEntryPrice: Double,
  val newEntryDate: String,
  val newQuantity: Int,
  val newOptionPrice: Double? = null,
  val newDelta: Double? = null,
)

/**
 * Request to update a scanner trade (notes only)
 */
data class UpdateScannerTradeRequest(
  val notes: String?,
)

/**
 * Request to fetch option contracts for multiple symbols
 */
data class OptionContractsRequest(
  val symbols: List<String>,
  val stockPrices: Map<String, Double>,
  val date: String? = null,
)

/**
 * Response for a single symbol's recommended option contract
 */
data class OptionContractResponse(
  val symbol: String,
  val strike: Double,
  val expiration: String,
  val price: Double,
  val delta: Double,
  val openInterest: Int?,
  val intrinsic: Double,
  val extrinsic: Double,
)

data class CloseScannerTradeRequest(
  val exitPrice: Double,
  val exitDate: String,
)

data class DrawdownStatsResponse(
  val totalRealizedPnl: Double,
  val closedTradeCount: Int,
  val winRate: Double,
  val totalUnrealizedPnl: Double,
  val currentEquity: Double,
  val peakEquity: Double,
  val currentDrawdownPct: Double,
)

data class StrategyClosedStats(
  val strategy: String,
  val trades: Int,
  val wins: Int,
  val losses: Int,
  val winRate: Double,
  val edge: Double,
  val profitFactor: Double?,
  val avgWinPct: Double,
  val avgLossPct: Double,
  val avgWinDollars: Double,
  val avgLossDollars: Double,
  val totalPnl: Double,
)

data class ClosedTradeStatsResponse(
  val overall: StrategyClosedStats?,
  val byStrategy: List<StrategyClosedStats>,
)

data class ValidateEntriesRequest(
  val symbols: List<String>,
  val entryStrategyName: String,
  val exitStrategyName: String,
)
