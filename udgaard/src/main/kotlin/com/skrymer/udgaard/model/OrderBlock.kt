package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 *
 */
data class OrderBlock(
  val startDate: LocalDate,
  val endDate: LocalDate?,
  val orderBlockType: OrderBlockType
)

enum class OrderBlockType {
  BEARISH, BULLISH
}