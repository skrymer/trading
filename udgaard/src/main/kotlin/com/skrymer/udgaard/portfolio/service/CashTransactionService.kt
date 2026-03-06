package com.skrymer.udgaard.portfolio.service

import com.skrymer.udgaard.portfolio.model.CashTransaction
import com.skrymer.udgaard.portfolio.model.CashTransactionSource
import com.skrymer.udgaard.portfolio.model.CashTransactionType
import com.skrymer.udgaard.portfolio.repository.CashTransactionJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class CashTransactionService(
  private val repository: CashTransactionJooqRepository,
) {
  fun addCashTransaction(
    portfolioId: Long,
    type: CashTransactionType,
    amount: Double,
    transactionDate: LocalDate,
    description: String?,
    currency: String = "USD",
    convertedAmount: Double? = null,
    fxRateToBase: Double? = null,
    brokerTransactionId: String? = null,
    source: CashTransactionSource = CashTransactionSource.MANUAL,
  ): CashTransaction {
    if (brokerTransactionId != null) {
      val existing = repository.findByBrokerTransactionId(brokerTransactionId)
      if (existing != null) {
        logger.debug("Cash transaction already exists: brokerTxId={}", brokerTransactionId)
        return existing
      }
    }

    val tx = CashTransaction(
      portfolioId = portfolioId,
      type = type,
      amount = amount,
      currency = currency,
      convertedAmount = convertedAmount ?: amount,
      transactionDate = transactionDate,
      description = description,
      fxRateToBase = fxRateToBase,
      brokerTransactionId = brokerTransactionId,
      source = source,
    )

    val saved = repository.save(tx)
    logger.info(
      "Cash transaction saved: type={}, amount={} {}, converted={}, date={}",
      type,
      amount,
      currency,
      tx.convertedAmount,
      transactionDate,
    )
    return saved
  }

  fun getCashTransactions(portfolioId: Long): List<CashTransaction> =
    repository.findByPortfolioId(portfolioId)

  fun getNetCashFlow(portfolioId: Long): Double =
    repository.getNetCashFlow(portfolioId)

  fun getTotalDeposits(portfolioId: Long): Double =
    repository.sumByType(portfolioId, CashTransactionType.DEPOSIT)

  fun getTotalWithdrawals(portfolioId: Long): Double =
    repository.sumByType(portfolioId, CashTransactionType.WITHDRAWAL)

  companion object {
    private val logger = LoggerFactory.getLogger(CashTransactionService::class.java)
  }
}
