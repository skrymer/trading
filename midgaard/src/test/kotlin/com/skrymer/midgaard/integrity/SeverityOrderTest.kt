package com.skrymer.midgaard.integrity

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SeverityOrderTest {
    @Test
    fun `Severity declaration order encodes triage priority`() {
        // Given: ViolationRepository.findAll and UiController.integrity sort by ordinal,
        // and the contract is "CRITICAL first, LOW last". Reordering the enum silently
        // breaks both consumers — pin the order with this test.
        assertEquals(
            listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW),
            Severity.entries,
        )
    }
}
