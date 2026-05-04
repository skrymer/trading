package com.skrymer.midgaard.service.sector

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SectorNormalizerTest {
    @Test
    fun `null input returns null`() {
        // Given: null raw sector
        // When / Then
        assertNull(SectorNormalizer.canonicalize(null))
    }

    @Test
    fun `empty string returns null`() {
        // Given: empty raw sector (provider returned an empty Sector field)
        // When / Then
        assertNull(SectorNormalizer.canonicalize(""))
    }

    @Test
    fun `whitespace-only string returns null`() {
        // Given: provider returned whitespace
        // When / Then
        assertNull(SectorNormalizer.canonicalize("   "))
    }

    @Test
    fun `each canonical name passes through unchanged`() {
        // Given: each of the 11 canonical UPPERCASE GICS names
        // When / Then: they pass through unchanged
        assertAll(
            { assertEquals("TECHNOLOGY", SectorNormalizer.canonicalize("TECHNOLOGY")) },
            { assertEquals("FINANCIAL SERVICES", SectorNormalizer.canonicalize("FINANCIAL SERVICES")) },
            { assertEquals("HEALTHCARE", SectorNormalizer.canonicalize("HEALTHCARE")) },
            { assertEquals("ENERGY", SectorNormalizer.canonicalize("ENERGY")) },
            { assertEquals("INDUSTRIALS", SectorNormalizer.canonicalize("INDUSTRIALS")) },
            { assertEquals("CONSUMER CYCLICAL", SectorNormalizer.canonicalize("CONSUMER CYCLICAL")) },
            { assertEquals("CONSUMER DEFENSIVE", SectorNormalizer.canonicalize("CONSUMER DEFENSIVE")) },
            { assertEquals("COMMUNICATION SERVICES", SectorNormalizer.canonicalize("COMMUNICATION SERVICES")) },
            { assertEquals("BASIC MATERIALS", SectorNormalizer.canonicalize("BASIC MATERIALS")) },
            { assertEquals("REAL ESTATE", SectorNormalizer.canonicalize("REAL ESTATE")) },
            { assertEquals("UTILITIES", SectorNormalizer.canonicalize("UTILITIES")) },
        )
    }

    @Test
    fun `proper-case input is uppercased`() {
        // Given: the proper-case spellings EODHD returns for all 11 sectors
        // When / Then: they all canonicalize to UPPERCASE
        assertAll(
            { assertEquals("TECHNOLOGY", SectorNormalizer.canonicalize("Technology")) },
            { assertEquals("FINANCIAL SERVICES", SectorNormalizer.canonicalize("Financial Services")) },
            { assertEquals("HEALTHCARE", SectorNormalizer.canonicalize("Healthcare")) },
            { assertEquals("ENERGY", SectorNormalizer.canonicalize("Energy")) },
            { assertEquals("INDUSTRIALS", SectorNormalizer.canonicalize("Industrials")) },
            { assertEquals("CONSUMER CYCLICAL", SectorNormalizer.canonicalize("Consumer Cyclical")) },
            { assertEquals("CONSUMER DEFENSIVE", SectorNormalizer.canonicalize("Consumer Defensive")) },
            { assertEquals("COMMUNICATION SERVICES", SectorNormalizer.canonicalize("Communication Services")) },
            { assertEquals("BASIC MATERIALS", SectorNormalizer.canonicalize("Basic Materials")) },
            { assertEquals("REAL ESTATE", SectorNormalizer.canonicalize("Real Estate")) },
            { assertEquals("UTILITIES", SectorNormalizer.canonicalize("Utilities")) },
        )
    }

    @Test
    fun `Financials and Financial map to FINANCIAL SERVICES`() {
        // Given: variant spellings observed on delisted PRD rows
        // When / Then
        assertEquals("FINANCIAL SERVICES", SectorNormalizer.canonicalize("Financials"))
        assertEquals("FINANCIAL SERVICES", SectorNormalizer.canonicalize("Financial"))
        assertEquals("FINANCIAL SERVICES", SectorNormalizer.canonicalize("FINANCIALS"))
    }

    @Test
    fun `Materials maps to BASIC MATERIALS`() {
        // Given: 'Materials' variant observed on 2 delisted PRD rows
        // When / Then
        assertEquals("BASIC MATERIALS", SectorNormalizer.canonicalize("Materials"))
    }

    @Test
    fun `Other and NONE return null in any case`() {
        // Given: provider's "I don't know" buckets
        // When / Then: all return null so sector_symbol resolves to null
        assertAll(
            { assertNull(SectorNormalizer.canonicalize("Other")) },
            { assertNull(SectorNormalizer.canonicalize("OTHER")) },
            { assertNull(SectorNormalizer.canonicalize("other")) },
            { assertNull(SectorNormalizer.canonicalize("NONE")) },
            { assertNull(SectorNormalizer.canonicalize("None")) },
            { assertNull(SectorNormalizer.canonicalize("none")) },
        )
    }

    @Test
    fun `unknown sector strings return null`() {
        // Given: hypothetical new variants the normalizer doesn't know yet
        // When / Then: null — a warn-log surfaces these in IngestionService for the analyst
        assertAll(
            { assertNull(SectorNormalizer.canonicalize("Crypto")) },
            { assertNull(SectorNormalizer.canonicalize("Cannabis")) },
            { assertNull(SectorNormalizer.canonicalize("Banks")) },
        )
    }

    @Test
    fun `whitespace is trimmed before lookup`() {
        // Given: provider returned a value with leading/trailing whitespace
        // When / Then: the trimmed form matches normally
        assertEquals("HEALTHCARE", SectorNormalizer.canonicalize(" Healthcare "))
        assertNull(SectorNormalizer.canonicalize("  Other  "))
    }
}
