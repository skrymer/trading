package com.skrymer.midgaard.integrity

import com.skrymer.midgaard.jooq.tables.references.DATA_INTEGRITY_VIOLATIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class ViolationRepository(
    private val dsl: DSLContext,
) {
    /**
     * Truncate-all + insert in a single transaction. Every `runAll()` produces a
     * fresh snapshot; previous runs' violations are wiped. The transactional
     * wrapping is the correctness guarantee: without it, a concurrent caller
     * (e.g. a manual "Re-run validators" click while a bulk ingest's auto-run
     * is finishing) could interleave its delete between this call's delete and
     * insert and corrupt the snapshot.
     */
    fun replaceAll(violations: List<Violation>) {
        dsl.transaction { config ->
            val txDsl = config.dsl()
            txDsl.deleteFrom(DATA_INTEGRITY_VIOLATIONS).execute()
            if (violations.isEmpty()) return@transaction
            var insert =
                txDsl.insertInto(
                    DATA_INTEGRITY_VIOLATIONS,
                    DATA_INTEGRITY_VIOLATIONS.VALIDATOR,
                    DATA_INTEGRITY_VIOLATIONS.INVARIANT,
                    DATA_INTEGRITY_VIOLATIONS.SEVERITY,
                    DATA_INTEGRITY_VIOLATIONS.DESCRIPTION,
                    DATA_INTEGRITY_VIOLATIONS.COUNT,
                    DATA_INTEGRITY_VIOLATIONS.SAMPLE_SYMBOLS,
                    DATA_INTEGRITY_VIOLATIONS.DETECTED_AT,
                )
            violations.forEach { v ->
                insert =
                    insert.values(
                        v.validator,
                        v.invariant,
                        v.severity.name,
                        v.description,
                        v.count,
                        v.sampleSymbols.toTypedArray(),
                        v.detectedAt,
                    )
            }
            insert.execute()
        }
    }

    /**
     * Returns the latest snapshot, sorted CRITICAL first by enum ordinal. Sorting
     * happens in Kotlin (not SQL) because severity is stored as VARCHAR — alphabetical
     * SQL sort would yield CRITICAL, HIGH, LOW, MEDIUM (wrong: LOW before MEDIUM).
     */
    fun findAll(): List<Violation> =
        dsl
            .selectFrom(DATA_INTEGRITY_VIOLATIONS)
            .fetch { record ->
                Violation(
                    validator = requireNotNull(record.validator),
                    invariant = requireNotNull(record.invariant),
                    severity = Severity.valueOf(requireNotNull(record.severity)),
                    description = requireNotNull(record.description),
                    count = requireNotNull(record.count),
                    sampleSymbols = record.sampleSymbols?.filterNotNull() ?: emptyList(),
                    detectedAt = requireNotNull(record.detectedAt),
                )
            }.sortedWith(compareBy({ it.severity.ordinal }, { it.validator }, { it.invariant }))

    fun count(): Int = dsl.fetchCount(DATA_INTEGRITY_VIOLATIONS)
}
