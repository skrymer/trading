package com.skrymer.udgaard.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.Earning
import java.time.LocalDate

/**
 * Alpha Vantage EARNINGS response
 *
 * This endpoint returns annual and quarterly earnings data for a given symbol.
 * Useful for strategies that need to exit positions before earnings announcements.
 *
 * Example response:
 * {
 *   "symbol": "AAPL",
 *   "annualEarnings": [
 *     {
 *       "fiscalDateEnding": "2024-09-30",
 *       "reportedEPS": "6.08"
 *     }
 *   ],
 *   "quarterlyEarnings": [
 *     {
 *       "fiscalDateEnding": "2024-09-30",
 *       "reportedDate": "2024-10-30",
 *       "reportedEPS": "1.64",
 *       "estimatedEPS": "1.60",
 *       "surprise": "0.04",
 *       "surprisePercentage": "2.5",
 *       "reportTime": "post-market"
 *     }
 *   ]
 * }
 *
 * API Documentation: https://www.alphavantage.co/documentation/#earnings
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageEarnings(
    @JsonProperty("symbol")
    val symbol: String? = null,

    @JsonProperty("annualEarnings")
    val annualEarnings: List<AnnualEarning>? = null,

    @JsonProperty("quarterlyEarnings")
    val quarterlyEarnings: List<QuarterlyEarning>? = null,

    @JsonProperty("Error Message")
    val errorMessage: String? = null,

    @JsonProperty("Note")
    val note: String? = null,

    @JsonProperty("Information")
    val information: String? = null
) {
    /**
     * Check if the response contains an error
     */
    fun hasError(): Boolean = errorMessage != null || note != null || information != null

    /**
     * Get human-readable error description
     */
    fun getErrorDescription(): String {
        return when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }
    }

    /**
     * Check if response is valid (has required data fields)
     */
    fun isValid(): Boolean = symbol != null && quarterlyEarnings != null

    /**
     * Convert to Earning domain objects
     * Focuses on quarterly earnings since that's what strategies need
     *
     * @return List of Earning sorted by fiscal date (oldest first)
     */
    fun toEarnings(): List<Earning> {
        val symbolValue = symbol ?: ""

        return quarterlyEarnings?.mapNotNull { quarterly ->
            try {
                Earning(
                    symbol = symbolValue,
                    fiscalDateEnding = LocalDate.parse(quarterly.fiscalDateEnding),
                    reportedDate = quarterly.reportedDate?.let { LocalDate.parse(it) },
                    reportedEPS = quarterly.reportedEPS?.toDoubleOrNull(),
                    estimatedEPS = quarterly.estimatedEPS?.toDoubleOrNull(),
                    surprise = quarterly.surprise?.toDoubleOrNull(),
                    surprisePercentage = quarterly.surprisePercentage?.toDoubleOrNull(),
                    reportTime = quarterly.reportTime
                )
            } catch (e: Exception) {
                // Skip invalid earnings entries
                null
            }
        }?.sortedBy { it.fiscalDateEnding } ?: emptyList()
    }
}

/**
 * Annual earnings data
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AnnualEarning(
    @JsonProperty("fiscalDateEnding")
    val fiscalDateEnding: String,

    @JsonProperty("reportedEPS")
    val reportedEPS: String
)

/**
 * Quarterly earnings data with estimates and surprises
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class QuarterlyEarning(
    @JsonProperty("fiscalDateEnding")
    val fiscalDateEnding: String,

    @JsonProperty("reportedDate")
    val reportedDate: String? = null,

    @JsonProperty("reportedEPS")
    val reportedEPS: String? = null,

    @JsonProperty("estimatedEPS")
    val estimatedEPS: String? = null,

    @JsonProperty("surprise")
    val surprise: String? = null,

    @JsonProperty("surprisePercentage")
    val surprisePercentage: String? = null,

    @JsonProperty("reportTime")
    val reportTime: String? = null
)
