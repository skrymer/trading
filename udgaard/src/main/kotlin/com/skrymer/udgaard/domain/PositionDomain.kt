package com.skrymer.udgaard.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Position domain model - represents an aggregate holding in a security
 * Built from one or more Executions
 */
data class PositionDomain(
  val id: Long?,
  val portfolioId: Long,
  val symbol: String,
  val underlyingSymbol: String?,
  val instrumentType: InstrumentTypeDomain,
  // Options-specific fields
  val optionType: OptionTypeDomain?,
  val strikePrice: Double?,
  val expirationDate: LocalDate?,
  val multiplier: Int = 100,
  // Position state (aggregated from executions)
  val currentQuantity: Int,
  val currentContracts: Int?,
  val averageEntryPrice: Double,
  val totalCost: Double,
  val status: PositionStatusDomain,
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
  val source: PositionSourceDomain = PositionSourceDomain.MANUAL,
  val createdAt: LocalDateTime? = null,
  val updatedAt: LocalDateTime? = null,
) {
  /**
   * Calculate position size (total value)
   */
  val positionSize: Double
    get() =
      if (instrumentType == InstrumentTypeDomain.OPTION) {
        averageEntryPrice * (currentContracts ?: currentQuantity) * multiplier
      } else {
        averageEntryPrice * currentQuantity
      }

  /**
   * Check if position is open
   */
  val isOpen: Boolean
    get() = status == PositionStatusDomain.OPEN

  /**
   * Check if position is closed
   */
  val isClosed: Boolean
    get() = status == PositionStatusDomain.CLOSED

  /**
   * Check if position is from broker
   */
  val isBrokerImported: Boolean
    get() = source == PositionSourceDomain.BROKER

  /**
   * Check if position is part of a roll chain
   */
  val isRolled: Boolean
    get() = rolledToPositionId != null || parentPositionId != null
}

/**
 * Instrument type
 */
enum class InstrumentTypeDomain {
  STOCK,
  OPTION,
  LEVERAGED_ETF,
}

/**
 * Option type
 */
enum class OptionTypeDomain {
  CALL,
  PUT,
}

/**
 * Position status
 */
enum class PositionStatusDomain {
  OPEN,
  CLOSED,
}

/**
 * Position source
 */
enum class PositionSourceDomain {
  BROKER,
  MANUAL,
}
