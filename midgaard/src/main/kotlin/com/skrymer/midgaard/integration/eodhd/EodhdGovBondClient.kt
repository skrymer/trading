package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.SafeLogging
import com.skrymer.midgaard.integration.eodhd.dto.EodhdBarDto
import com.skrymer.midgaard.model.TreasuryYield
import com.skrymer.midgaard.service.ApiKeyService
import com.skrymer.midgaard.service.RateLimiterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDate

/**
 * Fetches an EODHD government-bond yield series (e.g. `US3M.GBOND` — the 3-month T-bill).
 *
 * The gov-bond `/eod/{ticker}` endpoint returns the standard OHLCV bar shape where `close`
 * is the yield in percent. The ticker is already in EODHD form, so it is used verbatim on the
 * path — unlike equity symbols it must not be rewritten to `.US`. See ADR 0016.
 */
@Component
class EodhdGovBondClient(
    private val apiKeyService: ApiKeyService,
    private val rateLimiterService: RateLimiterService,
    @param:Value("\${eodhd.api.baseUrl}") private val baseUrl: String,
) {
    private val apiKey: String get() = apiKeyService.getEodhdApiKey()

    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(5))
                    setReadTimeout(Duration.ofSeconds(30))
                },
            ).build()

    suspend fun fetchYields(
        maturity: String,
        ticker: String,
        minDate: LocalDate = LocalDate.of(2000, 1, 1),
    ): List<TreasuryYield>? {
        rateLimiterService.acquirePermit(ProviderIds.EODHD)
        return withContext(Dispatchers.IO) {
            runCatching {
                val bars =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path("/eod/{ticker}")
                                .queryParam("api_token", apiKey)
                                .queryParam("fmt", "json")
                                .queryParam("from", minDate.toString())
                                .build(ticker)
                        }.retrieve()
                        .body(BAR_LIST_TYPE)
                TreasuryYieldMapper.toYields(maturity, bars ?: emptyList(), minDate)
            }.onFailure { e ->
                SafeLogging.logFetchFailure(logger, "EODHD", "gov-bond yields", ticker, e)
            }.getOrNull()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EodhdGovBondClient::class.java)
        private val BAR_LIST_TYPE = object : ParameterizedTypeReference<List<EodhdBarDto>>() {}
    }
}
