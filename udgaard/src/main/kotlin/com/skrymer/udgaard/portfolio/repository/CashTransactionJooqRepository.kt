package com.skrymer.udgaard.portfolio.repository

import com.skrymer.udgaard.jooq.tables.references.CASH_TRANSACTIONS
import com.skrymer.udgaard.portfolio.model.CashTransaction
import com.skrymer.udgaard.portfolio.model.CashTransactionSource
import com.skrymer.udgaard.portfolio.model.CashTransactionType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class CashTransactionJooqRepository(
  private val dsl: DSLContext,
) {
  fun save(tx: CashTransaction): CashTransaction {
    val record =
      dsl
        .insertInto(CASH_TRANSACTIONS)
        .set(CASH_TRANSACTIONS.PORTFOLIO_ID, tx.portfolioId)
        .set(CASH_TRANSACTIONS.TYPE, tx.type.name)
        .set(CASH_TRANSACTIONS.AMOUNT, tx.amount.toBigDecimal())
        .set(CASH_TRANSACTIONS.CURRENCY, tx.currency)
        .set(CASH_TRANSACTIONS.CONVERTED_AMOUNT, tx.convertedAmount?.toBigDecimal())
        .set(CASH_TRANSACTIONS.TRANSACTION_DATE, tx.transactionDate)
        .set(CASH_TRANSACTIONS.DESCRIPTION, tx.description)
        .set(CASH_TRANSACTIONS.FX_RATE_TO_BASE, tx.fxRateToBase?.toBigDecimal())
        .set(CASH_TRANSACTIONS.BROKER_TRANSACTION_ID, tx.brokerTransactionId)
        .set(CASH_TRANSACTIONS.SOURCE, tx.source.name)
        .returningResult(CASH_TRANSACTIONS.ID)
        .fetchOne()

    val newId = record?.getValue(CASH_TRANSACTIONS.ID)
      ?: throw IllegalStateException("Failed to insert cash transaction")
    return tx.copy(id = newId)
  }

  fun findByPortfolioId(portfolioId: Long): List<CashTransaction> =
    dsl
      .selectFrom(CASH_TRANSACTIONS)
      .where(CASH_TRANSACTIONS.PORTFOLIO_ID.eq(portfolioId))
      .orderBy(CASH_TRANSACTIONS.TRANSACTION_DATE.asc())
      .fetch()
      .map { toDomain(it.into(com.skrymer.udgaard.jooq.tables.pojos.CashTransactions::class.java)) }

  fun findByBrokerTransactionId(brokerTransactionId: String): CashTransaction? =
    dsl
      .selectFrom(CASH_TRANSACTIONS)
      .where(CASH_TRANSACTIONS.BROKER_TRANSACTION_ID.eq(brokerTransactionId))
      .fetchOne()
      ?.let { toDomain(it.into(com.skrymer.udgaard.jooq.tables.pojos.CashTransactions::class.java)) }

  fun getNetCashFlow(portfolioId: Long): Double {
    val deposits = sumByType(portfolioId, CashTransactionType.DEPOSIT)
    val withdrawals = sumByType(portfolioId, CashTransactionType.WITHDRAWAL)
    return deposits - withdrawals
  }

  fun sumByType(portfolioId: Long, type: CashTransactionType): Double =
    dsl
      .select(
        DSL.sum(
          DSL.coalesce(CASH_TRANSACTIONS.CONVERTED_AMOUNT, CASH_TRANSACTIONS.AMOUNT),
        ),
      ).from(CASH_TRANSACTIONS)
      .where(CASH_TRANSACTIONS.PORTFOLIO_ID.eq(portfolioId))
      .and(CASH_TRANSACTIONS.TYPE.eq(type.name))
      .fetchOne(0, Double::class.java) ?: 0.0

  private fun toDomain(pojo: com.skrymer.udgaard.jooq.tables.pojos.CashTransactions): CashTransaction =
    CashTransaction(
      id = pojo.id,
      portfolioId = pojo.portfolioId,
      type = CashTransactionType.valueOf(pojo.type),
      amount = pojo.amount.toDouble(),
      currency = pojo.currency ?: "USD",
      convertedAmount = pojo.convertedAmount?.toDouble(),
      transactionDate = pojo.transactionDate,
      description = pojo.description,
      fxRateToBase = pojo.fxRateToBase?.toDouble(),
      brokerTransactionId = pojo.brokerTransactionId,
      source = CashTransactionSource.valueOf(pojo.source ?: "MANUAL"),
      createdAt = pojo.createdAt,
    )
}
