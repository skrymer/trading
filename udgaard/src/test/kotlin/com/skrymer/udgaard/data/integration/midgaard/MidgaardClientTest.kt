package com.skrymer.udgaard.data.integration.midgaard

import com.skrymer.udgaard.data.model.OvtlyrSignal
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Uses the reserved-by-RFC `.invalid` TLD so that if the MockRestServiceServer ever
 * fails to intercept (e.g., the builder wiring regresses), the test fails loudly with a
 * DNS resolution error rather than silently hitting whatever happens to be on port 8081.
 */
@RestClientTest(MidgaardClient::class)
@TestPropertySource(properties = ["midgaard.base-url=http://midgaard-test.invalid"])
class MidgaardClientTest {
  @Autowired
  private lateinit var client: MidgaardClient

  @Autowired
  private lateinit var mockServer: MockRestServiceServer

  @Test
  fun `getEarnings returns mapped earnings on 200 OK`() {
    // Given Midgaard returns one historical and one projected earnings row
    val responseJson =
      """
      [
        {
          "symbol": "AAPL",
          "fiscalDateEnding": "2024-03-31",
          "reportedDate": "2024-05-02",
          "reportedEps": 1.53,
          "estimatedEps": 1.50,
          "surprise": 0.03,
          "surprisePercentage": 2.0,
          "reportTime": "AfterMarket"
        },
        {
          "symbol": "AAPL",
          "fiscalDateEnding": "2026-06-30",
          "reportedDate": "2026-07-30",
          "reportedEps": null,
          "estimatedEps": 1.89,
          "surprise": null,
          "surprisePercentage": null,
          "reportTime": "AfterMarket"
        }
      ]
      """.trimIndent()
    mockServer
      .expect(requestTo("http://midgaard-test.invalid/api/earnings/AAPL"))
      .andExpect(method(HttpMethod.GET))
      .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

    // When the client fetches earnings for AAPL
    val result = client.getEarnings("AAPL")

    // Then both rows come through with canonical field names mapped
    assertNotNull(result)
    assertEquals(2, result.size)

    val historical = result[0]
    assertEquals("AAPL", historical.symbol)
    assertEquals(LocalDate.of(2024, 3, 31), historical.fiscalDateEnding)
    assertEquals(LocalDate.of(2024, 5, 2), historical.reportedDate)
    assertEquals(1.53, historical.reportedEPS)
    assertEquals(1.50, historical.estimatedEPS)

    val projected = result[1]
    assertEquals(LocalDate.of(2026, 6, 30), projected.fiscalDateEnding)
    assertNull(projected.reportedEPS, "future earnings have no reportedEps yet")
    assertEquals(1.89, projected.estimatedEPS)
  }

  @Test
  fun `getEarnings returns empty list when provider has no rows for symbol`() {
    // Given the provider returns an empty array
    mockServer
      .expect(requestTo("http://midgaard-test.invalid/api/earnings/NEWLY"))
      .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

    // When the client fetches earnings
    val result = client.getEarnings("NEWLY")

    // Then the result is an empty list — distinguishable from null ("we don't know")
    assertNotNull(result)
    assertTrue(result.isEmpty())
  }

  @Test
  fun `getEarnings returns null on server error`() {
    // Given the provider responds with a 5xx
    mockServer
      .expect(requestTo("http://midgaard-test.invalid/api/earnings/BROKE"))
      .andRespond(withServerError())

    // When the client fetches earnings
    val result = client.getEarnings("BROKE")

    // Then the client surfaces the failure as null (callers fall back to existing data)
    assertNull(result)
  }

  @Test
  fun `getOvtlyrSignals returns mapped signals on 200 OK`() {
    // Given Midgaard returns a Buy and a Sell call for the symbol
    val responseJson =
      """
      [
        {"symbol": "AAPL", "signalDate": "2026-05-11", "signal": "BUY"},
        {"symbol": "AAPL", "signalDate": "2026-05-18", "signal": "SELL"}
      ]
      """.trimIndent()
    mockServer
      .expect(requestTo("http://midgaard-test.invalid/api/ovtlyr-signals/AAPL"))
      .andExpect(method(HttpMethod.GET))
      .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

    // When the client fetches ovtlyr signals
    val result = client.getOvtlyrSignals("AAPL")

    // Then both calls map into the Udgaard domain type
    assertNotNull(result)
    assertEquals(2, result.size)
    assertEquals(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 11), OvtlyrSignalType.BUY), result[0])
    assertEquals(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 18), OvtlyrSignalType.SELL), result[1])
  }

  @Test
  fun `getOvtlyrSignals returns null on server error`() {
    // Given the provider responds with a 5xx
    mockServer
      .expect(requestTo("http://midgaard-test.invalid/api/ovtlyr-signals/BROKE"))
      .andRespond(withServerError())

    // When / Then: the failure surfaces as null, not an exception
    assertNull(client.getOvtlyrSignals("BROKE"))
  }
}
