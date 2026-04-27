package com.skrymer.midgaard.integration.finnhub

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.QuoteProvider
import com.skrymer.midgaard.integration.finnhub.dto.FinnhubQuoteResponse
import com.skrymer.midgaard.model.LatestQuote
import com.skrymer.midgaard.service.ApiKeyService
import com.skrymer.midgaard.service.RateLimiterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class FinnhubProvider(
    private val apiKeyService: ApiKeyService,
    private val rateLimiterService: RateLimiterService,
    @param:Value("\${finnhub.api.baseUrl:https://finnhub.io}") private val baseUrl: String,
) : QuoteProvider {
    private val apiKey: String get() = apiKeyService.getFinnhubApiKey()
    private val restClient: RestClient by lazy {
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(5))
                    setReadTimeout(Duration.ofSeconds(10))
                },
            ).build()
    }

    override suspend fun getLatestQuote(symbol: String): LatestQuote? {
        rateLimiterService.acquirePermit(PROVIDER_ID)
        return withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    restClient
                        .get()
                        .uri("/api/v1/quote") { uriBuilder ->
                            uriBuilder
                                .queryParam("symbol", symbol)
                                .queryParam("token", apiKey)
                                .build()
                        }.retrieve()
                        .toEntity(FinnhubQuoteResponse::class.java)
                        .body
                when {
                    response == null -> null.also { logger.warn("No response from Finnhub for $symbol") }
                    !response.isValid() -> null.also { logger.warn("Invalid Finnhub response for $symbol (price=0 or no timestamp)") }
                    else -> response.toLatestQuote(symbol)
                }
            }.onFailure { e ->
                logger.error("Failed to fetch latest quote from Finnhub for $symbol: ${e.message}")
            }.getOrNull()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FinnhubProvider::class.java)
        private const val PROVIDER_ID = ProviderIds.FINNHUB
    }
}
