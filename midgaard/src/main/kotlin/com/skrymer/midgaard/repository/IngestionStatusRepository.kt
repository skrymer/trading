package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.INGESTION_STATUS
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
