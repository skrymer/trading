package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.TREASURY_YIELDS
import com.skrymer.midgaard.model.TreasuryYield
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** How many yields are stored for a maturity and the most-recent date — drives the ingestion-page status. */
data class TreasuryYieldStatus(
    val count: Int,
    val latestDate: LocalDate?,
)

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

    fun status(maturity: String): TreasuryYieldStatus {
        val record =
            dsl
                .select(DSL.count(), DSL.max(TREASURY_YIELDS.YIELD_DATE))
                .from(TREASURY_YIELDS)
                .where(TREASURY_YIELDS.MATURITY.eq(maturity))
                .fetchOne()
        return TreasuryYieldStatus(count = record?.value1() ?: 0, latestDate = record?.value2())
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
