package com.skrymer.midgaard.service.sector

/**
 * Canonicalizes raw provider sector strings to one of the 11 UPPERCASE GICS sector
 * names (matching `SectorMapping.SECTOR_TO_SYMBOL`'s key set), or null when the
 * sector cannot be reliably classified.
 *
 * Providers (EODHD, AlphaVantage) return sector names in inconsistent spellings
 * (`Financial Services` / `Financials` / `Financial`) and bucket unclassifiable
 * symbols as `Other` / `NONE`. Without normalization, the same GICS sector ends up
 * stored under multiple distinct strings in `symbols.sector`, and `Other` symbols
 * leak into downstream breadth calculations as a pseudo-12th-sector.
 *
 * Returns null for unmappable input so `SectorMapping.toSectorSymbol(null)` cleanly
 * yields a null `sector_symbol` — this then propagates to udgaard's
 * `STOCKS.SECTOR IS NULL` filter and the symbol is excluded from sector breadth
 * (the correct behaviour — better to exclude than to fake a category).
 */
object SectorNormalizer {
    private val CANONICAL =
        setOf(
            "TECHNOLOGY",
            "FINANCIAL SERVICES",
            "HEALTHCARE",
            "ENERGY",
            "INDUSTRIALS",
            "CONSUMER CYCLICAL",
            "CONSUMER DEFENSIVE",
            "COMMUNICATION SERVICES",
            "BASIC MATERIALS",
            "REAL ESTATE",
            "UTILITIES",
        )

    // Provider variants observed in PRD (datastore.symbols, 2026-05-04). All currently
    // appear ONLY on delisted rows, which the immutability rule keeps frozen post-V8 —
    // so this map is defensive code that's never triggered today. Kept for resilience
    // against future provider drift; new variants surface via the warn-log in
    // IngestionService and get added here as one-line entries.
    private val VARIANTS =
        mapOf(
            "FINANCIALS" to "FINANCIAL SERVICES",
            "FINANCIAL" to "FINANCIAL SERVICES",
            "MATERIALS" to "BASIC MATERIALS",
        )

    // Provider strings that explicitly mean "not classified". Treated as null.
    private val UNCLASSIFIED = setOf("OTHER", "NONE", "")

    fun canonicalize(rawSector: String?): String? {
        val upper = rawSector?.trim()?.uppercase() ?: return null
        return when {
            upper in UNCLASSIFIED -> null
            upper in CANONICAL -> upper
            else -> VARIANTS[upper]
        }
    }
}
