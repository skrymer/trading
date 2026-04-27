package com.skrymer.midgaard.integration.edgar

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.SafeLogging
import com.skrymer.midgaard.integration.edgar.dto.EdgarSubmissionDto
import com.skrymer.midgaard.service.RateLimiterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Reads SIC codes from SEC EDGAR's `submissions/CIK##########.json` endpoint.
 *
 * Used as a fallback when EODHD's fundamentals return `"NA"` for delisted
 * issuers' sector — EDGAR keeps SIC codes for delisted companies indefinitely.
 *
 * SEC etiquette:
 *  - 10 req/sec cap (enforced via `RateLimiterService` on `ProviderIds.EDGAR`)
 *  - User-Agent header must identify the application + contact email or the
 *    request is automatically blocked. Hard-coded via `edgar.api.userAgent`.
 *
 * 404 is a routine outcome — not every CIK has a current submission JSON
 * (recently delisted issuers, foreign filers without 10-K obligations). We
 * return null and let callers fall back to the default sector.
 */
@Component
class EdgarClient(
    private val rateLimiterService: RateLimiterService,
    @Value("\${edgar.api.baseUrl}") baseUrl: String,
    @param:Value("\${edgar.api.userAgent}") private val userAgent: String,
) {
    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .defaultHeader("User-Agent", userAgent)
            .defaultHeader("Accept", "application/json")
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(5))
                    setReadTimeout(Duration.ofSeconds(15))
                },
            ).build()

    /**
     * Returns the SIC submission for the given CIK, or null if the CIK has no
     * EDGAR record (404), the request fails, or the response lacks a SIC code.
     *
     * The CIK is zero-padded to 10 digits per SEC's URL convention.
     */
    suspend fun getSubmission(cik: String): EdgarSubmissionDto? {
        if (userAgent.isBlank()) {
            error("edgar.api.userAgent must be set — SEC blocks requests without identifying contact info")
        }
        rateLimiterService.acquirePermit(ProviderIds.EDGAR)
        val padded = cik.trim().padStart(CIK_LENGTH, '0')
        return withContext(Dispatchers.IO) {
            runCatching {
                restClient
                    .get()
                    .uri("/submissions/CIK{cik}.json", padded)
                    .retrieve()
                    .body(EdgarSubmissionDto::class.java)
            }.onFailure { e ->
                SafeLogging.logFetchFailure(logger, "EDGAR", "submission", cik, e)
            }.getOrNull()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EdgarClient::class.java)
        private const val CIK_LENGTH = 10
    }
}
