package com.skrymer.udgaard.portfolio.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Position domain model - represents an aggregate holding in a security
 * Built from one or more Executions
 */
data class Position(
  val id: Long?,
  val portfolioId: Long,
  val symbol: String,
  val underlyingSymbol: String?,
  val instrumentType: InstrumentType,
  // Options-specific fields
  val optionType: OptionType?,
  val strikePrice: Double?,
  val expirationDate: LocalDate?,
  val multiplier: Int = 100,
  // Position state (aggregated from executions)
  val currentQuantity: Int,
  val currentContracts: Int?,
  val averageEntryPrice: Double,
  val totalCost: Double,
  val status: PositionStatus,
  // Dates
  val openedDate: LocalDate,
  val closedDate: LocalDate?,
  // P&L
  val realizedPnl: Double?,
  // Rolling (clean 1-to-1 relationship)
  val rolledToPositionId: Long?,
  val parentPositionId: Long?,
  val rollNumber: Int = 0,
  // Strategy (editable metadata)
  val entryStrategy: String?,
  val exitStrategy: String?,
  // Metadata (editable)
  val notes: String?,
  val currency: String = "USD",
  val source: PositionSource = PositionSource.MANUAL,
  val createdAt: LocalDateTime? = null,
  val updatedAt: LocalDateTime? = null,
) {
  /**
   * Calculate position size (total value)
   */
  val positionSize: Double
    get() =
      if (instrumentType == InstrumentType.OPTION) {
        averageEntryPrice * (currentContracts ?: currentQuantity) * multiplier
      } else {
        averageEntryPrice * currentQuantity
      }

  /**
   * Check if position is open
   */
  val isOpen: Boolean
    get() = status == PositionStatus.OPEN

  /**
   * Check if position is closed
   */
  val isClosed: Boolean
    get() = status == PositionStatus.CLOSED

  /**
   * Check if position is from broker
   */
  val isBrokerImported: Boolean
    get() = source == PositionSource.BROKER

  /**
   * Check if position is part of a roll chain
   */
  val isRolled: Boolean
    get() = rolledToPositionId != null || parentPositionId != null
}

/**
 * Instrument type
 */
enum class InstrumentType {
  STOCK,
  OPTION,
  LEVERAGED_ETF,
}

/**
 * Option type
 */
enum class OptionType {
  CALL,
  PUT,
}

/**
 * Position status
 */
enum class PositionStatus {
  OPEN,
  CLOSED,
}

/**
 * Position source
 */
enum class PositionSource {
  BROKER,
  MANUAL,
}
