package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 * Represents an order block - a price zone where institutional traders placed large orders
 *
 * @param low The lowest price of the order block
 * @param high The highest price of the order block
 * @param startDate When the order block was formed
 * @param endDate When the order block was mitigated/invalidated (null if still active)
 * @param orderBlockType Type of order block (BULLISH or BEARISH)
 * @param source Source of the order block data (OVTLYR or CALCULATED)
 * @param volume Trading volume when the order block was formed
 * @param volumeStrength Relative volume strength (volume / average volume)
 * @param sensitivity Sensitivity level used for detection (HIGH or LOW)
 * @param rateOfChange The rate of change percentage that triggered this order block
 */
data class OrderBlock(
  val low: Double = 0.0,
  val high: Double = 0.0,
  val startDate: LocalDate,
  val endDate: LocalDate? = null,
  val orderBlockType: OrderBlockType,
  val source: OrderBlockSource = OrderBlockSource.OVTLYR,
  val volume: Long = 0L,
  val volumeStrength: Double = 0.0,
  val sensitivity: OrderBlockSensitivity? = null,
  val rateOfChange: Double = 0.0
)

enum class OrderBlockType {
  BEARISH, BULLISH
}

enum class OrderBlockSource {
  OVTLYR,      // Order block from Ovtlyr data provider
  CALCULATED   // Order block calculated using ROC algorithm
}

enum class OrderBlockSensitivity {
  HIGH,   // More order blocks detected (lower threshold, e.g., 28%)
  LOW     // Fewer, stronger order blocks (higher threshold, e.g., 50%)
}