package com.skrymer.midgaard.integration.edgar

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.service.RateLimiterService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the SEC EDGAR client's HTTP behaviour and rate-limiter integration.
 * Uses `MockWebServer` so we exercise the actual `RestClient` wiring rather
 * than mocking it out — that's where the User-Agent etiquette and CIK-padding
 * details live.
 */
class EdgarClientTest {
    private lateinit var server: MockWebServer
    private lateinit var rateLimiter: RateLimiterService

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        rateLimiter = mock()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `happy path returns SIC for a known CIK`() {
        // Given: EDGAR returns a submission JSON with a SIC code
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "cik": "320193",
                      "sic": "3571",
                      "sicDescription": "ELECTRONIC COMPUTERS"
                    }
                    """.trimIndent(),
                ),
        )
        val client = client()

        // When
        val submission = runBlocking { client.getSubmission("320193") }

        // Then
        assertEquals(3571, submission?.sicCode)
        assertEquals("ELECTRONIC COMPUTERS", submission?.sicDescription)
    }

    @Test
    fun `404 returns null without throwing`() {
        // Given: EDGAR has no record for this CIK
        server.enqueue(MockResponse().setResponseCode(404))
        val client = client()

        // When
        val submission = runBlocking { client.getSubmission("9999999999") }

        // Then: caller can fall back to a default sector instead of failing the ingest
        assertNull(submission)
    }

    @Test
    fun `CIK is zero-padded to 10 digits per SEC URL convention`() {
        // Given: a short CIK
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"cik":"320193","sic":"3571"}"""),
        )
        val client = client()

        // When
        runBlocking { client.getSubmission("320193") }

        // Then: request path uses the 10-digit padded form
        val recorded = server.takeRequest()
        assertEquals("/submissions/CIK0000320193.json", recorded.path)
    }

    @Test
    fun `User-Agent header identifies the application`() {
        // Given: any successful response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"cik":"320193","sic":"3571"}"""),
        )
        val client = client(userAgent = "Midgaard / sonni.nielsen@gmail.com")

        // When
        runBlocking { client.getSubmission("320193") }

        // Then: SEC etiquette satisfied — User-Agent is present and non-empty
        val recorded = server.takeRequest()
        val userAgent = recorded.getHeader("User-Agent")
        assertTrue(userAgent?.contains("Midgaard") == true, "expected Midgaard in User-Agent, got: $userAgent")
    }

    @Test
    fun `rate-limiter permit is acquired before each request`() {
        // Given: any successful response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"cik":"320193","sic":"3571"}"""),
        )
        val client = client()

        // When
        runBlocking { client.getSubmission("320193") }

        // Then: the EDGAR rate-limit bucket was consulted
        verifyBlocking(rateLimiter) { acquirePermit(ProviderIds.EDGAR) }
    }

    @Test
    fun `blank User-Agent fails fast — SEC blocks unidentified requests`() {
        // Given: a client wired with an empty user agent (configuration mistake)
        val client = client(userAgent = "")

        // When / Then: the client refuses to call EDGAR rather than send a request that will be blocked
        assertThrows<IllegalStateException> {
            runBlocking { client.getSubmission("320193") }
        }
    }

    @Test
    fun `submission without SIC returns dto with null sic`() {
        // Given: EDGAR returns a submission missing the SIC field (rare but real for some filers)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"cik":"320193"}"""),
        )
        val client = client()

        // When
        val submission = runBlocking { client.getSubmission("320193") }

        // Then: caller can detect the missing SIC and fall back
        assertNull(submission?.sicCode)
    }

    private fun client(userAgent: String = "Midgaard / test@example.com"): EdgarClient =
        EdgarClient(
            rateLimiterService = rateLimiter,
            baseUrl = server.url("/").toString().trimEnd('/'),
            userAgent = userAgent,
        )
}
