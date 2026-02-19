package com.skrymer.udgaard.data.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

/**
 * Alpha Vantage ATR (Average True Range) response
 *
 * Documentation: https://www.alphavantage.co/documentation/#atr
 *
 * Example response:
 * {
 *   "Meta Data": {
 *     "1: Symbol": "AAPL",
 *     "2: Indicator": "Average True Range (ATR)",
 *     "3: Last Refreshed": "2024-11-15",
 *     "4: Interval": "daily",
 *     "5: Time Period": 14,
 *     "6: Time Zone": "US/Eastern"
 *   },
 *   "Technical Analysis: ATR": {
 *     "2024-11-15": {
 *       "ATR": "3.6580"
 *     },
 *     "2024-11-14": {
 *       "ATR": "3.7120"
 *     },
 *     ...
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageATR(
  @JsonProperty("Meta Data")
  val metaData: ATRMetaData? = null,
  @JsonProperty("Technical Analysis: ATR")
  val technicalAnalysis: Map<String, ATRData>? = null,
  @JsonProperty("Error Message")
  val errorMessage: String? = null,
  @JsonProperty("Note")
  val note: String? = null,
  @JsonProperty("Information")
  val information: String? = null,
) {
  fun hasError(): Boolean = errorMessage != null || note != null || information != null

  fun getErrorDescription(): String =
    when {
      errorMessage != null -> errorMessage
      note != null -> note
      information != null -> information
      else -> "Unknown error"
    }

  fun isValid(): Boolean = metaData != null && technicalAnalysis != null

  /**
   * Get ATR value for a specific date
   * @param date The date to get ATR for
   * @return ATR value or null if not found
   */
  fun getATRForDate(date: LocalDate): Double? {
    val dateString = date.toString()
    return technicalAnalysis?.get(dateString)?.atr?.toDoubleOrNull()
  }

  /**
   * Convert to a map of LocalDate to ATR values
   * Only data from minDate onwards is included to match stock quote filtering
   * @param minDate Only include data from this date onwards (default: 2016-01-01)
   * @return Map of date to ATR value from minDate onwards
   */
  fun toATRMap(minDate: LocalDate = LocalDate.of(2016, 1, 1)): Map<LocalDate, Double> {
    return technicalAnalysis
      ?.mapNotNull { (dateString, data) ->
        runCatching {
          val date = LocalDate.parse(dateString)
          // Only include data from 2016-01-01 onwards
          if (date.isBefore(minDate)) {
            return@runCatching null
          }
          val atr = data.atr.toDoubleOrNull()
          if (atr != null) date to atr else null
        }.getOrNull()
      }?.toMap() ?: emptyMap()
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ATRMetaData(
  @JsonProperty("1: Symbol")
  val symbol: String,
  @JsonProperty("2: Indicator")
  val indicator: String,
  @JsonProperty("3: Last Refreshed")
  val lastRefreshed: String,
  @JsonProperty("4: Interval")
  val interval: String,
  @JsonProperty("5: Time Period")
  val timePeriod: Int,
  @JsonProperty("6: Time Zone")
  val timeZone: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ATRData(
  @JsonProperty("ATR")
  val atr: String,
)
