package com.skrymer.udgaard.portfolio.repository

import com.skrymer.udgaard.jooq.tables.references.FOREX_DISPOSALS
import com.skrymer.udgaard.portfolio.model.ForexDisposal
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ForexDisposalJooqRepository(
  private val dsl: DSLContext,
) {
  fun save(disposal: ForexDisposal): ForexDisposal {
    val record =
      dsl
        .insertInto(FOREX_DISPOSALS)
        .set(FOREX_DISPOSALS.PORTFOLIO_ID, disposal.portfolioId)
        .set(FOREX_DISPOSALS.LOT_ID, disposal.lotId)
        .set(FOREX_DISPOSALS.DISPOSAL_DATE, disposal.disposalDate)
        .set(FOREX_DISPOSALS.QUANTITY, disposal.quantity.toBigDecimal())
        .set(FOREX_DISPOSALS.COST_RATE, disposal.costRate.toBigDecimal())
        .set(FOREX_DISPOSALS.DISPOSAL_RATE, disposal.disposalRate.toBigDecimal())
        .set(FOREX_DISPOSALS.COST_BASIS_AUD, disposal.costBasisAud.toBigDecimal())
        .set(FOREX_DISPOSALS.PROCEEDS_AUD, disposal.proceedsAud.toBigDecimal())
        .set(FOREX_DISPOSALS.REALIZED_FX_PNL, disposal.realizedFxPnl.toBigDecimal())
        .set(FOREX_DISPOSALS.SOURCE_EXECUTION_ID, disposal.sourceExecutionId)
        .returningResult(FOREX_DISPOSALS.ID)
        .fetchOne()

    val newId = record?.getValue(FOREX_DISPOSALS.ID) ?: throw IllegalStateException("Failed to insert forex disposal")
    return disposal.copy(id = newId)
  }

  fun findByPortfolioId(portfolioId: Long): List<ForexDisposal> =
    dsl
      .selectFrom(FOREX_DISPOSALS)
      .where(FOREX_DISPOSALS.PORTFOLIO_ID.eq(portfolioId))
      .orderBy(FOREX_DISPOSALS.DISPOSAL_DATE.asc())
      .fetch()
      .map { toDomain(it.into(com.skrymer.udgaard.jooq.tables.pojos.ForexDisposals::class.java)) }

  fun findByPortfolioIdAndDateRange(
    portfolioId: Long,
    startDate: LocalDate,
    endDate: LocalDate,
  ): List<ForexDisposal> =
    dsl
      .selectFrom(FOREX_DISPOSALS)
      .where(FOREX_DISPOSALS.PORTFOLIO_ID.eq(portfolioId))
      .and(FOREX_DISPOSALS.DISPOSAL_DATE.between(startDate, endDate))
      .orderBy(FOREX_DISPOSALS.DISPOSAL_DATE.asc())
      .fetch()
      .map { toDomain(it.into(com.skrymer.udgaard.jooq.tables.pojos.ForexDisposals::class.java)) }

  private fun toDomain(pojo: com.skrymer.udgaard.jooq.tables.pojos.ForexDisposals): ForexDisposal =
    ForexDisposal(
      id = pojo.id,
      portfolioId = pojo.portfolioId,
      lotId = pojo.lotId,
      disposalDate = pojo.disposalDate,
      quantity = pojo.quantity.toDouble(),
      costRate = pojo.costRate.toDouble(),
      disposalRate = pojo.disposalRate.toDouble(),
      costBasisAud = pojo.costBasisAud.toDouble(),
      proceedsAud = pojo.proceedsAud.toDouble(),
      realizedFxPnl = pojo.realizedFxPnl.toDouble(),
      sourceExecutionId = pojo.sourceExecutionId,
      createdAt = pojo.createdAt,
    )
}
