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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the delisted-symbol catalogue client's HTTP behaviour. The client
 * is expected to return a parsed list on 200 OK and null on any HTTP failure
 * — the ingest pipeline relies on null to skip enrichment for that run.
 */
class EodhdSymbolListClientTest {
    private lateinit var server: MockWebServer
    private lateinit var rateLimiter: RateLimiterService
    private lateinit var apiKeys: ApiKeyService

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        rateLimiter = mock()
        apiKeys = mock()
        whenever(apiKeys.getEodhdApiKey()).thenReturn("test-key")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `delisted-list fixture parses to dto list with the expected fields`() {
        // Given: a delisted-list response with three rows of varying types
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"Code":"ABCD","Name":"Acme Corp","Country":"USA","Exchange":"US","Currency":"USD","Type":"Common Stock"},
                      {"Code":"EFGH","Name":"Beta ETF","Country":"USA","Exchange":"US","Currency":"USD","Type":"ETF"},
                      {"Code":"IJKL-W","Name":"Gamma Warrants","Country":"USA","Exchange":"US","Currency":"USD","Type":"Warrant"}
                    ]
                    """.trimIndent(),
                ),
        )
        val client = client()

        // When
        val symbols = runBlocking { client.getDelistedSymbols() }

        // Then: every row in the array is parsed; type field preserved so the caller can filter
        assertNotNull(symbols)
        assertEquals(3, symbols.size)
        assertEquals("ABCD", symbols[0].code)
        assertEquals("Acme Corp", symbols[0].name)
        assertEquals("Common Stock", symbols[0].type)
        assertEquals("US", symbols[0].exchange)
        assertEquals("ETF", symbols[1].type)
        assertEquals("Warrant", symbols[2].type)
    }

    @Test
    fun `empty list returns an empty result rather than null`() {
        // Given: EODHD has no delisted symbols (degenerate case for testing)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val client = client()

        // When
        val symbols = runBlocking { client.getDelistedSymbols() }

        // Then: caller distinguishes "no data" (empty list) from "fetch failed" (null)
        assertNotNull(symbols)
        assertTrue(symbols.isEmpty())
    }

    @Test
    fun `HTTP failure returns null so the ingest pipeline can skip the run`() {
        // Given: EODHD returns a server error
        server.enqueue(MockResponse().setResponseCode(500))
        val client = client()

        // When
        val symbols = runBlocking { client.getDelistedSymbols() }

        // Then
        assertNull(symbols)
    }

    @Test
    fun `request URL includes delisted=1 and the api token`() {
        // Given: any successful response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val client = client()

        // When
        runBlocking { client.getDelistedSymbols() }

        // Then: URL contains the delisted flag and the api_token query param
        val recorded = server.takeRequest()
        val path = recorded.path ?: ""
        assertTrue(path.contains("/exchange-symbol-list/US"), "expected exchange-symbol-list path, got: $path")
        assertTrue(path.contains("delisted=1"), "expected delisted=1 query param, got: $path")
        assertTrue(path.contains("api_token=test-key"), "expected api_token in query, got: $path")
    }

    @Test
    fun `rate-limiter permit is acquired before each request`() {
        // Given
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"),
        )
        val client = client()

        // When
        runBlocking { client.getDelistedSymbols() }

        // Then
        verifyBlocking(rateLimiter) { acquirePermit(ProviderIds.EODHD) }
    }

    private fun client(): EodhdSymbolListClient =
        EodhdSymbolListClient(
            apiKeyService = apiKeys,
            rateLimiterService = rateLimiter,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
}
