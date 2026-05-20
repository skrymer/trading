package com.skrymer.midgaard.integration.ovtlyr

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/**
 * ovtlyr.com session credentials. ovtlyr exposes no API key — requests are authenticated
 * by replaying a logged-in browser session (cookies) plus a static project header.
 */
data class OvtlyrCredentials(
    val cookieUserId: String,
    val cookieToken: String,
    val projectId: String,
)

/**
 * Thin HTTP client for ovtlyr.com's private stock-information endpoint. One call returns a
 * symbol's full history; [getStockInformation] hands back the raw payload — extraction of
 * buy/sell signals is [OvtlyrPayloadMapper]'s job. Call pacing is the caller's responsibility
 * (`OvtlyrBackfillService` rate-limits the loop).
 */
@Component
class OvtlyrClient(
    @param:Value("\${ovtlyr.stockinformation.baseUrl:}") private val baseUrl: String,
) {
    private val restClient: RestClient by lazy {
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(10))
                    setReadTimeout(Duration.ofSeconds(30))
                },
            ).build()
    }

    fun getStockInformation(
        symbol: String,
        credentials: OvtlyrCredentials,
    ): OvtlyrPayloadDto? =
        runCatching {
            restClient
                .post()
                .header("ProjectId", credentials.projectId)
                .header("Accept", "application/json")
                .cookie("UserId", credentials.cookieUserId)
                .cookie("Token", credentials.cookieToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "stockSymbol" to symbol,
                        "period" to "All",
                        "page_index" to 0,
                        "page_size" to MAX_PAGE_SIZE,
                    ),
                ).retrieve()
                .toEntity(OvtlyrPayloadDto::class.java)
                .body
        }.onFailure { e ->
            // Log the HTTP status when the failure carries one — a status code is not a secret
            // and makes throttle/auth failures diagnosable (429 vs 401 vs a connection error).
            // Never log the throwable or its message: those can carry the session cookies.
            val detail = if (e is RestClientResponseException) "HTTP ${e.statusCode.value()}" else e.javaClass.simpleName
            logger.error("Ovtlyr fetch failed for $symbol: $detail")
        }.getOrNull()

    companion object {
        // ovtlyr paginates; one oversized page pulls a symbol's entire history in a single call.
        private const val MAX_PAGE_SIZE = 20000
        private val logger = LoggerFactory.getLogger(OvtlyrClient::class.java)
    }
}
