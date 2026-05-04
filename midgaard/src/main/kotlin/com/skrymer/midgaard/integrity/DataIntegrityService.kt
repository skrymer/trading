package com.skrymer.midgaard.integrity

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Orchestrates all `DataIntegrityValidator` beans. `runAll()` produces a fresh
 * snapshot — every validator runs, results are persisted via truncate-and-replace.
 *
 * Spring auto-injects `List<DataIntegrityValidator>` so adding a new validator is
 * a one-class addition. No registration needed.
 */
@Service
class DataIntegrityService(
    private val validators: List<DataIntegrityValidator>,
    private val repository: ViolationRepository,
) {
    private val logger = LoggerFactory.getLogger(DataIntegrityService::class.java)

    /**
     * Runs every registered validator and persists the resulting snapshot.
     *
     * `@Synchronized` serializes concurrent invocations so the truncate-and-replace
     * in `ViolationRepository` is a single logical snapshot per call. Concrete races
     * the lock prevents: a manual "Re-run validators" UI click while a bulk ingest's
     * auto-run is finishing (both call this method from different threads). Single-user
     * app — contention is irrelevant; correctness wins.
     */
    @Synchronized
    fun runAll(): List<Violation> {
        val all =
            validators.flatMap { v ->
                runCatching { v.validate() }.getOrElse { e ->
                    logger.error("Validator ${v.name} threw: ${e.message}", e)
                    emptyList()
                }
            }
        repository.replaceAll(all)
        logger.info("Data integrity check complete: ${all.size} violations across ${validators.size} validators")
        return all
    }

    /** Returns the persisted snapshot from the last `runAll()` call. */
    fun latestViolations(): List<Violation> = repository.findAll()

    /** Cheap count for UI banners — avoids loading all violations to compute size. */
    fun violationCount(): Int = repository.count()
}
