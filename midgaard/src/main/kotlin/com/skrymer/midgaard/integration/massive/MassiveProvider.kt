package com.skrymer.midgaard.integration.massive

import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.integration.massive.dto.MassiveAggregatesResponse
import com.skrymer.midgaard.model.RawBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class MassiveProvider(
    @Value("\${massive.api.key:}") private val apiKey: String,
    @Value("\${massive.api.baseUrl:}") private val baseUrl: String,
) : OhlcvProvider {
    private val restClient: RestClient by lazy {
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .build()
    }

    override suspend fun getDailyBars(
        symbol: String,
        outputSize: String,
        minDate: LocalDate,
    ): List<RawBar>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = fetchFirstPage(symbol, minDate) ?: return@runCatching null
                val firstBars = response.toRawBars(symbol, minDate)
                val remainingBars = fetchRemainingPages(symbol, response.nextUrl, minDate)
                (firstBars + remainingBars).sortedBy { it.date }
            }.onFailure { e ->
                logger.error("Failed to fetch aggregates from Massive API for $symbol: ${e.message}", e)
            }.getOrNull()
        }

    private fun fetchFirstPage(
        symbol: String,
        minDate: LocalDate,
    ): MassiveAggregatesResponse? {
        val from = minDate.format(DATE_FORMAT)
        val to = LocalDate.now().format(DATE_FORMAT)
        val response =
            restClient
                .get()
                .uri("/v2/aggs/ticker/$symbol/range/1/day/$from/$to") { uriBuilder ->
                    uriBuilder
                        .queryParam("adjusted", true)
                        .queryParam("limit", MAX_RESULTS_PER_PAGE)
                        .queryParam("apiKey", apiKey)
                        .build()
                }.retrieve()
                .toEntity(MassiveAggregatesResponse::class.java)
                .body
        return when {
            response == null -> null.also { logger.warn("No response from Massive API for $symbol") }
            response.hasError() -> null.also { logger.error("Massive API error for $symbol: ${response.getErrorDescription()}") }
            !response.isValid() -> null.also { logger.error("Invalid Massive API response for $symbol") }
            else -> response
        }
    }

    private fun fetchRemainingPages(
        symbol: String,
        initialNextUrl: String?,
        minDate: LocalDate,
    ): List<RawBar> {
        val allBars = mutableListOf<RawBar>()
        var nextUrl = initialNextUrl
        while (nextUrl != null) {
            val pageResponse =
                restClient
                    .get()
                    .uri(nextUrl) { uriBuilder ->
                        uriBuilder
                            .queryParam("apiKey", apiKey)
                            .build()
                    }.retrieve()
                    .toEntity(MassiveAggregatesResponse::class.java)
                    .body
            if (pageResponse == null || pageResponse.hasError() || !pageResponse.isValid()) {
                logger.warn("Stopping pagination for $symbol: invalid page response")
                break
            }
            allBars.addAll(pageResponse.toRawBars(symbol, minDate))
            nextUrl = pageResponse.nextUrl
        }
        return allBars
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MassiveProvider::class.java)
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private const val MAX_RESULTS_PER_PAGE = 50000
    }
}
