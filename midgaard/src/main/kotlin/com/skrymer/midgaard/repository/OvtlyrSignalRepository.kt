package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.OVTLYR_SIGNALS
import com.skrymer.midgaard.model.OvtlyrSignal
import com.skrymer.midgaard.model.OvtlyrSignalType
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class OvtlyrSignalRepository(
    private val dsl: DSLContext,
) {
    fun findBySymbol(symbol: String): List<OvtlyrSignal> =
        dsl
            .selectFrom(OVTLYR_SIGNALS)
            .where(OVTLYR_SIGNALS.SYMBOL.eq(symbol))
            .orderBy(OVTLYR_SIGNALS.SIGNAL_DATE.asc())
            .fetch()
            .map { record ->
                OvtlyrSignal(
                    symbol = record.symbol,
                    signalDate = record.signalDate,
                    signal = OvtlyrSignalType.valueOf(record.signal),
                )
            }

    /** Symbols that already have at least one stored signal — used to skip them on a backfill re-run. */
    fun findDistinctSymbols(): Set<String> =
        dsl
            .selectDistinct(OVTLYR_SIGNALS.SYMBOL)
            .from(OVTLYR_SIGNALS)
            .fetchSet(OVTLYR_SIGNALS.SYMBOL)
            .filterNotNull()
            .toSet()

    @Transactional
    fun upsert(signals: List<OvtlyrSignal>) {
        if (signals.isEmpty()) return

        dsl.batched { ctx ->
            for (signal in signals) {
                ctx
                    .dsl()
                    .insertInto(OVTLYR_SIGNALS)
                    .set(OVTLYR_SIGNALS.SYMBOL, signal.symbol)
                    .set(OVTLYR_SIGNALS.SIGNAL_DATE, signal.signalDate)
                    .set(OVTLYR_SIGNALS.SIGNAL, signal.signal.name)
                    .onConflict(OVTLYR_SIGNALS.SYMBOL, OVTLYR_SIGNALS.SIGNAL_DATE)
                    .doUpdate()
                    .set(OVTLYR_SIGNALS.SIGNAL, signal.signal.name)
                    .execute()
            }
        }
    }
}
