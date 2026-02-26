package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.EARNINGS
import com.skrymer.midgaard.model.Earning
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class EarningsRepository(
    private val dsl: DSLContext,
) {
    fun findBySymbol(symbol: String): List<Earning> =
        dsl
            .selectFrom(EARNINGS)
            .where(EARNINGS.SYMBOL.eq(symbol))
            .orderBy(EARNINGS.FISCAL_DATE_ENDING.asc())
            .fetch()
            .map { record ->
                Earning(
                    symbol = record.symbol,
                    fiscalDateEnding = record.fiscalDateEnding,
                    reportedDate = record.reportedDate,
                    reportedEps = record.reportedEps,
                    estimatedEps = record.estimatedEps,
                    surprise = record.surprise,
                    surprisePercentage = record.surprisePercentage,
                    reportTime = record.reportTime,
                )
            }

    fun upsertEarnings(earnings: List<Earning>) {
        if (earnings.isEmpty()) return

        dsl.batched { ctx ->
            for (earning in earnings) {
                ctx
                    .dsl()
                    .insertInto(EARNINGS)
                    .set(EARNINGS.SYMBOL, earning.symbol)
                    .set(EARNINGS.FISCAL_DATE_ENDING, earning.fiscalDateEnding)
                    .set(EARNINGS.REPORTED_DATE, earning.reportedDate)
                    .set(EARNINGS.REPORTED_EPS, earning.reportedEps)
                    .set(EARNINGS.ESTIMATED_EPS, earning.estimatedEps)
                    .set(EARNINGS.SURPRISE, earning.surprise)
                    .set(EARNINGS.SURPRISE_PERCENTAGE, earning.surprisePercentage)
                    .set(EARNINGS.REPORT_TIME, earning.reportTime)
                    .onConflict(EARNINGS.SYMBOL, EARNINGS.FISCAL_DATE_ENDING)
                    .doUpdate()
                    .set(EARNINGS.REPORTED_DATE, earning.reportedDate)
                    .set(EARNINGS.REPORTED_EPS, earning.reportedEps)
                    .set(EARNINGS.ESTIMATED_EPS, earning.estimatedEps)
                    .set(EARNINGS.SURPRISE, earning.surprise)
                    .set(EARNINGS.SURPRISE_PERCENTAGE, earning.surprisePercentage)
                    .set(EARNINGS.REPORT_TIME, earning.reportTime)
                    .execute()
            }
        }
    }
}
