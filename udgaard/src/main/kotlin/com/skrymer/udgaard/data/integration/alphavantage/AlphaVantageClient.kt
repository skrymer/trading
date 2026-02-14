package com.skrymer.udgaard.data.integration.alphavantage

import com.skrymer.udgaard.data.integration.FundamentalDataProvider
import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.TechnicalIndicatorProvider
import com.skrymer.udgaard.data.integration.alphavantage.dto.AlphaVantageADX
import com.skrymer.udgaard.data.integration.alphavantage.dto.AlphaVantageATR
import com.skrymer.udgaard.data.integration.alphavantage.dto.AlphaVantageCompanyOverview
import com.skrymer.udgaard.data.integration.alphavantage.dto.AlphaVantageEarnings
import com.skrymer.udgaard.data.integration.alphavantage.dto.AlphaVantageTimeSeriesDailyAdjusted
import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.SectorSymbol
import com.skrymer.udgaard.data.model.StockQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate

/**
 * Alpha Vantage implementation of stock, technical indicator, and fundamental data providers
 *
 * Documentation: https://www.alphavantage.co/documentation/
 *
 * Provides:
 * - Stock price data (adjusted for splits and dividends)
 * - Technical indicators (ATR)
 * - Fundamental data (earnings history)
 *
 * API Rate Limits:
 * - Free tier: 25 requests per day
 * - Premium: Higher limits available
 *
 * Note: Use sparingly and consider caching responses
 */
@Component
open class AlphaVantageClient(
  @Value("\${alphavantage.api.key:}") private val apiKey: String,
  @Value("\${alphavantage.api.baseUrl}") private val baseUrl: String,
) : StockProvider,
  TechnicalIndicatorProvider,
  FundamentalDataProvider {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(AlphaVantageClient::class.java)
    private const val FUNCTION_DAILY_ADJUSTED = "TIME_SERIES_DAILY_ADJUSTED"
    private const val FUNCTION_ATR = "ATR"
    private const val FUNCTION_ADX = "ADX"
    private const val FUNCTION_EARNINGS = "EARNINGS"
    private const val FUNCTION_OVERVIEW = "OVERVIEW"
    private const val OUTPUT_SIZE_FULL = "full"
    private const val OUTPUT_SIZE_COMPACT = "compact" // Last 100 data points
  }

  private val restClient: RestClient =
    RestClient
      .builder()
      .baseUrl(baseUrl)
      .build()

  /**
   * Get adjusted daily time series data for a stock symbol
   *
   * This is the PREFERRED method for stock data as it includes:
   * - Prices adjusted for stock splits and dividend events
   * - Accurate historical data for backtesting
   * - Volume information
   *
   * Uses adjusted close price which accounts for corporate actions,
   * ensuring accurate portfolio value calculations over time.
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @param outputSize "compact" for last 100 data points, "full" for 20+ years of historical data
   * @return List of stock quotes with adjusted prices and volume data, or null if request fails
   */
  override suspend fun getDailyAdjustedTimeSeries(
    symbol: String,
    outputSize: String,
    minDate: LocalDate,
  ): List<StockQuote>? {
    return withContext(Dispatchers.IO) {
      runCatching {
        val url = "$baseUrl?function=$FUNCTION_DAILY_ADJUSTED&symbol=$symbol&outputsize=$outputSize&apikey=$apiKey"

        val response =
          restClient
            .get()
            .uri { uriBuilder ->
              uriBuilder
                .queryParam("function", FUNCTION_DAILY_ADJUSTED)
                .queryParam("symbol", symbol)
                .queryParam("outputsize", outputSize)
                .queryParam("apikey", apiKey)
                .build()
            }.retrieve()
            .toEntity(AlphaVantageTimeSeriesDailyAdjusted::class.java)
            .body

        if (response == null) {
          logger.warn("No response received from Alpha Vantage for $symbol")
          return@runCatching null
        }

        // Check if response contains an error
        if (response.hasError()) {
          logger.error("Alpha Vantage API error for $symbol: ${response.getErrorDescription()}")
          return@runCatching null
        }

        // Check if response is valid (has required data)
        if (!response.isValid()) {
          logger.error("Alpha Vantage API returned invalid response for $symbol (missing Meta Data or Time Series)")
          return@runCatching null
        }

        response.toStockQuotes(minDate)
      }.onFailure { e ->
        logger.error("Failed to fetch adjusted data from Alpha Vantage for $symbol: ${e.message}", e)
      }.getOrNull()
    }
  }

  /**
   * Get ATR (Average True Range) technical indicator for a stock symbol
   *
   * Cached to reduce API calls - ATR data is historical and doesn't change.
   * Cache key includes symbol, interval, and time period.
   *
   * @param symbol Stock symbol (e.g., "AAPL", "QQQ", "TQQQ")
   * @param interval Time interval: "daily", "weekly", "monthly"
   * @param timePeriod Number of data points used to calculate ATR (default: 14)
   * @return Map of date to ATR value, or null if request fails
   */
  override suspend fun getATR(
    symbol: String,
    interval: String,
    timePeriod: Int,
    minDate: LocalDate,
  ): Map<LocalDate, Double>? {
    return withContext(Dispatchers.IO) {
      runCatching {
        val url = "$baseUrl?function=$FUNCTION_ATR&symbol=$symbol&interval=$interval&time_period=$timePeriod&apikey=$apiKey"

        val response =
          restClient
            .get()
            .uri { uriBuilder ->
              uriBuilder
                .queryParam("function", FUNCTION_ATR)
                .queryParam("symbol", symbol)
                .queryParam("interval", interval)
                .queryParam("time_period", timePeriod)
                .queryParam("apikey", apiKey)
                .build()
            }.retrieve()
            .toEntity(AlphaVantageATR::class.java)
            .body

        if (response == null) {
          logger.warn("No response received from Alpha Vantage ATR for $symbol")
          return@runCatching null
        }

        // Check if response contains an error
        if (response.hasError()) {
          logger.error("Alpha Vantage API error for ATR $symbol: ${response.getErrorDescription()}")
          return@runCatching null
        }

        // Check if response is valid (has required data)
        if (!response.isValid()) {
          logger.error("Alpha Vantage API returned invalid ATR response for $symbol (missing Meta Data or Technical Analysis)")
          return@runCatching null
        }

        response.toATRMap(minDate)
      }.onFailure { e ->
        logger.error("Failed to fetch ATR from Alpha Vantage for $symbol: ${e.message}", e)
        logger.error("Stack trace:", e)
      }.getOrNull()
    }
  }

  /**
   * Get ADX (Average Directional Index) technical indicator values
   *
   * ADX measures the strength of a trend (not the direction).
   * Values range from 0 to 100:
   * - 0-25: Absent or weak trend
   * - 25-50: Strong trend
   * - 50-75: Very strong trend
   * - 75-100: Extremely strong trend
   *
   * Commonly used to identify trending markets vs ranging markets.
   * Higher values indicate stronger trends (either up or down).
   *
   * @param symbol Stock symbol (e.g., "AAPL", "QQQ", "TQQQ")
   * @param interval Time interval: "daily", "weekly", "monthly"
   * @param timePeriod Number of data points used to calculate ADX (default: 14)
   * @return Map of date to ADX value, or null if request fails
   */
  override suspend fun getADX(
    symbol: String,
    interval: String,
    timePeriod: Int,
    minDate: LocalDate,
  ): Map<LocalDate, Double>? {
    return withContext(Dispatchers.IO) {
      runCatching {
        val url = "$baseUrl?function=$FUNCTION_ADX&symbol=$symbol&interval=$interval&time_period=$timePeriod&apikey=$apiKey"

        val response =
          restClient
            .get()
            .uri { uriBuilder ->
              uriBuilder
                .queryParam("function", FUNCTION_ADX)
                .queryParam("symbol", symbol)
                .queryParam("interval", interval)
                .queryParam("time_period", timePeriod)
                .queryParam("apikey", apiKey)
                .build()
            }.retrieve()
            .toEntity(AlphaVantageADX::class.java)
            .body

        if (response == null) {
          logger.warn("No response received from Alpha Vantage ADX for $symbol")
          return@runCatching null
        }

        // Check if response contains an error
        if (response.hasError()) {
          logger.error("Alpha Vantage API error for ADX $symbol: ${response.getErrorDescription()}")
          return@runCatching null
        }

        // Check if response is valid (has required data)
        if (!response.isValid()) {
          logger.error("Alpha Vantage API returned invalid ADX response for $symbol (missing Meta Data or Technical Analysis)")
          return@runCatching null
        }

        response.toADXMap(minDate)
      }.onFailure { e ->
        logger.error("Failed to fetch ADX from Alpha Vantage for $symbol: ${e.message}", e)
        logger.error("Stack trace:", e)
      }.getOrNull()
    }
  }

  /**
   * Get earnings history for a stock symbol
   *
   * Returns annual and quarterly earnings data including:
   * - Reported earnings per share (EPS)
   * - Estimated EPS
   * - Earnings surprises
   * - Report dates and times
   *
   * Used by strategies that need to exit positions before earnings announcements
   * to avoid earnings-related volatility.
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @return List of quarterly earnings, or null if request fails
   */
  override suspend fun getEarnings(symbol: String): List<Earning>? {
    return withContext(Dispatchers.IO) {
      runCatching {
        val url = "$baseUrl?function=$FUNCTION_EARNINGS&symbol=$symbol&apikey=$apiKey"

        val response =
          restClient
            .get()
            .uri { uriBuilder ->
              uriBuilder
                .queryParam("function", FUNCTION_EARNINGS)
                .queryParam("symbol", symbol)
                .queryParam("apikey", apiKey)
                .build()
            }.retrieve()
            .toEntity(AlphaVantageEarnings::class.java)
            .body

        if (response == null) {
          logger.warn("No response received from Alpha Vantage earnings for $symbol")
          return@runCatching null
        }

        // Check if response contains an error
        if (response.hasError()) {
          logger.error("Alpha Vantage API error for earnings $symbol: ${response.getErrorDescription()}")
          return@runCatching null
        }

        // Check if response is valid (has required data)
        if (!response.isValid()) {
          logger.error("Alpha Vantage API returned invalid earnings response for $symbol (missing symbol or quarterly earnings)")
          return@runCatching null
        }

        response.toEarnings()
      }.onFailure { e ->
        logger.error("Failed to fetch earnings from Alpha Vantage for $symbol: ${e.message}", e)
      }.getOrNull()
    }
  }

  /**
   * Get sector symbol for a stock using Company Overview endpoint
   *
   * Fetches company information and extracts the sector, mapping it to our
   * internal SectorSymbol enum (based on S&P sector ETFs).
   *
   * Example sectors from Alpha Vantage:
   * - "TECHNOLOGY" -> XLK
   * - "FINANCIALS" -> XLF
   * - "HEALTH CARE" -> XLV
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @return SectorSymbol enum, or null if sector cannot be determined
   */
  override suspend fun getSectorSymbol(symbol: String): SectorSymbol? {
    return withContext(Dispatchers.IO) {
      runCatching {
        val url = "$baseUrl?function=$FUNCTION_OVERVIEW&symbol=$symbol&apikey=$apiKey"

        val response =
          restClient
            .get()
            .uri { uriBuilder ->
              uriBuilder
                .queryParam("function", FUNCTION_OVERVIEW)
                .queryParam("symbol", symbol)
                .queryParam("apikey", apiKey)
                .build()
            }.retrieve()
            .toEntity(AlphaVantageCompanyOverview::class.java)
            .body

        if (response == null) {
          logger.warn("No response received from Alpha Vantage company overview for $symbol")
          return@runCatching null
        }

        // Check if response contains an error
        if (response.hasError()) {
          logger.error("Alpha Vantage API error for company overview $symbol: ${response.getErrorDescription()}")
          return@runCatching null
        }

        // Check if response is valid (has required data)
        if (!response.isValid()) {
          logger.error("Alpha Vantage API returned invalid company overview for $symbol (missing symbol or sector)")
          return@runCatching null
        }

        val sectorSymbol = response.toSectorSymbol()
        if (sectorSymbol != null) {
        } else {
          logger.warn("Could not map sector '${response.sector}' to SectorSymbol for $symbol")
        }

        sectorSymbol
      }.onFailure { e ->
        logger.error("Failed to fetch company overview from Alpha Vantage for $symbol: ${e.message}", e)
      }.getOrNull()
    }
  }
}
