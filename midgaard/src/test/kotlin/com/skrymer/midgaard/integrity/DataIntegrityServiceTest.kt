package com.skrymer.midgaard.integrity

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class DataIntegrityServiceTest {
    @Test
    fun `runAll aggregates violations from all registered validators`() {
        // Given: two validators, one returning two violations and one returning one
        val v1Violations =
            listOf(
                Violation("V1", "I1", Severity.CRITICAL, "desc1", count = 5, sampleSymbols = listOf("AAA")),
                Violation("V1", "I2", Severity.HIGH, "desc2", count = 3, sampleSymbols = listOf("BBB")),
            )
        val v2Violations =
            listOf(
                Violation("V2", "I1", Severity.MEDIUM, "desc3", count = 1, sampleSymbols = emptyList()),
            )
        val validator1 =
            mock<DataIntegrityValidator> {
                on { name } doReturn "V1"
                on { validate() } doReturn v1Violations
            }
        val validator2 =
            mock<DataIntegrityValidator> {
                on { name } doReturn "V2"
                on { validate() } doReturn v2Violations
            }
        val repo = mock<ViolationRepository>()
        val service = DataIntegrityService(listOf(validator1, validator2), repo)

        // When
        val result = service.runAll()

        // Then: result contains all 3 violations
        assertEquals(3, result.size)
        assertEquals(v1Violations + v2Violations, result)
    }

    @Test
    fun `runAll persists the aggregated violations via replaceAll`() {
        // Given: a validator returning one violation
        val violations =
            listOf(
                Violation("V1", "I1", Severity.CRITICAL, "desc", count = 1, sampleSymbols = emptyList()),
            )
        val validator =
            mock<DataIntegrityValidator> {
                on { name } doReturn "V1"
                on { validate() } doReturn violations
            }
        val repo = mock<ViolationRepository>()
        val service = DataIntegrityService(listOf(validator), repo)

        // When
        service.runAll()

        // Then: replaceAll called with the violations
        val captor = argumentCaptor<List<Violation>>()
        verify(repo).replaceAll(captor.capture())
        assertEquals(violations, captor.firstValue)
    }

    @Test
    fun `runAll continues when one validator throws and persists the others`() {
        // Given: validator A throws, validator B returns one violation
        val bViolations =
            listOf(
                Violation("B", "I1", Severity.MEDIUM, "desc", count = 1, sampleSymbols = emptyList()),
            )
        val validatorA =
            mock<DataIntegrityValidator> {
                on { name } doReturn "A"
            }
        whenever(validatorA.validate()).thenThrow(IllegalStateException("DB connection lost"))
        val validatorB =
            mock<DataIntegrityValidator> {
                on { name } doReturn "B"
                on { validate() } doReturn bViolations
            }
        val repo = mock<ViolationRepository>()
        val service = DataIntegrityService(listOf(validatorA, validatorB), repo)

        // When
        val result = service.runAll()

        // Then: only B's violation is persisted; A's exception is swallowed
        assertEquals(bViolations, result)
        val captor = argumentCaptor<List<Violation>>()
        verify(repo).replaceAll(captor.capture())
        assertEquals(bViolations, captor.firstValue)
    }

    @Test
    fun `runAll with no validators persists empty list`() {
        // Given: no validators registered (degenerate)
        val repo = mock<ViolationRepository>()
        val service = DataIntegrityService(emptyList(), repo)

        // When
        val result = service.runAll()

        // Then: empty list returned, replaceAll called with empty list
        assertEquals(emptyList(), result)
        verify(repo).replaceAll(emptyList())
    }

    @Test
    fun `latestViolations delegates to repository findAll`() {
        // Given: repository configured to return a known set
        val persisted =
            listOf(
                Violation("X", "I1", Severity.LOW, "from db", count = 2, sampleSymbols = emptyList()),
            )
        val repo =
            mock<ViolationRepository> {
                on { findAll() } doReturn persisted
            }
        val service = DataIntegrityService(emptyList(), repo)

        // When
        val result = service.latestViolations()

        // Then
        assertEquals(persisted, result)
    }

    @Test
    fun `violationCount delegates to repository count`() {
        // Given: repository configured with a count
        val repo =
            mock<ViolationRepository> {
                on { count() } doReturn 7
            }
        val service = DataIntegrityService(emptyList(), repo)

        // When / Then
        assertEquals(7, service.violationCount())
    }

    @Test
    fun `runAll propagates repository failure to caller`() {
        // Given: validators succeed but the repository fails to persist
        val repo = mock<ViolationRepository>()
        whenever(repo.replaceAll(any())).thenThrow(IllegalStateException("DB connection lost"))
        val service = DataIntegrityService(emptyList(), repo)

        // When / Then: failure propagates so caller can decide how to handle it
        // (IngestionService wraps in runCatching to avoid crashing a successful ingest;
        // IntegrityController surfaces the 500 to the API caller).
        val ex =
            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                service.runAll()
            }
        assertEquals("DB connection lost", ex.message)
    }
}
