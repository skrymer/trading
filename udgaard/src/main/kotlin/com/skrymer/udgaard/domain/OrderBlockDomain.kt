package com.skrymer.udgaard.domain

import java.time.LocalDate

/**
 * Domain model for OrderBlock (Hibernate-independent)
 */
data class OrderBlockDomain(
  val low: Double = 0.0,
  val high: Double = 0.0,
  val startDate: LocalDate = LocalDate.now(),
  var endDate: LocalDate? = null,
  val orderBlockType: OrderBlockType = OrderBlockType.BEARISH,
  val volume: Long = 0L,
  val volumeStrength: Double = 0.0,
  val sensitivity: OrderBlockSensitivity? = null,
  val rateOfChange: Double = 0.0,
)

enum class OrderBlockType {
  BEARISH,
  BULLISH,
}

enum class OrderBlockSensitivity {
  HIGH, // More order blocks detected (lower threshold, e.g., 28%)
  LOW, // Fewer, stronger order blocks (higher threshold, e.g., 50%)
}
