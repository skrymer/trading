package com.skrymer.udgaard.portfolio.model

import java.time.LocalDate
import java.time.LocalDateTime

data class ForexLot(
  val id: Long? = null,
  val portfolioId: Long,
  val acquisitionDate: LocalDate,
  val currency: String = "USD",
  val quantity: Double,
  val remainingQuantity: Double,
  val costRate: Double,
  val costBasis: Double,
  val sourceExecutionId: Long?,
  val sourceDescription: String?,
  val status: ForexLotStatus = ForexLotStatus.OPEN,
  val createdAt: LocalDateTime? = null,
)

enum class ForexLotStatus { OPEN, EXHAUSTED }
