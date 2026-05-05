package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.service.ApiKeyService
import com.skrymer.midgaard.service.RateLimiterService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the EODHD FX client's HTTP wire shape and the parse-into-LocalDate-keyed-map
 * contract that the series cache depends on. Uses `MockWebServer` so we exercise the
 * actual `RestClient` wiring rather than mocking it out.
 */
class EodhdFxClientTest {
    private lateinit var server: MockWebServer
    private lateinit var apiKeyService: ApiKeyService
    private lateinit var rateLimiter: RateLimiterService

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        apiKeyService = mock()
        rateLimiter = mock()
        whenever(apiKeyService.getEodhdApiKey()).thenReturn("test-key")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchCurrent returns close from EODHD real-time response`() {
        // Given: real-time endpoint returns a single quote with a close price
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":"EURUSD.FOREX","timestamp":1718380800,"close":1.0856}"""),
        )

        // When
        val rate = runBlocking { client().fetchCurrent("EUR", "USD") }

        // Then
        assertEquals(1.0856, rate)
        // Then: pair is built as `{from}{to}.FOREX` per EODHD convention
        val recorded = server.takeRequest()
        assertTrue(recorded.path?.contains("/real-time/EURUSD.FOREX") == true, "expected EURUSD.FOREX path, got: ${recorded.path}")
    }

    @Test
    fun `fetchCurrent returns null on 404 — caller falls back rather than crashing the controller`() {
        // Given: EODHD has no record for this pair (or upstream is down)
        server.enqueue(MockResponse().setResponseCode(404))

        // When
        val rate = runBlocking { client().fetchCurrent("XXX", "YYY") }

        // Then: null bubbles up so the cache layer's `unless = "#result == null"` skips caching
        assertNull(rate)
    }

    @Test
    fun `fetchHistoricalSeries parses EOD response into LocalDate-keyed close map`() {
        // Given: EOD endpoint returns three trading days
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"date":"2024-06-12","open":1.5210,"high":1.5240,"low":1.5200,"close":1.5230,"adjusted_close":1.5230,"volume":0},
                      {"date":"2024-06-13","open":1.5230,"high":1.5245,"low":1.5215,"close":1.5232,"adjusted_close":1.5232,"volume":0},
                      {"date":"2024-06-14","open":1.5232,"high":1.5250,"low":1.5220,"close":1.5234,"adjusted_close":1.5234,"volume":0}
                    ]
                    """.trimIndent(),
                ),
        )

        // When
        val series = runBlocking { client().fetchHistoricalSeries("USD", "AUD") }

        // Then: rows are flattened into the LocalDate→close shape the series cache stores
        assertEquals(3, series?.size)
        assertEquals(1.5234, series?.get(LocalDate.of(2024, 6, 14)))
        // Then: pair convention applies to the historical endpoint too
        val recorded = server.takeRequest()
        assertTrue(recorded.path?.contains("/eod/USDAUD.FOREX") == true, "expected USDAUD.FOREX path, got: ${recorded.path}")
    }

    @Test
    fun `fetchHistoricalSeries drops rows with null close rather than failing the entire series`() {
        // Given: EODHD occasionally emits null close on holiday placeholders or in-progress days —
        // a single bad row must not nuke the whole pair (which would then be uncached for 24h
        // and 404 every caller until the upstream changes)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"date":"2024-06-13","open":1.5005,"high":1.5040,"low":1.4990,"close":1.5010,"volume":0},
                      {"date":"2024-06-14","open":null,"high":null,"low":null,"close":null,"volume":0},
                      {"date":"2024-06-15","open":1.5020,"high":1.5050,"low":1.5000,"close":1.5025,"volume":0}
                    ]
                    """.trimIndent(),
                ),
        )

        // When
        val series = runBlocking { client().fetchHistoricalSeries("USD", "AUD") }

        // Then: only the rows with valid closes survive
        assertEquals(2, series?.size)
        assertEquals(1.5010, series?.get(LocalDate.of(2024, 6, 13)))
        assertEquals(1.5025, series?.get(LocalDate.of(2024, 6, 15)))
        assertNull(series?.get(LocalDate.of(2024, 6, 14)))
    }

    @Test
    fun `fetchHistoricalSeries returns null on empty array — keeps an empty payload out of the series cache`() {
        // Given: EODHD returns an empty array (no history for pair)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )

        // When
        val series = runBlocking { client().fetchHistoricalSeries("XXX", "YYY") }

        // Then: returning null trips `unless = "#result == null"` so we don't cache "no data"
        assertNull(series)
    }

    @Test
    fun `rate-limiter permit is acquired on every call — cache hits are tested elsewhere`() {
        // Given: any successful response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":"EURUSD.FOREX","close":1.0856}"""),
        )

        // When
        runBlocking { client().fetchCurrent("EUR", "USD") }

        // Then: provider quota is consulted (cache short-circuit happens at the proxy level, so this
        // direct call exercises the inner method body)
        verifyBlocking(rateLimiter) { acquirePermit(ProviderIds.EODHD) }
    }

    private fun client(): EodhdFxClient =
        EodhdFxClient(
            apiKeyService = apiKeyService,
            rateLimiterService = rateLimiter,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
}
