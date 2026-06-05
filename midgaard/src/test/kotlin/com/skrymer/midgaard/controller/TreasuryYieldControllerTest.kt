package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.TreasuryYield
import com.skrymer.midgaard.repository.TreasuryYieldRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals

class TreasuryYieldControllerTest {
    private lateinit var repository: TreasuryYieldRepository
    private lateinit var controller: TreasuryYieldController

    @BeforeEach
    fun setUp() {
        repository = mock()
        controller = TreasuryYieldController(repository)
    }

    @Test
    fun `getYields upper-cases the maturity before lookup and returns the repository series`() {
        // Given: the store holds the US3M series
        val series = listOf(TreasuryYield("US3M", LocalDate.of(2025, 5, 1), 4.2931))
        whenever(repository.findByMaturity("US3M")).thenReturn(series)

        // When: the request comes in lower-case
        val result = controller.getYields("us3m")

        // Then: the maturity was upper-cased for the lookup and the series flows through
        assertEquals(series, result)
        verify(repository).findByMaturity(eq("US3M"))
    }
}
