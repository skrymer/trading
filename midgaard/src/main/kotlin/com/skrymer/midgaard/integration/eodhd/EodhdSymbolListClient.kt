package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.SafeLogging
import com.skrymer.midgaard.integration.eodhd.dto.EodhdDelistedSymbolDto
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

/**
 * Reads EODHD's delisted-symbol catalogue.
 *
 * Lives as a sibling to `EodhdProvider` rather than as a method on it because
 * the catalogue endpoint has different response shape and cost characteristics
 * (one big array, billed at ~1 weighted call) and is only needed for
 * survivorship-bias bootstrapping — not the per-symbol ingest hot path.
 *
 * Per-symbol enrichment (CIK + delistedDate) goes through the existing
 * `EodhdFundamentalsClient.fetch()` so we hit the in-process cache and don't
 * pay 10 weighted calls twice for the same symbol.
 */
@Component
class EodhdSymbolListClient(
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
                    // The delisted catalogue can be a few MB on the US exchange — generous read timeout.
                    setReadTimeout(Duration.ofSeconds(60))
                },
            ).build()

    /**
     * Returns all symbols ever delisted from the US exchange, or null on HTTP
     * failure. The list is large (~7,500 rows) — caller is expected to filter
     * by `type = "Common Stock"` and a liquidity threshold before enriching.
     */
    suspend fun getDelistedSymbols(): List<EodhdDelistedSymbolDto>? {
        rateLimiterService.acquirePermit(PROVIDER_ID)
        return withContext(Dispatchers.IO) {
            runCatching {
                restClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .path(PATH_DELISTED_LIST)
                            .queryParam(QUERY_API_TOKEN, apiKey)
                            .queryParam(QUERY_FMT, FMT_JSON)
                            .queryParam(QUERY_DELISTED, DELISTED_FLAG)
                            .build()
                    }.retrieve()
                    .body(SYMBOL_LIST_TYPE)
            }.onFailure { e -> SafeLogging.logFetchFailure(logger, "EODHD", "delisted symbol list", "US", e) }.getOrNull()
        }
    }

    companion object {
        private const val PROVIDER_ID = ProviderIds.EODHD
        private val logger = LoggerFactory.getLogger(EodhdSymbolListClient::class.java)
        private val SYMBOL_LIST_TYPE = object : ParameterizedTypeReference<List<EodhdDelistedSymbolDto>>() {}
        private const val PATH_DELISTED_LIST = "/exchange-symbol-list/US"
        private const val QUERY_API_TOKEN = "api_token"
        private const val QUERY_FMT = "fmt"
        private const val QUERY_DELISTED = "delisted"
        private const val FMT_JSON = "json"
        private const val DELISTED_FLAG = "1"
    }
}
