package com.skrymer.udgaard.integration.alphavantage

import com.skrymer.udgaard.integration.alphavantage.dto.AlphaVantageHistoricalOptions
import com.skrymer.udgaard.integration.options.OptionContract
import com.skrymer.udgaard.integration.options.OptionsDataProvider
import com.skrymer.udgaard.model.OptionType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * AlphaVantage implementation of OptionsDataClient.
 * Fetches historical option prices and Greeks using the HISTORICAL_OPTIONS API.
 *
 * Documentation: https://www.alphavantage.co/documentation/#historical-options
 *
 * API Rate Limits (75 requests/minute plan):
 * - Premium: 75 requests per minute
 * - Sufficient for user-initiated chart viewing
 */
@Service
@Primary
class AlphaVantageOptionsProvider(
    @Value("\${alphavantage.api.key:}") private val apiKey: String,
    @Value("\${alphavantage.api.baseUrl}") private val baseUrl: String
) : OptionsDataProvider {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AlphaVantageOptionsProvider::class.java)
        private const val FUNCTION_HISTORICAL_OPTIONS = "HISTORICAL_OPTIONS"
    }

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    /**
     * Get all option contracts for a symbol on a specific date.
     * If date is null, fetches all available historical options data.
     * Returns null if no data available (weekends, holidays, or API error).
     */
    override fun getHistoricalOptions(symbol: String, date: String?): List<OptionContract>? {
        return runCatching {
            val dateInfo = if (date != null) " on $date" else " (all data)"

            // Build the URL for logging
            val urlBuilder = StringBuilder("$baseUrl?function=$FUNCTION_HISTORICAL_OPTIONS&symbol=$symbol")
            if (date != null) {
                urlBuilder.append("&date=$date")
            }
            urlBuilder.append("&apikey=$apiKey")
            val fullUrl = urlBuilder.toString()

            logger.info("Fetching historical options for $symbol$dateInfo from AlphaVantage")
            logger.info("AlphaVantage URL: ${fullUrl.replace(apiKey, "***KEY***")}")

            val response = restClient.get()
                .uri { uriBuilder ->
                    val builder = uriBuilder
                        .queryParam("function", FUNCTION_HISTORICAL_OPTIONS)
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)

                    // Only add date parameter if provided
                    if (date != null) {
                        builder.queryParam("date", date)
                    }

                    builder.build()
                }
                .retrieve()
                .toEntity(AlphaVantageHistoricalOptions::class.java)
                .body

            if (response == null) {
                logger.warn("No response received from AlphaVantage historical options for $symbol$dateInfo")
                return null
            }

            logger.debug("AlphaVantage response - message: ${response.message}, data count: ${response.data?.size ?: 0}")

            // Check if response contains an error
            if (response.hasError()) {
                logger.error("AlphaVantage API error for historical options $symbol$dateInfo: ${response.getErrorDescription()}")
                logger.error("Response details - message: ${response.message}, data count: ${response.data?.size ?: 0}")
                return null
            }

            // Check if response is valid (has required data)
            if (!response.isValid()) {
                logger.error("AlphaVantage API returned invalid or empty historical options response for $symbol$dateInfo")
                logger.error("Response details - message: ${response.message}, data count: ${response.data?.size ?: 0}")
                return null
            }

            val contracts = response.toOptionContracts()
            logger.info("Successfully fetched ${contracts.size} option contracts for $symbol$dateInfo")

            if (contracts.isNotEmpty()) {
                val sampleContract = contracts.first()
                logger.debug("Sample contract: ${sampleContract.contractId}, strike=${sampleContract.strike}, type=${sampleContract.optionType}, price=${sampleContract.price}")
            }

            contracts
        }.onFailure { e ->
            logger.error("Failed to fetch historical options from AlphaVantage for $symbol${if (date != null) " on $date" else ""}: ${e.message}", e)
        }.getOrNull()
    }

    /**
     * Find a specific option contract matching the given parameters.
     * Returns null if no matching contract found.
     */
    override fun findOptionContract(
        symbol: String,
        strike: Double,
        expiration: String,
        optionType: OptionType,
        date: String
    ): OptionContract? {
        // Fetch historical options data for the specific date
        // Date format: YYYY-MM-DD (ISO 8601 format)
        val contracts = getHistoricalOptions(symbol, date) ?: return null

        // Filter for the specific contract parameters
        return contracts.firstOrNull { contract ->
            contract.strike == strike &&
                contract.expiration.toString() == expiration &&
                contract.optionType == optionType
        }.also { contract ->
            if (contract != null) {
                logger.debug("Found matching contract: ${contract.contractId} for $symbol $strike $optionType exp=$expiration on $date")
            } else {
                logger.debug("No matching contract found for $symbol $strike $optionType exp=$expiration on $date")
            }
        }
    }
}
