package com.skrymer.udgaard.portfolio.model

import java.time.LocalDate
import java.time.LocalDateTime

data class ForexDisposal(
  val id: Long? = null,
  val portfolioId: Long,
  val lotId: Long,
  val disposalDate: LocalDate,
  val quantity: Double,
  val costRate: Double,
  val disposalRate: Double,
  val costBasisAud: Double,
  val proceedsAud: Double,
  val realizedFxPnl: Double,
  val sourceExecutionId: Long?,
  val createdAt: LocalDateTime? = null,
)
