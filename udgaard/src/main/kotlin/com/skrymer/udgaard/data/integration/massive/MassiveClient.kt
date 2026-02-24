package com.skrymer.udgaard.data.integration.massive

import com.skrymer.udgaard.data.integration.CompanyInfoProvider
import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.massive.dto.MassiveAggregatesResponse
import com.skrymer.udgaard.data.integration.massive.dto.MassiveTickerOverview
import com.skrymer.udgaard.data.model.CompanyInfo
import com.skrymer.udgaard.data.model.StockQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Massive API client for company information and OHLCV stock data.
 *
 * Uses the Ticker Overview endpoint for SIC codes and market capitalization,
 * and the Aggregates (Bars) endpoint for split-adjusted daily OHLCV data.
 *
 * Documentation: https://massive.cloud/docs
 */
@Component
class MassiveClient(
  @Value("\${massive.api.key:}") private val apiKey: String,
  @Value("\${massive.api.baseUrl:}") private val baseUrl: String,
) : CompanyInfoProvider,
  StockProvider {
  private val restClient: RestClient by lazy {
    RestClient
      .builder()
      .baseUrl(baseUrl)
      .build()
  }

  override suspend fun getCompanyInfo(symbol: String): CompanyInfo? {
    return withContext(Dispatchers.IO) {
      runCatching {
        val response =
          restClient
            .get()
            .uri("/v3/reference/tickers/$symbol") { uriBuilder ->
              uriBuilder
                .queryParam("apiKey", apiKey)
                .build()
            }.retrieve()
            .toEntity(MassiveTickerOverview::class.java)
            .body

        if (response == null) {
          logger.warn("No response received from Massive API for $symbol")
          return@runCatching null
        }

        if (response.hasError()) {
          logger.error("Massive API error for $symbol: ${response.getErrorDescription()}")
          return@runCatching null
        }

        if (!response.isValid()) {
          logger.error("Massive API returned invalid response for $symbol (missing results or ticker)")
          return@runCatching null
        }

        val companyInfo = response.toCompanyInfo()
        if (companyInfo.sectorSymbol == null) {
          logger.warn("Could not map SIC code '${response.results?.sicCode}' to SectorSymbol for $symbol")
        }

        companyInfo
      }.onFailure { e ->
        logger.error("Failed to fetch company info from Massive API for $symbol: ${e.message}", e)
      }.getOrNull()
    }
  }

  override suspend fun getDailyAdjustedTimeSeries(
    symbol: String,
    outputSize: String,
    minDate: LocalDate,
  ): List<StockQuote>? {
    return withContext(Dispatchers.IO) {
      runCatching {
        val from = minDate.format(DATE_FORMAT)
        val to = LocalDate.now().format(DATE_FORMAT)
        val allQuotes = mutableListOf<StockQuote>()
        var nextUrl: String? = null

        // First page
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

        if (response == null) {
          logger.warn("No response received from Massive API aggregates for $symbol")
          return@runCatching null
        }

        if (response.hasError()) {
          logger.error("Massive API aggregates error for $symbol: ${response.getErrorDescription()}")
          return@runCatching null
        }

        if (!response.isValid()) {
          logger.error("Massive API returned invalid aggregates response for $symbol (missing results or ticker)")
          return@runCatching null
        }

        allQuotes.addAll(response.toStockQuotes(symbol, minDate))
        nextUrl = response.nextUrl

        // Paginate through remaining results
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

          allQuotes.addAll(pageResponse.toStockQuotes(symbol, minDate))
          nextUrl = pageResponse.nextUrl
        }

        allQuotes.sortedBy { it.date }
      }.onFailure { e ->
        logger.error("Failed to fetch aggregates from Massive API for $symbol: ${e.message}", e)
      }.getOrNull()
    }
  }

  companion object {
    private val logger = LoggerFactory.getLogger(MassiveClient::class.java)
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private const val MAX_RESULTS_PER_PAGE = 50000
  }
}
