package com.skrymer.midgaard.integration.ovtlyr

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real RestClient + Jackson wiring against MockWebServer — verifies the
 * `@JsonProperty` mappings on [OvtlyrPayloadDto] parse ovtlyr's actual response shape.
 */
class OvtlyrClientTest {
    private lateinit var server: MockWebServer

    private val credentials = OvtlyrCredentials("user-1", "token-1", "proj-1")

    // Kotlin module + JSR-310 (LocalDate) — mirrors the Spring-managed ObjectMapper.
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = OvtlyrClient(baseUrl = server.url("/").toString(), objectMapper = objectMapper)

    @Test
    fun `getStockInformation parses ovtlyr response into a payload of daily entries`() {
        // Given: ovtlyr returns two daily entries under lst_h, one with a call and one without
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"stockName":"Apple Inc","sectorSymbol":"XLK","lst_h":[
                      {"stockSymbol":"AAPL","quotedate":"2026-05-11T00:00:00","quotedateStr":"2026-05-11","final_calls":"Buy"},
                      {"stockSymbol":"AAPL","quotedate":"2026-05-14T00:00:00","quotedateStr":"2026-05-14","final_calls":null}
                    ]}
                    """.trimIndent(),
                ),
        )

        // When
        val payload = client().getStockInformation("AAPL", credentials)

        // Then: both entries parsed; the date-only field and nullable final_calls round-trip
        assertEquals(2, payload?.quotes?.size)
        assertEquals("AAPL", payload?.quotes?.get(0)?.symbol)
        assertEquals(LocalDate.of(2026, 5, 11), payload?.quotes?.get(0)?.date)
        assertEquals("Buy", payload?.quotes?.get(0)?.finalCalls)
        assertNull(payload?.quotes?.get(1)?.finalCalls)
        // Then: the request carries ovtlyr's auth — ProjectId header + session cookies
        val recorded = server.takeRequest()
        assertEquals("proj-1", recorded.getHeader("ProjectId"))
        assertTrue(recorded.getHeader("Cookie")?.contains("Token=token-1") == true)
    }

    @Test
    fun `getStockInformation returns null on a non-2xx response`() {
        // Given: ovtlyr rejects the request (e.g. expired session cookies)
        server.enqueue(MockResponse().setResponseCode(401))

        // When / Then: the failure is swallowed — the backfill skips this symbol, not crashes
        assertNull(client().getStockInformation("AAPL", credentials))
    }

    @Test
    fun `getStockInformation returns null when ovtlyr reports the symbol as Invalid Stock`() {
        // Given: ovtlyr's 200 error envelope for a symbol outside its coverage
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"result":"3","resultDetail":"Invalid Stock","lst_h":null}"""),
        )

        // When / Then: a coverage gap reads as no data — null, no exception
        assertNull(client().getStockInformation("NOTREAL", credentials))
    }

    @Test
    fun `getStockInformation returns null when ovtlyr serves a non-JSON body`() {
        // Given: a 200 whose body is an HTML page, not the expected JSON (ovtlyr's soft-block
        // / expired-session behaviour — it serves a web page instead of a 4xx)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<!DOCTYPE html><html><head><title>Log in</title></head><body>Please sign in</body></html>"),
        )

        // When / Then: the unparseable body is swallowed — the backfill skips this symbol
        assertNull(client().getStockInformation("AAPL", credentials))
    }
}
