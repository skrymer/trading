package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.OvtlyrSignal
import com.skrymer.midgaard.model.OvtlyrSignalType
import com.skrymer.midgaard.repository.OvtlyrSignalRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals

class OvtlyrSignalControllerTest {
    private lateinit var repository: OvtlyrSignalRepository
    private lateinit var controller: OvtlyrSignalController

    @BeforeEach
    fun setUp() {
        repository = mock()
        controller = OvtlyrSignalController(repository)
    }

    @Test
    fun `getSignals upper-cases the symbol before lookup and returns the repository result`() {
        // Given: the store holds one signal for AAPL
        val signal = OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 18), OvtlyrSignalType.BUY)
        whenever(repository.findBySymbol("AAPL")).thenReturn(listOf(signal))

        // When: the request comes in lower-case
        val result = controller.getSignals("aapl")

        // Then: the symbol was upper-cased for the lookup and the result flows through
        assertEquals(listOf(signal), result)
        verify(repository).findBySymbol(eq("AAPL"))
    }
}
