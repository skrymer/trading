package com.skrymer.udgaard.integration.alphavantage

import com.skrymer.udgaard.integration.alphavantage.dto.AlphaVantageTimeSeriesDaily
import com.skrymer.udgaard.model.StockQuote
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

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
    @Value("\${alphavantage.api.key}") private val apiKey: String,
    @Value("\${alphavantage.api.baseUrl}") private val baseUrl: String
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AlphaVantageClient::class.java)
        private const val FUNCTION_DAILY = "TIME_SERIES_DAILY"
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
     * Get full daily time series (20+ years of historical data)
     * Changed from compact to ensure all historical quotes have volume data
     */
    fun getDailyTimeSeriesCompact(symbol: String): List<StockQuote>? {
        logger.info("Getting full time series for $symbol (using FULL output size for complete historical data)")
        return getDailyTimeSeries(symbol, OUTPUT_SIZE_FULL)
    }

    /**
     * Get full daily time series (20+ years of historical data)
     * Use with caution due to API rate limits
     */
    fun getDailyTimeSeriesFull(symbol: String): List<StockQuote>? {
        return getDailyTimeSeries(symbol, OUTPUT_SIZE_FULL)
    }
}
