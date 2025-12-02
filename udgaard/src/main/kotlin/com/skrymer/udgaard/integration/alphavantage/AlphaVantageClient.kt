package com.skrymer.udgaard.integration.alphavantage

import com.skrymer.udgaard.integration.alphavantage.dto.AlphaVantageATR
import com.skrymer.udgaard.integration.alphavantage.dto.AlphaVantageEtfProfile
import com.skrymer.udgaard.integration.alphavantage.dto.AlphaVantageTimeSeriesDaily
import com.skrymer.udgaard.model.StockQuote
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate

/**
 * Client for Alpha Vantage API
 *
 * Documentation: https://www.alphavantage.co/documentation/
 *
 * Provides stock data including volume information that is not available from Ovtlyr.
 * Used to enrich stock quotes with volume data for order block analysis.
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
    @Value("\${alphavantage.api.baseUrl}") private val baseUrl: String
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AlphaVantageClient::class.java)
        private const val FUNCTION_DAILY = "TIME_SERIES_DAILY"
        private const val FUNCTION_ETF_PROFILE = "ETF_PROFILE"
        private const val FUNCTION_ATR = "ATR"
        private const val OUTPUT_SIZE_FULL = "full"
        private const val OUTPUT_SIZE_COMPACT = "compact" // Last 100 data points
    }

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    /**
     * Get daily time series data for a stock symbol
     *
     * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
     * @param outputSize "compact" for last 100 data points, "full" for 20+ years of historical data
     * @return List of stock quotes with volume data, or null if request fails
     */
    fun getDailyTimeSeries(symbol: String, outputSize: String = OUTPUT_SIZE_FULL): List<StockQuote>? {
        return runCatching {
            val url = "$baseUrl?function=$FUNCTION_DAILY&symbol=$symbol&outputsize=$outputSize&apikey=$apiKey"
            logger.info("Fetching daily time series for $symbol from Alpha Vantage (outputSize: $outputSize)")
            logger.debug("Alpha Vantage API URL: ${url.replace(apiKey, "***")}")

            val response = restClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .queryParam("function", FUNCTION_DAILY)
                        .queryParam("symbol", symbol)
                        .queryParam("outputsize", outputSize)
                        .queryParam("apikey", apiKey)
                        .build()
                }
                .retrieve()
                .toEntity(AlphaVantageTimeSeriesDaily::class.java)
                .body

            if (response == null) {
                logger.warn("No response received from Alpha Vantage for $symbol")
                return null
            }

            // Check if response contains an error
            if (response.hasError()) {
                logger.error("Alpha Vantage API error for $symbol: ${response.getErrorDescription()}")
                return null
            }

            // Check if response is valid (has required data)
            if (!response.isValid()) {
                logger.error("Alpha Vantage API returned invalid response for $symbol (missing Meta Data or Time Series)")
                return null
            }

            logger.debug("Response metadata: ${response.metaData}")
            val quotes = response.toStockQuotes()
            logger.info("Successfully fetched ${quotes.size} quotes for $symbol (${quotes.firstOrNull()?.date} to ${quotes.lastOrNull()?.date})")

            if (quotes.isNotEmpty()) {
                val sampleQuote = quotes.first()
                logger.debug("Sample quote: date=${sampleQuote.date}, volume=${sampleQuote.volume}")
            }

            quotes
        }.onFailure { e ->
            logger.error("Failed to fetch data from Alpha Vantage for $symbol: ${e.message}", e)
        }.getOrNull()
    }

    /**
     * Get ETF profile information including holdings, expense ratio, AUM, etc.
     *
     * @param symbol ETF symbol (e.g., "SPY", "QQQ", "TQQQ")
     * @return ETF profile data, or null if request fails
     */
    fun getEtfProfile(symbol: String): AlphaVantageEtfProfile? {
        return runCatching {
            val url = "$baseUrl?function=$FUNCTION_ETF_PROFILE&symbol=$symbol&apikey=$apiKey"
            logger.info("Fetching ETF profile for $symbol from Alpha Vantage")
            logger.debug("Alpha Vantage API URL: ${url.replace(apiKey, "***")}")

            val response = restClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .queryParam("function", FUNCTION_ETF_PROFILE)
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build()
                }
                .retrieve()
                .toEntity(AlphaVantageEtfProfile::class.java)
                .body

            if (response == null) {
                logger.warn("No response received from Alpha Vantage ETF profile for $symbol")
                return null
            }

            // Check if response contains an error
            if (response.hasError()) {
                logger.error("Alpha Vantage API error for ETF profile $symbol: ${response.getErrorDescription()}")
                return null
            }

            // Check if response is valid (has required data)
            if (!response.isValid()) {
                logger.error("Alpha Vantage API returned invalid ETF profile for $symbol (missing required fields)")
                return null
            }

            logger.info("Successfully fetched ETF profile for $symbol (AUM: ${response.netAssets}, Expense Ratio: ${response.netExpenseRatio}, Holdings: ${response.holdings?.size ?: 0})")

            response
        }.onFailure { e ->
            logger.error("Failed to fetch ETF profile from Alpha Vantage for $symbol: ${e.message}", e)
        }.getOrNull()
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
    fun getATR(
        symbol: String,
        interval: String = "daily",
        timePeriod: Int = 14
    ): Map<LocalDate, Double>? {
        return runCatching {
            val url = "$baseUrl?function=$FUNCTION_ATR&symbol=$symbol&interval=$interval&time_period=$timePeriod&apikey=$apiKey"
            logger.info("Fetching ATR for $symbol from Alpha Vantage (interval: $interval, period: $timePeriod)")
            logger.debug("Alpha Vantage API URL: ${url.replace(apiKey, "***")}")

            val response = restClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .queryParam("function", FUNCTION_ATR)
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("time_period", timePeriod)
                        .queryParam("apikey", apiKey)
                        .build()
                }
                .retrieve()
                .toEntity(AlphaVantageATR::class.java)
                .body

            if (response == null) {
                logger.warn("No response received from Alpha Vantage ATR for $symbol")
                return null
            }

            // Check if response contains an error
            if (response.hasError()) {
                logger.error("Alpha Vantage API error for ATR $symbol: ${response.getErrorDescription()}")
                return null
            }

            // Check if response is valid (has required data)
            if (!response.isValid()) {
                logger.error("Alpha Vantage API returned invalid ATR response for $symbol (missing Meta Data or Technical Analysis)")
                return null
            }

            val atrMap = response.toATRMap()
            logger.info("Successfully fetched ${atrMap.size} ATR values for $symbol (${atrMap.keys.minOrNull()} to ${atrMap.keys.maxOrNull()})")

            if (atrMap.isNotEmpty()) {
                val sampleEntry = atrMap.entries.first()
                logger.debug("Sample ATR: date=${sampleEntry.key}, atr=${sampleEntry.value}")
            }

            atrMap
        }.onFailure { e ->
            logger.error("Failed to fetch ATR from Alpha Vantage for $symbol: ${e.message}", e)
            logger.error("Stack trace:", e)
        }.getOrNull()
    }
}
