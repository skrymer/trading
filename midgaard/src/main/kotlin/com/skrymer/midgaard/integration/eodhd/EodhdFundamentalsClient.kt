package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.config.CacheConfiguration.Companion.EODHD_FUNDAMENTALS_CACHE
import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.SafeLogging
import com.skrymer.midgaard.integration.eodhd.dto.EodhdFundamentalsResponse
import com.skrymer.midgaard.service.ApiKeyService
import com.skrymer.midgaard.service.RateLimiterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Caches EODHD fundamentals responses per symbol. Lives as a sibling to
 * `EodhdProvider` so that the `@Cacheable` interception fires across the class
 * boundary — `EodhdProvider`'s `getEarnings` and `getCompanyInfo` both go
 * through this client, so the second call within an ingest cycle is a cache
 * hit and never touches EODHD.
 *
 * EODHD bills 10 weighted quota units per fundamentals request. Without
 * dedup, a 3,128-symbol bulk ingest burns 62k weighted calls just for
 * fundamentals (×2 callers). With dedup, 31k. The 100k/day cap on the
 * All-In-One plan would otherwise be exhausted mid-run.
 *
 * `unless = "#result == null"` keeps null responses (404s, transient 402
 * rate-limit errors) out of the cache so a retry-failed run can pick them
 * up later instead of silently serving the negative cache.
 *
 * The `acquirePermit` call sits inside the cached method body. On cache hits
 * Spring's proxy short-circuits before invoking the body, so we don't waste
 * permits when the previous fetch satisfied this symbol.
 */
@Component
class EodhdFundamentalsClient(
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

    @Cacheable(cacheNames = [EODHD_FUNDAMENTALS_CACHE], key = "#symbol", unless = "#result == null")
    suspend fun fetch(
        symbol: String,
        eodhdSymbol: String,
    ): EodhdFundamentalsResponse? {
        rateLimiterService.acquirePermit(ProviderIds.EODHD)
        return withContext(Dispatchers.IO) {
            runCatching {
                restClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .path("/fundamentals/{symbol}")
                            .queryParam("api_token", apiKey)
                            .queryParam("filter", "General,Highlights,Earnings")
                            .build(eodhdSymbol)
                    }.retrieve()
                    .body(EodhdFundamentalsResponse::class.java)
            }.onFailure { e ->
                SafeLogging.logFetchFailure(logger, "EODHD", "fundamentals", symbol, e)
            }.getOrNull()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EodhdFundamentalsClient::class.java)
    }
}
