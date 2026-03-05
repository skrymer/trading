package com.skrymer.udgaard.portfolio.repository

import com.skrymer.udgaard.jooq.tables.references.FOREX_LOTS
import com.skrymer.udgaard.portfolio.model.ForexLot
import com.skrymer.udgaard.portfolio.model.ForexLotStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class ForexLotJooqRepository(
  private val dsl: DSLContext,
) {
  fun save(lot: ForexLot): ForexLot {
    if (lot.id == null) {
      val record =
        dsl
          .insertInto(FOREX_LOTS)
          .set(FOREX_LOTS.PORTFOLIO_ID, lot.portfolioId)
          .set(FOREX_LOTS.ACQUISITION_DATE, lot.acquisitionDate)
          .set(FOREX_LOTS.CURRENCY, lot.currency)
          .set(FOREX_LOTS.QUANTITY, lot.quantity.toBigDecimal())
          .set(FOREX_LOTS.REMAINING_QUANTITY, lot.remainingQuantity.toBigDecimal())
          .set(FOREX_LOTS.COST_RATE, lot.costRate.toBigDecimal())
          .set(FOREX_LOTS.COST_BASIS, lot.costBasis.toBigDecimal())
          .set(FOREX_LOTS.SOURCE_EXECUTION_ID, lot.sourceExecutionId)
          .set(FOREX_LOTS.SOURCE_DESCRIPTION, lot.sourceDescription)
          .set(FOREX_LOTS.STATUS, lot.status.name)
          .returningResult(FOREX_LOTS.ID)
          .fetchOne()

      val newId = record?.getValue(FOREX_LOTS.ID) ?: throw IllegalStateException("Failed to insert forex lot")
      return lot.copy(id = newId)
    } else {
      dsl
        .update(FOREX_LOTS)
        .set(FOREX_LOTS.REMAINING_QUANTITY, lot.remainingQuantity.toBigDecimal())
        .set(FOREX_LOTS.STATUS, lot.status.name)
        .where(FOREX_LOTS.ID.eq(lot.id))
        .execute()
      return lot
    }
  }

  fun findOpenLotsByPortfolioFIFO(portfolioId: Long): List<ForexLot> =
    dsl
      .selectFrom(FOREX_LOTS)
      .where(FOREX_LOTS.PORTFOLIO_ID.eq(portfolioId))
      .and(FOREX_LOTS.STATUS.eq(ForexLotStatus.OPEN.name))
      .orderBy(FOREX_LOTS.ACQUISITION_DATE.asc(), FOREX_LOTS.ID.asc())
      .fetch()
      .map { toDomain(it.into(com.skrymer.udgaard.jooq.tables.pojos.ForexLots::class.java)) }

  fun findByPortfolioId(portfolioId: Long): List<ForexLot> =
    dsl
      .selectFrom(FOREX_LOTS)
      .where(FOREX_LOTS.PORTFOLIO_ID.eq(portfolioId))
      .orderBy(FOREX_LOTS.ACQUISITION_DATE.asc())
      .fetch()
      .map { toDomain(it.into(com.skrymer.udgaard.jooq.tables.pojos.ForexLots::class.java)) }

  private fun toDomain(pojo: com.skrymer.udgaard.jooq.tables.pojos.ForexLots): ForexLot =
    ForexLot(
      id = pojo.id,
      portfolioId = pojo.portfolioId,
      acquisitionDate = pojo.acquisitionDate,
      currency = pojo.currency ?: "USD",
      quantity = pojo.quantity.toDouble(),
      remainingQuantity = pojo.remainingQuantity.toDouble(),
      costRate = pojo.costRate.toDouble(),
      costBasis = pojo.costBasis.toDouble(),
      sourceExecutionId = pojo.sourceExecutionId,
      sourceDescription = pojo.sourceDescription,
      status = ForexLotStatus.valueOf(pojo.status ?: "OPEN"),
      createdAt = pojo.createdAt,
    )
}
