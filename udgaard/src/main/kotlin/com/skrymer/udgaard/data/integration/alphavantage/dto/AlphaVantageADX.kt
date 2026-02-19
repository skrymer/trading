package com.skrymer.udgaard.data.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

/**
 * Alpha Vantage ADX (Average Directional Index) response
 *
 * Documentation: https://www.alphavantage.co/documentation/#adx
 *
 * ADX measures the strength of a trend (not direction).
 * Values range from 0 to 100:
 * - 0-25: Absent or weak trend
 * - 25-50: Strong trend
 * - 50-75: Very strong trend
 * - 75-100: Extremely strong trend
 *
 * Example response:
 * {
 *   "Meta Data": {
 *     "1: Symbol": "AAPL",
 *     "2: Indicator": "Average Directional Movement Index (ADX)",
 *     "3: Last Refreshed": "2024-11-15",
 *     "4: Interval": "daily",
 *     "5: Time Period": 14,
 *     "6: Time Zone": "US/Eastern"
 *   },
 *   "Technical Analysis: ADX": {
 *     "2024-11-15": {
 *       "ADX": "28.5432"
 *     },
 *     "2024-11-14": {
 *       "ADX": "27.8910"
 *     },
 *     ...
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageADX(
  @JsonProperty("Meta Data")
  val metaData: ADXMetaData? = null,
  @JsonProperty("Technical Analysis: ADX")
  val technicalAnalysis: Map<String, ADXData>? = null,
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
   * Get ADX value for a specific date
   * @param date The date to get ADX for
   * @return ADX value or null if not found
   */
  fun getADXForDate(date: LocalDate): Double? {
    val dateString = date.toString()
    return technicalAnalysis?.get(dateString)?.adx?.toDoubleOrNull()
  }

  /**
   * Convert to a map of LocalDate to ADX values
   * Only data from minDate onwards is included to match stock quote filtering
   * @param minDate Only include data from this date onwards (default: 2016-01-01)
   * @return Map of date to ADX value from minDate onwards
   */
  fun toADXMap(minDate: LocalDate = LocalDate.of(2016, 1, 1)): Map<LocalDate, Double> {
    return technicalAnalysis
      ?.mapNotNull { (dateString, data) ->
        runCatching {
          val date = LocalDate.parse(dateString)
          // Only include data from 2016-01-01 onwards
          if (date.isBefore(minDate)) {
            return@runCatching null
          }
          val adx = data.adx.toDoubleOrNull()
          if (adx != null) date to adx else null
        }.getOrNull()
      }?.toMap() ?: emptyMap()
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ADXMetaData(
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
data class ADXData(
  @JsonProperty("ADX")
  val adx: String,
)
