package com.skrymer.udgaard.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Alpha Vantage error response
 *
 * Returned when API call fails due to:
 * - Invalid API key
 * - Rate limit exceeded
 * - Invalid symbol
 * - Invalid function
 *
 * Example error response:
 * {
 *   "Error Message": "Invalid API call. Please retry or visit the documentation...",
 *   "Note": "Please consider optimizing your API call frequency."
 * }
 *
 * Example rate limit response:
 * {
 *   "Note": "Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute..."
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageErrorResponse(
    @JsonProperty("Error Message")
    val errorMessage: String? = null,

    @JsonProperty("Note")
    val note: String? = null,

    @JsonProperty("Information")
    val information: String? = null
) {
    fun hasError(): Boolean = errorMessage != null || note != null || information != null

    fun getErrorDescription(): String {
        return when {
            errorMessage != null -> errorMessage
            note != null -> note
            information != null -> information
            else -> "Unknown error"
        }
    }
}
