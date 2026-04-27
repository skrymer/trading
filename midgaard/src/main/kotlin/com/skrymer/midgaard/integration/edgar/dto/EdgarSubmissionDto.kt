package com.skrymer.midgaard.integration.edgar.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Subset of SEC EDGAR's `submissions/CIK##########.json` response that we need
 * for sector attribution. EDGAR returns far more (filing history, addresses,
 * etc.) — we ignore everything we don't read.
 *
 * EDGAR returns `sic` as a string ("6021"). We keep it as a string here and
 * expose a typed `sicCode` accessor for callers, sidestepping Jackson's
 * Kotlin-data-class-with-renamed-property edge cases.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EdgarSubmissionDto(
    val cik: String? = null,
    val sic: String? = null,
    val sicDescription: String? = null,
) {
    val sicCode: Int? get() = sic?.toIntOrNull()
}
