package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.model.TreasuryYield
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
 * Verifies the EODHD gov-bond yield client's HTTP wire shape and the gross-yield parse contract.
 * Uses `MockWebServer` so the real `RestClient` wiring is exercised rather than mocked out.
 */
class EodhdGovBondClientTest {
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
    fun `fetchYields parses EOD gov-bond response into gross treasury yields keyed by maturity`() {
        // Given: the gov-bond EOD endpoint returns three trading days where close is the yield in percent
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"date":"2007-06-01","open":4.719,"high":4.719,"low":4.719,"close":4.719,"adjusted_close":4.719,"volume":0},
                      {"date":"2014-06-02","open":0.035,"high":0.041,"low":0.030,"close":0.035,"adjusted_close":0.035,"volume":0},
                      {"date":"2025-05-01","open":4.301,"high":4.350,"low":4.288,"close":4.2931,"adjusted_close":4.2931,"volume":0}
                    ]
                    """.trimIndent(),
                ),
        )

        // When
        val yields =
            runBlocking {
                client().fetchYields("US3M", "US3M.GBOND", minDate = LocalDate.of(2000, 1, 1))
            }

        // Then: each close becomes a gross yieldPct, keyed by the maturity, sorted by date
        assertEquals(
            listOf(
                TreasuryYield("US3M", LocalDate.of(2007, 6, 1), 4.719),
                TreasuryYield("US3M", LocalDate.of(2014, 6, 2), 0.035),
                TreasuryYield("US3M", LocalDate.of(2025, 5, 1), 4.2931),
            ),
            yields,
        )
        // Then: the gov-bond ticker is used as-is on the /eod path (not mangled to .US)
        val recorded = server.takeRequest()
        assertTrue(
            recorded.path?.contains("/eod/US3M.GBOND") == true,
            "expected /eod/US3M.GBOND path, got: ${recorded.path}",
        )
    }

    @Test
    fun `fetchYields returns null on 404 so the ingestion step can skip rather than persist a wrong series`() {
        // Given: EODHD has no record for this ticker (or upstream is down)
        server.enqueue(MockResponse().setResponseCode(404))

        // When
        val yields = runBlocking { client().fetchYields("US3M", "BOGUS.GBOND", minDate = LocalDate.of(2000, 1, 1)) }

        // Then: null bubbles up — a missing series must never be silently stored as empty/zero
        assertNull(yields)
    }

    @Test
    fun `fetchYields acquires a rate-limiter permit on every call`() {
        // Given: any successful response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"date":"2025-05-01","close":4.2931}]"""),
        )

        // When
        runBlocking { client().fetchYields("US3M", "US3M.GBOND", minDate = LocalDate.of(2000, 1, 1)) }

        // Then: provider quota is consulted before the outbound request
        verifyBlocking(rateLimiter) { acquirePermit(ProviderIds.EODHD) }
    }

    private fun client(): EodhdGovBondClient =
        EodhdGovBondClient(
            apiKeyService = apiKeyService,
            rateLimiterService = rateLimiter,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
}
