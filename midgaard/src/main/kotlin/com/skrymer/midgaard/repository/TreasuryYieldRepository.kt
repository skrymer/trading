package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.TREASURY_YIELDS
import com.skrymer.midgaard.model.TreasuryYield
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class TreasuryYieldRepository(
    private val dsl: DSLContext,
) {
    fun findByMaturity(maturity: String): List<TreasuryYield> =
        dsl
            .selectFrom(TREASURY_YIELDS)
            .where(TREASURY_YIELDS.MATURITY.eq(maturity))
            .orderBy(TREASURY_YIELDS.YIELD_DATE.asc())
            .fetch()
            .map { record ->
                TreasuryYield(
                    maturity = record.maturity,
                    date = record.yieldDate,
                    yieldPct = record.yieldPct.toDouble(),
                )
            }

    @Transactional
    fun upsert(yields: List<TreasuryYield>) {
        if (yields.isEmpty()) return

        dsl.batched { ctx ->
            for (y in yields) {
                ctx
                    .dsl()
                    .insertInto(TREASURY_YIELDS)
                    .set(TREASURY_YIELDS.MATURITY, y.maturity)
                    .set(TREASURY_YIELDS.YIELD_DATE, y.date)
                    .set(TREASURY_YIELDS.YIELD_PCT, y.yieldPct.toBigDecimal())
                    .onConflict(TREASURY_YIELDS.MATURITY, TREASURY_YIELDS.YIELD_DATE)
                    .doUpdate()
                    .set(TREASURY_YIELDS.YIELD_PCT, y.yieldPct.toBigDecimal())
                    .execute()
            }
        }
    }
}
