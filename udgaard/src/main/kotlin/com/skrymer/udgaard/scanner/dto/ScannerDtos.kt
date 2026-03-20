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
)
