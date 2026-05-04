package com.skrymer.midgaard.integrity

/**
 * Implementations are Spring `@Component`s; `DataIntegrityService` discovers them
 * via constructor injection of `List<DataIntegrityValidator>`. A new validator is
 * a single-file addition that auto-registers.
 *
 * Each `validate()` returns rolled-up violations: one Violation per (validator,
 * invariant) tuple, with `count` + `sampleSymbols` carrying the drill-down.
 */
interface DataIntegrityValidator {
    val name: String

    fun validate(): List<Violation>
}
