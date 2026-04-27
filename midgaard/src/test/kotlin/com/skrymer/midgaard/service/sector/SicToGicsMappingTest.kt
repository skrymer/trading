package com.skrymer.midgaard.service.sector

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SicToGicsMappingTest {
    @Test
    fun `commercial banks resolve to FINANCIAL SERVICES`() {
        // Given: SIC codes for national and state commercial banks
        // When / Then
        assertEquals("FINANCIAL SERVICES", SicToGicsMapping.gicsSectorFor(6021))
        assertEquals("FINANCIAL SERVICES", SicToGicsMapping.gicsSectorFor(6022))
    }

    @Test
    fun `prepackaged software resolves to TECHNOLOGY`() {
        // Given: SIC 7372 (prepackaged software)
        // When / Then: post-2018 GICS classifies this as Information Technology;
        // Midgaard's existing taxonomy uses TECHNOLOGY
        assertEquals("TECHNOLOGY", SicToGicsMapping.gicsSectorFor(7372))
    }

    @Test
    fun `pharmaceuticals override the broader chemicals major group`() {
        // Given: SIC 2834 (pharmaceutical preparations) sits inside chemicals (2800-2899)
        // When
        val sector = SicToGicsMapping.gicsSectorFor(2834)

        // Then: specific pharmaceutical override wins over the broader BASIC MATERIALS rule
        assertEquals("HEALTHCARE", sector)
    }

    @Test
    fun `chemicals outside the pharmaceutical range stay BASIC MATERIALS`() {
        // Given: SIC 2810 (industrial inorganic chemicals) is outside the 2830-2839 override
        // When / Then
        assertEquals("BASIC MATERIALS", SicToGicsMapping.gicsSectorFor(2810))
    }

    @Test
    fun `semiconductors resolve to TECHNOLOGY`() {
        // Given: SIC 3674 (semiconductors and related devices)
        // When / Then
        assertEquals("TECHNOLOGY", SicToGicsMapping.gicsSectorFor(3674))
    }

    @Test
    fun `motor vehicles resolve to CONSUMER CYCLICAL despite sitting in transportation equipment`() {
        // Given: SIC 3711 (motor vehicles and passenger car bodies) sits inside the 3700-3799
        // transportation-equipment major group, which would default to INDUSTRIALS
        // When
        val sector = SicToGicsMapping.gicsSectorFor(3711)

        // Then: motor-vehicle override wins
        assertEquals("CONSUMER CYCLICAL", sector)
    }

    @Test
    fun `oil and gas extraction resolves to ENERGY`() {
        // Given: SIC 1311 (crude petroleum and natural gas)
        // When / Then
        assertEquals("ENERGY", SicToGicsMapping.gicsSectorFor(1311))
    }

    @Test
    fun `utilities resolve to UTILITIES`() {
        // Given: SIC 4911 (electric services)
        // When / Then
        assertEquals("UTILITIES", SicToGicsMapping.gicsSectorFor(4911))
    }

    @Test
    fun `real estate resolves to REAL ESTATE`() {
        // Given: SIC 6512 (operators of apartment buildings)
        // When / Then
        assertEquals("REAL ESTATE", SicToGicsMapping.gicsSectorFor(6512))
    }

    @Test
    fun `health services resolve to HEALTHCARE`() {
        // Given: SIC 8060 (hospitals)
        // When / Then
        assertEquals("HEALTHCARE", SicToGicsMapping.gicsSectorFor(8060))
    }

    @Test
    fun `telecommunications resolve to COMMUNICATION SERVICES`() {
        // Given: SIC 4813 (telephone communications)
        // When / Then
        assertEquals("COMMUNICATION SERVICES", SicToGicsMapping.gicsSectorFor(4813))
    }

    @Test
    fun `unknown SIC code returns null so callers can apply their own fallback`() {
        // Given: SIC 0 (not a valid SIC code) — outside any defined range
        // When / Then
        assertNull(SicToGicsMapping.gicsSectorFor(0))
    }

    @Test
    fun `nonclassifiable establishments fall through to INDUSTRIALS`() {
        // Given: SIC 9999 (nonclassifiable establishments) — the documented catch-all
        // When / Then
        assertEquals("INDUSTRIALS", SicToGicsMapping.gicsSectorFor(9999))
    }

    @Test
    fun `food stores resolve to CONSUMER DEFENSIVE`() {
        // Given: SIC 5411 (grocery stores) — staples retail
        // When / Then
        assertEquals("CONSUMER DEFENSIVE", SicToGicsMapping.gicsSectorFor(5411))
    }

    @Test
    fun `apparel stores resolve to CONSUMER CYCLICAL`() {
        // Given: SIC 5651 (family clothing stores) — discretionary retail
        // When / Then
        assertEquals("CONSUMER CYCLICAL", SicToGicsMapping.gicsSectorFor(5651))
    }
}
