package com.skrymer.midgaard.integration.alphavantage

import com.skrymer.midgaard.integration.FxCacheNames
import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.SafeLogging
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageCurrencyExchangeRate
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageFxDaily
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
 * Sibling-class for cross-boundary `@Cacheable` interception. AlphaVantage's
 * `CURRENCY_EXCHANGE_RATE` (current rate) and `FX_DAILY` (historical series).
 *
 * Lives in a separate `@Component` so Spring's proxy interception fires when
 * `AlphaVantageProvider.getExchangeRate` and `.getHistoricalExchangeRate` call it.
 *
 * `unless = "#result == null"` keeps null responses out of the cache so a transient
 * error doesn't poison subsequent retries with a cached negative.
 */
@Component
class AlphaVantageFxClient(
    private val apiKeyService: ApiKeyService,
    private val rateLimiterService: RateLimiterService,
    @param:Value("\${alphavantage.api.baseUrl}") private val baseUrl: String,
) {
    private val apiKey: String get() = apiKeyService.getAlphaVantageApiKey()

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
        rateLimiterService.acquirePermit(ProviderIds.ALPHAVANTAGE)
        return withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .queryParam("function", FUNCTION_CURRENCY_EXCHANGE_RATE)
                                .queryParam("from_currency", from)
                                .queryParam("to_currency", to)
                                .queryParam("apikey", apiKey)
                                .build()
                        }.retrieve()
                        .body(AlphaVantageCurrencyExchangeRate::class.java)
                when {
                    response == null -> null
                    response.hasError() -> {
                        logger.error("AlphaVantage FX current error for $from/$to: ${response.getErrorDescription()}")
                        null
                    }
                    !response.isValid() -> null
                    else -> response.toRate()
                }
            }.onFailure { e ->
                SafeLogging.logFetchFailure(logger, "AlphaVantage", "FX current", "$from/$to", e)
            }.getOrNull()
        }
    }

    @Cacheable(cacheNames = [FxCacheNames.FX_HISTORICAL_SERIES], key = "#from + '/' + #to", unless = "#result == null")
    suspend fun fetchHistoricalSeries(
        from: String,
        to: String,
    ): Map<LocalDate, Double>? {
        rateLimiterService.acquirePermit(ProviderIds.ALPHAVANTAGE)
        return withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .queryParam("function", FUNCTION_FX_DAILY)
                                .queryParam("from_symbol", from)
                                .queryParam("to_symbol", to)
                                .queryParam("outputsize", "full")
                                .queryParam("apikey", apiKey)
                                .build()
                        }.retrieve()
                        .body(AlphaVantageFxDaily::class.java)
                when {
                    response == null -> null
                    response.hasError() -> {
                        logger.error(
                            "AlphaVantage FX historical series error for $from/$to: ${response.getErrorDescription()}",
                        )
                        null
                    }
                    else -> response.toSeriesMap()
                }
            }.onFailure { e ->
                SafeLogging.logFetchFailure(logger, "AlphaVantage", "FX historical series", "$from/$to", e)
            }.getOrNull()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AlphaVantageFxClient::class.java)
        private const val FUNCTION_CURRENCY_EXCHANGE_RATE = "CURRENCY_EXCHANGE_RATE"
        private const val FUNCTION_FX_DAILY = "FX_DAILY"
    }
}
