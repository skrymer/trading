package com.skrymer.udgaard.data.integration.midgaard

import com.skrymer.udgaard.data.integration.LatestQuote
import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardExchangeRateDto
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardLatestQuoteDto
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardQuoteDto
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardSymbolDto
import com.skrymer.udgaard.data.model.StockQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Client for the Midgaard reference data service.
 *
 * Implements StockProvider to serve as the primary OHLCV + indicator data source.
 * Returns quotes with ATR, ADX, EMAs, and Donchian pre-populated.
 * Trend determination remains in Udgaard (TechnicalIndicatorService).
 */
@Component
class MidgaardClient(
  @param:Value("\${midgaard.base-url:http://localhost:8081}") private val baseUrl: String,
) : StockProvider {
  private val logger = LoggerFactory.getLogger(MidgaardClient::class.java)

  private val restClient: RestClient by lazy {
    RestClient
      .builder()
      .baseUrl(baseUrl)
      .build()
  }

  override fun getDailyAdjustedTimeSeries(symbol: String): List<StockQuote>? {
    try {
      val response = restClient
        .get()
        .uri("/api/quotes/{symbol}", symbol)
        .retrieve()
        .body(object : ParameterizedTypeReference<List<MidgaardQuoteDto>>() {})

      val quotes = response?.map { it.toStockQuote() }
      if (quotes.isNullOrEmpty()) {
        logger.warn("No quotes returned from Midgaard for $symbol")
        return null
      }

      logger.debug("Fetched ${quotes.size} quotes from Midgaard for $symbol")
      return quotes
    } catch (e: Exception) {
      logger.error("Failed to fetch quotes from Midgaard for $symbol: ${e.message}", e)
      return null
    }
  }

  /**
   * Get symbol info (including sector) from Midgaard.
   * Used for sector population during stock ingestion.
   */
  fun getSymbolInfo(symbol: String): MidgaardSymbolDto? {
    try {
      return restClient
        .get()
        .uri("/api/symbols/{symbol}", symbol)
        .retrieve()
        .body(MidgaardSymbolDto::class.java)
    } catch (e: Exception) {
      logger.warn("Failed to fetch symbol info from Midgaard for $symbol: ${e.message}")
      return null
    }
  }

  /**
   * Get all symbols from Midgaard.
   */
  fun getAllSymbols(): List<MidgaardSymbolDto>? {
    try {
      return restClient
        .get()
        .uri("/api/symbols")
        .retrieve()
        .body(object : ParameterizedTypeReference<List<MidgaardSymbolDto>>() {})
    } catch (e: Exception) {
      logger.error("Failed to fetch symbols from Midgaard: ${e.message}", e)
      return null
    }
  }

  override fun getLatestQuote(symbol: String): LatestQuote? {
    try {
      val dto = restClient
        .get()
        .uri("/api/quotes/{symbol}/latest", symbol)
        .retrieve()
        .body(MidgaardLatestQuoteDto::class.java)
      return dto?.let {
        LatestQuote(
          symbol = it.symbol,
          price = it.price,
          previousClose = it.previousClose,
          change = it.change,
          changePercent = it.changePercent,
          volume = it.volume,
          high = it.high,
          low = it.low,
          // The upstream timestamp is Unix seconds from the market's clock; interpret it as
          // a trading-day date in America/New_York so downstream consumers don't have to
          // re-translate timezones. 0 means the provider didn't supply one.
          date = if (it.timestamp > 0) {
            Instant.ofEpochSecond(it.timestamp).atZone(NY_ZONE).toLocalDate()
          } else {
            null
          },
        )
      }
    } catch (e: Exception) {
      logger.warn("Failed to fetch latest quote from Midgaard for $symbol: ${e.message}")
      return null
    }
  }

  override fun getLatestQuotes(symbols: List<String>): Map<String, LatestQuote> =
    runBlocking(Dispatchers.IO) {
      symbols
        .map { symbol ->
          async {
            getLatestQuote(symbol)?.let { symbol to it }
          }
        }.awaitAll()
        .filterNotNull()
        .toMap()
    }

  /**
   * Get current exchange rate from Midgaard (e.g., USD to AUD).
   */
  fun getExchangeRate(from: String, to: String): Double? {
    try {
      val response = restClient
        .get()
        .uri { uriBuilder ->
          uriBuilder
            .path("/api/fx/rate")
            .queryParam("from", from)
            .queryParam("to", to)
            .build()
        }.retrieve()
        .body(MidgaardExchangeRateDto::class.java)
      return response?.rate
    } catch (e: Exception) {
      logger.warn("Failed to fetch exchange rate $from/$to from Midgaard: ${e.message}")
      return null
    }
  }

  /**
   * Get historical exchange rate from Midgaard for a specific date.
   */
  fun getHistoricalExchangeRate(from: String, to: String, date: LocalDate): Double? {
    try {
      val response = restClient
        .get()
        .uri { uriBuilder ->
          uriBuilder
            .path("/api/fx/rate/historical")
            .queryParam("from", from)
            .queryParam("to", to)
            .queryParam("date", date.toString())
            .build()
        }.retrieve()
        .body(MidgaardExchangeRateDto::class.java)
      return response?.rate
    } catch (e: Exception) {
      logger.warn("Failed to fetch historical exchange rate $from/$to for $date from Midgaard: ${e.message}")
      return null
    }
  }

  private companion object {
    private val NY_ZONE: ZoneId = ZoneId.of("America/New_York")
  }
}
