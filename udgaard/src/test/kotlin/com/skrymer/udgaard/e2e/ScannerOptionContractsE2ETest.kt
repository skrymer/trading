package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.portfolio.integration.options.OptionContract
import com.skrymer.udgaard.portfolio.integration.options.OptionsDataProvider
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.scanner.dto.OptionContractResponse
import com.skrymer.udgaard.scanner.dto.OptionContractsRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Covers POST /api/scanner/option-contracts over real HTTP. The endpoint's logic lives in the
 * controller (delta filter 0.70..0.90, lowest expiration, closest to 0.80 delta target). Pinned:
 * given a controlled `OptionsDataProvider` response, the controller picks the right contract and
 * its JSON shape round-trips. The provider is mocked so the test doesn't depend on Midgaard or
 * a network round-trip.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannerOptionContractsE2ETest : AbstractIntegrationTest() {
  @MockitoBean
  private lateinit var optionsDataProvider: OptionsDataProvider

  private val scanDate = LocalDate.of(2024, 5, 8)
  private val expirationNear = LocalDate.of(2024, 6, 21)
  private val expirationFar = LocalDate.of(2024, 7, 19)

  @BeforeEach
  fun stubProvider() {
    whenever(optionsDataProvider.getHistoricalOptions(any(), anyOrNull())).thenReturn(
      listOf(
        // delta 0.55 — outside 0.70..0.90 band, ignored
        contract("low", 145.0, expirationNear, delta = 0.55, price = 6.0),
        // both inside the delta band on expirationNear; 0.80 is the target → pick this one
        contract("near-080", 150.0, expirationNear, delta = 0.80, price = 7.5, openInterest = 4321),
        contract("near-075", 155.0, expirationNear, delta = 0.75, price = 6.0),
        // also valid but on a later expiration → loses on the lowest-expiration tiebreaker
        contract("far-080", 152.0, expirationFar, delta = 0.80, price = 9.0),
      ),
    )
  }

  @Test
  fun `POST option-contracts picks the closest-to-target-delta contract on the lowest expiration`() {
    // Given: stock at $160 → intrinsic = max(160 - 150, 0) = $10, extrinsic = 7.5 - 10 = -2.5
    val request = OptionContractsRequest(
      symbols = listOf("AAPL"),
      stockPrices = mapOf("AAPL" to 160.0),
      date = scanDate.toString(),
    )

    // When
    val response = restTemplate.exchange(
      "/api/scanner/option-contracts",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      object : ParameterizedTypeReference<Map<String, OptionContractResponse>>() {},
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(1, body.size)
    val pick = body["AAPL"]!!
    assertEquals("AAPL", pick.symbol)
    assertEquals(150.0, pick.strike, "0.80-delta on expirationNear wins over 0.75-delta and far-expiration alternatives")
    assertEquals(expirationNear.toString(), pick.expiration)
    assertEquals(7.5, pick.price)
    assertEquals(0.80, pick.delta)
    assertEquals(4321, pick.openInterest)
    assertEquals(10.0, pick.intrinsic, "max(stockPrice - strike, 0) = max(160 - 150, 0)")
    assertEquals(-2.5, pick.extrinsic, "price - intrinsic = 7.5 - 10 = -2.5")
  }

  @Test
  fun `POST option-contracts returns 400 when symbol count exceeds the per-request cap`() {
    // Given: 21 symbols, one over MAX_OPTION_SYMBOLS = 20
    val symbols = (1..21).map { "S$it" }
    val request = OptionContractsRequest(
      symbols = symbols,
      stockPrices = symbols.associateWith { 100.0 },
      date = scanDate.toString(),
    )

    // When
    val response = restTemplate.exchange(
      "/api/scanner/option-contracts",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      String::class.java,
    )

    // Then: was a silent take(20) before — now rejects loudly so the client knows to chunk.
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `POST option-contracts skips symbols whose stock price is missing`() {
    // Given: request includes a symbol with no stockPrice entry
    val request = OptionContractsRequest(
      symbols = listOf("AAPL"),
      stockPrices = emptyMap(),
      date = scanDate.toString(),
    )

    // When
    val response = restTemplate.exchange(
      "/api/scanner/option-contracts",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      object : ParameterizedTypeReference<Map<String, OptionContractResponse>>() {},
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(emptyMap<String, OptionContractResponse>(), response.body)
  }

  private fun contract(
    suffix: String,
    strike: Double,
    expiration: LocalDate,
    delta: Double,
    price: Double,
    openInterest: Int? = null,
  ) = OptionContract(
    contractId = "AAPL-$suffix",
    symbol = "AAPL",
    strike = strike,
    expiration = expiration,
    optionType = OptionType.CALL,
    date = scanDate,
    price = price,
    delta = delta,
    openInterest = openInterest,
  )
}
