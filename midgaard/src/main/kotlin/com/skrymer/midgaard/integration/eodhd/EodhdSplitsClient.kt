package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.SafeLogging
import com.skrymer.midgaard.integration.eodhd.dto.EodhdSplitDto
import com.skrymer.midgaard.model.Split
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
 * Fetches a symbol's split history from EODHD's `GET /api/splits/{symbol}` endpoint — the corporate
 * actions the cumulative split factor k(t) is built from (ADR 0027). Rows that can't yield a usable
 * ratio are dropped (see [EodhdSplitDto.toSplit]); the result is sorted by ex-date ascending.
 */
@Component
class EodhdSplitsClient(
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

    suspend fun fetch(
        symbol: String,
        eodhdSymbol: String,
        minDate: LocalDate = LocalDate.of(2000, 1, 1),
    ): List<Split>? {
        rateLimiterService.acquirePermit(ProviderIds.EODHD)
        return withContext(Dispatchers.IO) {
            runCatching {
                val rows =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path("/splits/{symbol}")
                                .queryParam("api_token", apiKey)
                                .queryParam("fmt", "json")
                                .queryParam("from", minDate.toString())
                                .build(eodhdSymbol)
                        }.retrieve()
                        .body(SPLIT_LIST_TYPE)
                rows
                    .orEmpty()
                    .mapNotNull { it.toSplit(symbol, minDate) }
                    .sortedBy { it.exDate }
            }.onFailure { e ->
                SafeLogging.logFetchFailure(logger, "EODHD", "splits", symbol, e)
            }.getOrNull()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EodhdSplitsClient::class.java)
        private val SPLIT_LIST_TYPE = object : ParameterizedTypeReference<List<EodhdSplitDto>>() {}
    }
}
