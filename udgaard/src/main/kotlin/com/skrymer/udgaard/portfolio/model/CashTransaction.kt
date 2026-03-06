package com.skrymer.udgaard.portfolio.model

import java.time.LocalDate
import java.time.LocalDateTime

data class CashTransaction(
  val id: Long? = null,
  val portfolioId: Long,
  val type: CashTransactionType,
  val amount: Double,
  val currency: String = "USD",
  val convertedAmount: Double? = null,
  val transactionDate: LocalDate,
  val description: String? = null,
  val fxRateToBase: Double? = null,
  val brokerTransactionId: String? = null,
  val source: CashTransactionSource = CashTransactionSource.MANUAL,
  val createdAt: LocalDateTime? = null,
)

enum class CashTransactionType { DEPOSIT, WITHDRAWAL }

enum class CashTransactionSource { MANUAL, BROKER }
