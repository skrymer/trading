package com.skrymer.midgaard.integration.ovtlyr

import com.fasterxml.jackson.databind.ObjectMapper
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
    private val objectMapper: ObjectMapper,
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
    ): OvtlyrPayloadDto? {
        val body = fetchBody(symbol, credentials) ?: return null
        return when {
            // ovtlyr answers an uncovered symbol with a 200 + an "Invalid Stock" envelope, not
            // the data shape. That's a normal coverage gap, not a failure — debug, not error.
            isInvalidStockResponse(body) -> {
                logger.debug("Ovtlyr has no coverage for $symbol (Invalid Stock)")
                null
            }
            else -> parsePayload(symbol, body)
        }
    }

    private fun parsePayload(
        symbol: String,
        body: String,
    ): OvtlyrPayloadDto? =
        try {
            objectMapper.readValue(body, OvtlyrPayloadDto::class.java)
        } catch (e: Exception) {
            // A 200 whose body isn't the expected JSON — typically an HTML block/login page.
            // The snippet makes that immediately visible.
            logger.error("Ovtlyr returned an unparseable body for $symbol (${e.javaClass.simpleName}): ${snippet(body)}")
            null
        }

    /**
     * True when the body is ovtlyr's `resultDetail: "Invalid Stock"` error envelope. A
     * non-JSON body (e.g. an HTML login page) is not JSON-parseable here, so it returns
     * false and falls through to [parsePayload] — which logs it as a genuine failure.
     */
    private fun isInvalidStockResponse(body: String): Boolean =
        runCatching {
            objectMapper
                .readTree(body)
                .path("resultDetail")
                .asText("")
                .equals("Invalid Stock", ignoreCase = true)
        }.getOrDefault(false)

    private fun fetchBody(
        symbol: String,
        credentials: OvtlyrCredentials,
    ): String? {
        val body = requestBody(symbol, credentials)
        if (body.isNullOrBlank()) {
            // A null body here means the request failed — requestBody already logged the cause.
            if (body != null) logger.error("Ovtlyr returned an empty body for $symbol")
            return null
        }
        return body
    }

    private fun requestBody(
        symbol: String,
        credentials: OvtlyrCredentials,
    ): String? =
        try {
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
                .body(String::class.java)
        } catch (e: RestClientResponseException) {
            // 4xx/5xx — log the status and a snippet of ovtlyr's error body (a response
            // body never carries our request cookies; response headers, which can, are not logged).
            logger.error("Ovtlyr fetch failed for $symbol: HTTP ${e.statusCode.value()} — ${snippet(e.responseBodyAsString)}")
            null
        } catch (e: Exception) {
            // Transport failure (timeout, connection) — exception type only, never the message.
            logger.error("Ovtlyr fetch failed for $symbol: ${e.javaClass.simpleName}")
            null
        }

    private fun snippet(body: String): String = body.replace(Regex("\\s+"), " ").trim().take(BODY_LOG_LIMIT)

    companion object {
        // ovtlyr paginates; one oversized page pulls a symbol's entire history in a single call.
        private const val MAX_PAGE_SIZE = 20000

        // Cap on the response-body excerpt written to logs on a failure.
        private const val BODY_LOG_LIMIT = 400
        private val logger = LoggerFactory.getLogger(OvtlyrClient::class.java)
    }
}
