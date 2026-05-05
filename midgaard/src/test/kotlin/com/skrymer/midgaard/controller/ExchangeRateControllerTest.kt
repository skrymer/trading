package com.skrymer.midgaard.controller

import com.skrymer.midgaard.integration.FxProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the controller delegates to the qualified `FxProvider` (whichever the
 * `app.fx.provider` toggle wires) and translates null → 404, value → 200. The provider
 * itself is mocked — provider-shape concerns belong to FX client tests.
 */
class ExchangeRateControllerTest {
    private lateinit var fxProvider: FxProvider
    private lateinit var controller: ExchangeRateController

    @BeforeEach
    fun setUp() {
        fxProvider = mock()
        controller = ExchangeRateController(fxProvider)
    }

    @Test
    fun `getCurrentRate returns 200 with rate from provider — currencies upper-cased before lookup`() {
        // Given: provider returns a rate for USD->AUD
        fxProvider.stub { onBlocking { getExchangeRate(any(), any()) }.doReturn(1.5234) }

        // When: request comes in lower-case
        val response = controller.getCurrentRate(from = "usd", to = "aud")

        // Then: 200, rate flows through, response echoes the upper-cased pair
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(1.5234, body.rate)
        assertEquals("USD", body.from)
        assertEquals("AUD", body.to)
        // Then: provider was called with the upper-cased pair, not the raw input
        verifyBlocking(fxProvider) { getExchangeRate(eq("USD"), eq("AUD")) }
    }

    @Test
    fun `getCurrentRate returns 404 when provider returns null — caller can fall back without parsing a body`() {
        // Given: provider has no data
        fxProvider.stub { onBlocking { getExchangeRate(any(), any()) }.doReturn(null) }

        // When
        val response = controller.getCurrentRate(from = "XXX", to = "YYY")

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNull(response.body)
    }

    @Test
    fun `getHistoricalRate passes the requested date to the provider and echoes it in the response`() {
        // Given: provider returns historical rate for a specific date
        val date = LocalDate.of(2024, 6, 14)
        fxProvider.stub { onBlocking { getHistoricalExchangeRate(any(), any(), any()) }.doReturn(1.5234) }

        // When
        val response = controller.getHistoricalRate(from = "usd", to = "aud", date = date)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = assertNotNull(response.body)
        assertEquals(1.5234, body.rate)
        assertEquals(date, body.date)
        verifyBlocking(fxProvider) { getHistoricalExchangeRate(eq("USD"), eq("AUD"), eq(date)) }
    }

    @Test
    fun `getHistoricalRate returns 404 when provider has no rate for that date`() {
        // Given: outside the cached series (or pre-history)
        val date = LocalDate.of(1900, 1, 1)
        fxProvider.stub { onBlocking { getHistoricalExchangeRate(any(), any(), any()) }.doReturn(null) }

        // When
        val response = controller.getHistoricalRate(from = "USD", to = "AUD", date = date)

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
