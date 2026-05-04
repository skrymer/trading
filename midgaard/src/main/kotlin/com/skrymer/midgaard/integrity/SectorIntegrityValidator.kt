package com.skrymer.midgaard.integrity

import com.skrymer.midgaard.jooq.tables.references.QUOTES
import com.skrymer.midgaard.jooq.tables.references.SYMBOLS
import com.skrymer.midgaard.model.AssetType
import com.skrymer.midgaard.model.SectorMapping
import org.jooq.DSLContext
import org.springframework.stereotype.Component

/**
 * Sector-related data invariants. Each `checkI*` returns a single rolled-up
 * Violation when the invariant is broken (regardless of how many rows are
 * affected — the count + sample symbols carry the drill-down) or null when it
 * holds.
 *
 * - I1: `sector` must be in the canonical 11 GICS names OR null.
 * - I2: `sector_symbol` must be in the canonical 11 ETF tickers OR null.
 * - I3: `sector` and `sector_symbol` must agree (`SectorMapping.toSectorSymbol(sector) == sector_symbol`).
 * - I4: every delisted row (`delisted_at IS NOT NULL`) must have `sector IS NOT NULL` (V6 invariant).
 * - I5: every active row with at least one OHLCV bar must have `sector IS NOT NULL`
 *       (otherwise it's invisible to udgaard's sector breadth — informational, not a bug).
 */
@Component
class SectorIntegrityValidator(
    private val dsl: DSLContext,
) : DataIntegrityValidator {
    override val name: String = "SectorIntegrityValidator"

    override fun validate(): List<Violation> =
        listOfNotNull(
            checkI1NonCanonicalSector(),
            checkI2NonCanonicalSectorSymbol(),
            checkI3SectorSymbolConsistency(),
            checkI4DelistedHasSector(),
            checkI5ActiveWithOhlcvHasSector(),
        )

    private fun checkI1NonCanonicalSector(): Violation? {
        val offenders =
            dsl
                .select(SYMBOLS.SYMBOL)
                .from(SYMBOLS)
                .where(SYMBOLS.SECTOR.isNotNull.and(SYMBOLS.SECTOR.notIn(SectorMapping.canonicalNames())))
                .orderBy(SYMBOLS.SYMBOL)
                .fetch(SYMBOLS.SYMBOL)
                .filterNotNull()
        if (offenders.isEmpty()) return null
        return buildViolation(
            invariant = "I1",
            severity = Severity.CRITICAL,
            summary = "${offenders.size} symbols have sector not in canonical 11.",
            offenders = offenders,
        )
    }

    private fun checkI2NonCanonicalSectorSymbol(): Violation? {
        val offenders =
            dsl
                .select(SYMBOLS.SYMBOL)
                .from(SYMBOLS)
                .where(SYMBOLS.SECTOR_SYMBOL.isNotNull.and(SYMBOLS.SECTOR_SYMBOL.notIn(SectorMapping.canonicalSectorSymbols())))
                .orderBy(SYMBOLS.SYMBOL)
                .fetch(SYMBOLS.SYMBOL)
                .filterNotNull()
        if (offenders.isEmpty()) return null
        return buildViolation(
            invariant = "I2",
            severity = Severity.CRITICAL,
            summary = "${offenders.size} symbols have sector_symbol not in canonical 11 ETF tickers.",
            offenders = offenders,
        )
    }

    private fun checkI3SectorSymbolConsistency(): Violation? {
        val offenders =
            dsl
                .select(SYMBOLS.SYMBOL, SYMBOLS.SECTOR, SYMBOLS.SECTOR_SYMBOL)
                .from(SYMBOLS)
                .fetch { record ->
                    val symbol = record.value1()!!
                    val sector = record.value2()
                    val sectorSymbol = record.value3()
                    val expected = SectorMapping.toSectorSymbol(sector)
                    if (expected != sectorSymbol) symbol else null
                }.filterNotNull()
                .sorted()
        if (offenders.isEmpty()) return null
        return buildViolation(
            invariant = "I3",
            severity = Severity.CRITICAL,
            summary = "${offenders.size} symbols have inconsistent sector/sector_symbol pairing.",
            offenders = offenders,
        )
    }

    private fun checkI4DelistedHasSector(): Violation? {
        // STOCK only — ETFs/BOND_ETF/COMMODITY_ETF/LEVERAGED_ETF don't carry GICS sectors,
        // so a NULL sector on a non-STOCK row is correct, not a violation.
        val offenders =
            dsl
                .select(SYMBOLS.SYMBOL)
                .from(SYMBOLS)
                .where(SYMBOLS.ASSET_TYPE.eq(AssetType.STOCK.name))
                .and(SYMBOLS.DELISTED_AT.isNotNull)
                .and(SYMBOLS.SECTOR.isNull)
                .orderBy(SYMBOLS.SYMBOL)
                .fetch(SYMBOLS.SYMBOL)
                .filterNotNull()
        if (offenders.isEmpty()) return null
        return buildViolation(
            invariant = "I4",
            severity = Severity.HIGH,
            summary = "${offenders.size} delisted STOCK symbols have sector IS NULL (expected V6 baseline).",
            offenders = offenders,
        )
    }

    private fun checkI5ActiveWithOhlcvHasSector(): Violation? {
        // EXISTS subquery on quotes — cheaper than a join when most active stocks have data.
        // STOCK only — ETFs don't carry GICS sectors so their NULL sector is intentional, not a leak.
        val offenders =
            dsl
                .select(SYMBOLS.SYMBOL)
                .from(SYMBOLS)
                .where(SYMBOLS.ASSET_TYPE.eq(AssetType.STOCK.name))
                .and(SYMBOLS.DELISTED_AT.isNull)
                .and(SYMBOLS.SECTOR.isNull)
                .andExists(
                    dsl.selectOne().from(QUOTES).where(QUOTES.SYMBOL.eq(SYMBOLS.SYMBOL)),
                ).orderBy(SYMBOLS.SYMBOL)
                .fetch(SYMBOLS.SYMBOL)
                .filterNotNull()
        if (offenders.isEmpty()) return null
        return buildViolation(
            invariant = "I5",
            severity = Severity.MEDIUM,
            summary = "${offenders.size} active symbols with OHLCV bars have sector IS NULL — invisible to udgaard sector breadth.",
            offenders = offenders,
        )
    }

    private fun buildViolation(
        invariant: String,
        severity: Severity,
        summary: String,
        offenders: List<String>,
    ): Violation {
        val samples = offenders.take(SAMPLE_LIMIT)
        return Violation(
            validator = name,
            invariant = invariant,
            severity = severity,
            description = "$summary Samples: ${samples.joinToString(", ")}",
            count = offenders.size,
            sampleSymbols = samples,
        )
    }

    companion object {
        private const val SAMPLE_LIMIT = 10
    }
}
