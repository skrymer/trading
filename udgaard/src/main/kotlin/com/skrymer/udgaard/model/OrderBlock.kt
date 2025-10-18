package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 *
 */
data class OrderBlock(
  val low: Double = 0.0,
  val high: Double = 0.0,
  val startDate: LocalDate,
  val endDate: LocalDate?,
  val orderBlockType: OrderBlockType
)

enum class OrderBlockType {
  BEARISH, BULLISH
}