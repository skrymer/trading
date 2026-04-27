package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.INGESTION_STATUS
import com.skrymer.midgaard.jooq.tables.references.SYMBOLS
import com.skrymer.midgaard.model.IngestionState
import com.skrymer.midgaard.model.IngestionStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class IngestionStatusRepository(
    private val dsl: DSLContext,
) {
    fun findAll(): List<IngestionStatus> =
        dsl
            .selectFrom(INGESTION_STATUS)
            .orderBy(INGESTION_STATUS.SYMBOL.asc())
            .fetch()
            .map { it.toModel() }

    fun findBySymbol(symbol: String): IngestionStatus? =
        dsl
            .selectFrom(INGESTION_STATUS)
            .where(INGESTION_STATUS.SYMBOL.eq(symbol))
            .fetchOne()
            ?.toModel()

    fun findByStatus(status: IngestionState): List<IngestionStatus> =
        dsl
            .selectFrom(INGESTION_STATUS)
            .where(INGESTION_STATUS.STATUS.eq(status.name))
            .orderBy(INGESTION_STATUS.SYMBOL.asc())
            .fetch()
            .map { it.toModel() }

    /**
     * Like `findByStatus` but excludes symbols that have a `delisted_at` set.
     * Used by the daily-update path so we don't waste EODHD weighted calls
     * fetching empty bar lists for tickers that no longer trade.
     *
     * Symbols absent from the `symbols` table (orphaned `ingestion_status`
     * rows) are still returned — outer-join + null-delisted-at predicate.
     */
    fun findActiveByStatus(status: IngestionState): List<IngestionStatus> =
        dsl
            .select(*INGESTION_STATUS.fields())
            .from(INGESTION_STATUS)
            .leftJoin(SYMBOLS)
            .on(SYMBOLS.SYMBOL.eq(INGESTION_STATUS.SYMBOL))
            .where(INGESTION_STATUS.STATUS.eq(status.name))
            .and(SYMBOLS.DELISTED_AT.isNull)
            .orderBy(INGESTION_STATUS.SYMBOL.asc())
            .fetch()
            .map { it.toModel() }

    fun upsert(
        symbol: String,
        barCount: Int,
        lastBarDate: LocalDate?,
        status: IngestionState,
    ) {
        val now = LocalDateTime.now()
        dsl
            .insertInto(INGESTION_STATUS)
            .set(INGESTION_STATUS.SYMBOL, symbol)
            .set(INGESTION_STATUS.BAR_COUNT, barCount)
            .set(INGESTION_STATUS.LAST_BAR_DATE, lastBarDate)
            .set(INGESTION_STATUS.LAST_INGESTED, now)
            .set(INGESTION_STATUS.STATUS, status.name)
            .onConflict(INGESTION_STATUS.SYMBOL)
            .doUpdate()
            .set(INGESTION_STATUS.BAR_COUNT, barCount)
            .set(INGESTION_STATUS.LAST_BAR_DATE, lastBarDate)
            .set(INGESTION_STATUS.LAST_INGESTED, now)
            .set(INGESTION_STATUS.STATUS, status.name)
            .execute()
    }

    fun countByStatus(status: IngestionState): Int = dsl.fetchCount(INGESTION_STATUS, INGESTION_STATUS.STATUS.eq(status.name))

    fun countTotal(): Int = dsl.fetchCount(INGESTION_STATUS)

    /**
     * Returns symbols that haven't successfully completed initial ingest yet:
     *  - rows where `ingestion_status.status != COMPLETE` (PENDING, FAILED, PARTIAL)
     *  - symbols present in `symbols` but absent from `ingestion_status` (e.g. just
     *    added via the V6 delisted-bootstrap migration)
     *
     * Used by the "Retry Not Complete" path so a single button can backfill new
     * symbols + retry past failures without re-ingesting the whole universe.
     */
    fun findNotCompleteSymbols(): List<String> =
        dsl
            .select(SYMBOLS.SYMBOL)
            .from(SYMBOLS)
            .leftJoin(INGESTION_STATUS)
            .on(INGESTION_STATUS.SYMBOL.eq(SYMBOLS.SYMBOL))
            .where(
                INGESTION_STATUS.STATUS
                    .ne(IngestionState.COMPLETE.name)
                    .or(INGESTION_STATUS.SYMBOL.isNull),
            ).orderBy(SYMBOLS.SYMBOL.asc())
            .fetch(SYMBOLS.SYMBOL)
            .filterNotNull()

    /** Count of symbols that haven't completed initial ingest yet — for UI display. */
    fun countNotComplete(): Int = findNotCompleteSymbols().size

    fun getLastUpdated(): LocalDateTime? =
        dsl
            .select(
                org.jooq.impl.DSL
                    .max(INGESTION_STATUS.LAST_INGESTED),
            ).from(INGESTION_STATUS)
            .fetchOne(0, LocalDateTime::class.java)

    private fun org.jooq.Record.toModel(): IngestionStatus =
        IngestionStatus(
            symbol = get(INGESTION_STATUS.SYMBOL)!!,
            barCount = get(INGESTION_STATUS.BAR_COUNT) ?: 0,
            lastBarDate = get(INGESTION_STATUS.LAST_BAR_DATE),
            lastIngested = get(INGESTION_STATUS.LAST_INGESTED),
            status =
                get(INGESTION_STATUS.STATUS)?.let { IngestionState.valueOf(it) }
                    ?: IngestionState.PENDING,
        )
}
