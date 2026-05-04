package com.skrymer.midgaard.integrity

import java.time.LocalDateTime

/**
 * Rolled-up: one Violation per (validator, invariant) tuple per validation run,
 * regardless of how many rows are affected. `count` + `sampleSymbols` carry the
 * drill-down info; the full affected-symbol list is recoverable via SQL when
 * needed (the validator's underlying query is documented in its KDoc).
 */
data class Violation(
    val validator: String,
    val invariant: String,
    val severity: Severity,
    val description: String,
    val count: Int,
    val sampleSymbols: List<String> = emptyList(),
    val detectedAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Triage gradation. Surfaced in the UI via color (red CRITICAL → gray LOW) and
 * sort order (CRITICAL first). No behavioural coupling — Severity does not affect
 * HTTP responses, log levels, or refresh outcomes.
 *
 * **Declaration order is load-bearing**: `ViolationRepository.findAll()` and
 * `UiController.integrity` both sort by `Severity.ordinal`, so reordering this
 * enum reorders the UI / API output. Pinned by `SeverityOrderTest`.
 */
enum class Severity { CRITICAL, HIGH, MEDIUM, LOW }
