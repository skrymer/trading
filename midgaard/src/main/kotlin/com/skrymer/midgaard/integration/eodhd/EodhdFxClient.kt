package com.skrymer.midgaard.integration.eodhd

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.midgaard.integration.FxCacheNames
import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.SafeLogging
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
import java.time.LocalDate

/**
 * Sibling-class for cross-boundary `@Cacheable` interception. EODHD's `/api/real-time/`
 * (current rate) and `/api/eod/` (historical series) FX endpoints — pair format is
 * `{from}{to}.FOREX` e.g. `EURUSD.FOREX`.
 *
 * Lives in a separate `@Component` so Spring's proxy interception fires when
 * `EodhdProvider.getExchangeRate` and `.getHistoricalExchangeRate` call it.
 *
 * `unless = "#result == null"` keeps null responses out of the cache so a transient
 * error / rate-limit doesn't poison subsequent retries with a cached negative.
 */
@Component
class EodhdFxClient(
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

    @Cacheable(cacheNames = [FxCacheNames.FX_CURRENT], key = "#from + '/' + #to", unless = "#result == null")
    suspend fun fetchCurrent(
        from: String,
        to: String,
    ): Double? {
        rateLimiterService.acquirePermit(ProviderIds.EODHD)
        val pair = "$from$to"
        return withContext(Dispatchers.IO) {
            runCatching {
                restClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .path("/real-time/{pair}.FOREX")
                            .queryParam("api_token", apiKey)
                            .queryParam("fmt", "json")
                            .build(pair)
                    }.retrieve()
                    .body(EodhdRealtimeFxResponse::class.java)
                    ?.close
            }.onFailure { e ->
                SafeLogging.logFetchFailure(logger, "EODHD", "FX current", "$from/$to", e)
            }.getOrNull()
        }
    }

    @Cacheable(cacheNames = [FxCacheNames.FX_HISTORICAL_SERIES], key = "#from + '/' + #to", unless = "#result == null")
    suspend fun fetchHistoricalSeries(
        from: String,
        to: String,
    ): Map<LocalDate, Double>? {
        rateLimiterService.acquirePermit(ProviderIds.EODHD)
        val pair = "$from$to"
        return withContext(Dispatchers.IO) {
            runCatching {
                val rows =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path("/eod/{pair}.FOREX")
                                .queryParam("api_token", apiKey)
                                .queryParam("period", "d")
                                .queryParam("fmt", "json")
                                .build(pair)
                        }.retrieve()
                        .body(Array<EodhdEodFxRow>::class.java)
                if (rows.isNullOrEmpty()) {
                    null
                } else {
                    rows
                        .mapNotNull { row ->
                            val close = row.close ?: return@mapNotNull null
                            runCatching { LocalDate.parse(row.date) to close }.getOrNull()
                        }.toMap()
                        .takeIf { it.isNotEmpty() }
                }
            }.onFailure { e ->
                SafeLogging.logFetchFailure(logger, "EODHD", "FX historical series", "$from/$to", e)
            }.getOrNull()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EodhdFxClient::class.java)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdRealtimeFxResponse(
    val close: Double?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdEodFxRow(
    @param:JsonProperty("date") val date: String,
    @param:JsonProperty("close") val close: Double?,
)
