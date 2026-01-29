package com.skrymer.udgaard.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Execution domain model - represents a single broker transaction
 * Immutable record of what happened when
 */
data class ExecutionDomain(
  val id: Long?,
  val positionId: Long,
  val brokerTradeId: String?,
  val linkedBrokerTradeId: String?,
  /**
   * Quantity: signed integer
   * Positive = buy/open
   * Negative = sell/close
   */
  val quantity: Int,
  val price: Double,
  val executionDate: LocalDate,
  val executionTime: LocalTime?,
  val commission: Double?,
  val notes: String?,
  val createdAt: LocalDateTime? = null,
) {
  /**
   * Check if this is a buy execution
   */
  val isBuy: Boolean
    get() = quantity > 0

  /**
   * Check if this is a sell execution
   */
  val isSell: Boolean
    get() = quantity < 0

  /**
   * Get absolute quantity
   */
  val absoluteQuantity: Int
    get() = kotlin.math.abs(quantity)

  /**
   * Calculate total value (quantity * price)
   */
  val totalValue: Double
    get() = quantity * price

  /**
   * Calculate net cost (includes commission if available)
   */
  val netCost: Double
    get() = totalValue + (commission ?: 0.0)
}
