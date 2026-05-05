package com.skrymer.midgaard.integration.alphavantage

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
 * Verifies the AlphaVantage FX client's HTTP wire shape (CURRENCY_EXCHANGE_RATE for
 * current, FX_DAILY?outputsize=full for the historical series the cache stores).
 */
class AlphaVantageFxClientTest {
    private lateinit var server: MockWebServer
    private lateinit var apiKeyService: ApiKeyService
    private lateinit var rateLimiter: RateLimiterService

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        apiKeyService = mock()
        rateLimiter = mock()
        whenever(apiKeyService.getAlphaVantageApiKey()).thenReturn("test-key")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchCurrent parses CURRENCY_EXCHANGE_RATE response and uses correct AV function`() {
        // Given: AV CURRENCY_EXCHANGE_RATE-shaped payload
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "Realtime Currency Exchange Rate": {
                        "1. From_Currency Code": "EUR",
                        "3. To_Currency Code": "USD",
                        "5. Exchange Rate": "1.08560000",
                        "6. Last Refreshed": "2024-06-14 12:00:00"
                      }
                    }
                    """.trimIndent(),
                ),
        )

        // When
        val rate = runBlocking { client().fetchCurrent("EUR", "USD") }

        // Then
        assertEquals(1.0856, rate)
        // Then: function= and from/to params follow AV's CURRENCY_EXCHANGE_RATE contract
        val recorded = server.takeRequest()
        assertTrue(recorded.path?.contains("function=CURRENCY_EXCHANGE_RATE") == true)
        assertTrue(recorded.path?.contains("from_currency=EUR") == true)
        assertTrue(recorded.path?.contains("to_currency=USD") == true)
    }

    @Test
    fun `fetchCurrent returns null when AV returns rate-limit Note rather than data`() {
        // Given: AV's standard "you're being rate limited" response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Note":"Thank you for using Alpha Vantage! ... API call frequency exceeded."}"""),
        )

        // When
        val rate = runBlocking { client().fetchCurrent("EUR", "USD") }

        // Then: null short-circuits the cache `unless = "#result == null"` so we'll retry next call
        assertNull(rate)
    }

    @Test
    fun `fetchHistoricalSeries returns null and logs when AV returns a rate-limit Note instead of data`() {
        // Given: AV's "throttled" response on the FX_DAILY endpoint
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Note":"Thank you for using Alpha Vantage! ... API call frequency exceeded."}"""),
        )

        // When
        val series = runBlocking { client().fetchHistoricalSeries("USD", "AUD") }

        // Then: null short-circuits the cache (no poisoned cache entry); the diagnostic logging
        // happens at ERROR level so an operator seeing 404s on /api/fx/rate/historical can find the
        // upstream cause without logging into the AV dashboard
        assertNull(series)
    }

    @Test
    fun `fetchHistoricalSeries pulls FX_DAILY full series and parses to LocalDate map`() {
        // Given: AV FX_DAILY-shaped response with two days
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "Meta Data": {"2. From Symbol":"USD","3. To Symbol":"AUD","4. Output Size":"Full size"},
                      "Time Series FX (Daily)": {
                        "2024-06-13": {"1. open":"1.5005","2. high":"1.5040","3. low":"1.4990","4. close":"1.5010"},
                        "2024-06-14": {"1. open":"1.5010","2. high":"1.5050","3. low":"1.4995","4. close":"1.5020"}
                      }
                    }
                    """.trimIndent(),
                ),
        )

        // When
        val series = runBlocking { client().fetchHistoricalSeries("USD", "AUD") }

        // Then
        assertEquals(2, series?.size)
        assertEquals(1.5020, series?.get(LocalDate.of(2024, 6, 14)))
        // Then: outputsize=full ensures we get the entire history with one call (the whole point
        // of the series-cache strategy — pay once, answer many dates)
        val recorded = server.takeRequest()
        assertTrue(recorded.path?.contains("function=FX_DAILY") == true)
        assertTrue(recorded.path?.contains("outputsize=full") == true)
    }

    @Test
    fun `rate-limiter permit is acquired before each call`() {
        // Given: any valid response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Realtime Currency Exchange Rate":{"5. Exchange Rate":"1.0"}}"""),
        )

        // When
        runBlocking { client().fetchCurrent("EUR", "USD") }

        // Then
        verifyBlocking(rateLimiter) { acquirePermit(ProviderIds.ALPHAVANTAGE) }
    }

    private fun client(): AlphaVantageFxClient =
        AlphaVantageFxClient(
            apiKeyService = apiKeyService,
            rateLimiterService = rateLimiter,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
}
