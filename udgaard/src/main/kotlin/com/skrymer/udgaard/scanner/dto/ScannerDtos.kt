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
)

/**
 * Request to update a scanner trade (notes only)
 */
data class UpdateScannerTradeRequest(
  val notes: String?,
)
