package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.SPLITS
import com.skrymer.midgaard.model.Split
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Persistence for a symbol's split history — the corporate actions the cumulative split factor k(t) is
 * built from (ADR 0027). Replaced wholesale per symbol on ingest, mirroring the quote/fundamentals pattern.
 */
@Repository
class SplitRepository(
    private val dsl: DSLContext,
) {
    fun findBySymbol(symbol: String): List<Split> =
        dsl
            .selectFrom(SPLITS)
            .where(SPLITS.SYMBOL.eq(symbol))
            .orderBy(SPLITS.EX_DATE.asc())
            .fetch()
            .map { Split(symbol = it.symbol, exDate = it.exDate, ratio = it.ratio) }

    /** Delete-then-insert this symbol's splits, so a re-ingest reflects exactly the provider's current set. */
    @Transactional
    fun replaceForSymbol(
        symbol: String,
        splits: List<Split>,
    ) {
        require(splits.all { it.symbol == symbol }) { "All splits must belong to $symbol" }
        dsl.deleteFrom(SPLITS).where(SPLITS.SYMBOL.eq(symbol)).execute()
        if (splits.isEmpty()) return
        dsl.batched { ctx ->
            for (split in splits) {
                ctx
                    .dsl()
                    .insertInto(SPLITS)
                    .set(SPLITS.SYMBOL, split.symbol)
                    .set(SPLITS.EX_DATE, split.exDate)
                    .set(SPLITS.RATIO, split.ratio)
                    .execute()
            }
        }
    }
}
