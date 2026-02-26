package com.skrymer.udgaard.data.integration.midgaard

import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardQuoteDto
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardSymbolDto
import com.skrymer.udgaard.data.model.StockQuote
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Client for the Midgaard reference data service.
 *
 * Implements StockProvider to serve as the primary OHLCV + indicator data source.
 * Returns quotes with ATR, ADX, EMAs, and Donchian pre-populated.
 * Trend determination remains in Udgaard (TechnicalIndicatorService).
 */
@Component
class MidgaardClient(
  @Value("\${midgaard.base-url:http://localhost:8081}") private val baseUrl: String,
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
}
