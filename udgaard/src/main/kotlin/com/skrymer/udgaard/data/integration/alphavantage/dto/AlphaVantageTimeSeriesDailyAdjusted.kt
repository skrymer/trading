package com.skrymer.udgaard.data.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate

/**
 * Alpha Vantage TIME_SERIES_DAILY_ADJUSTED response
 *
 * This endpoint returns daily OHLCV data adjusted for stock splits and dividend events.
 * Critical for accurate backtesting as it accounts for corporate actions.
 *
 * Example response:
 * {
 *   "Meta Data": {
 *     "1. Information": "Daily Time Series with Splits and Dividend Events",
 *     "2. Symbol": "IBM",
 *     "3. Last Refreshed": "2024-11-29",
 *     "4. Output Size": "Compact",
 *     "5. Time Zone": "US/Eastern"
 *   },
 *   "Time Series (Daily)": {
 *     "2024-11-29": {
 *       "1. open": "213.8900",
 *       "2. high": "216.5000",
 *       "3. low": "213.3200",
 *       "4. close": "215.6100",
 *       "5. adjusted close": "215.6100",
 *       "6. volume": "3384176",
 *       "7. dividend amount": "0.0000",
 *       "8. split coefficient": "1.0000"
 *     },
 *     ...
 *   }
 * }
 *
 * API Documentation: https://www.alphavantage.co/documentation/#dailyadj
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageTimeSeriesDailyAdjusted(
  @JsonProperty("Meta Data")
  val metaData: AdjustedMetaData? = null,
  @JsonProperty("Time Series (Daily)")
  val timeSeriesDaily: Map<String, DailyAdjustedData>? = null,
  @JsonProperty("Error Message")
  val errorMessage: String? = null,
  @JsonProperty("Note")
  val note: String? = null,
  @JsonProperty("Information")
  val information: String? = null,
) {
  /**
   * Check if the response contains an error
   * Possible errors: rate limit exceeded, invalid API key, invalid symbol
   */
  fun hasError(): Boolean = errorMessage != null || note != null || information != null

  /**
   * Get human-readable error description
   */
  fun getErrorDescription(): String =
    when {
      errorMessage != null -> errorMessage
      note != null -> note
      information != null -> information
      else -> "Unknown error"
    }

  /**
   * Check if response is valid (has required data fields)
   */
  fun isValid(): Boolean = metaData != null && timeSeriesDaily != null

  /**
   * Convert to StockQuote domain objects with ALL prices adjusted for splits/dividends
   *
   * IMPORTANT: AlphaVantage only provides adjusted close directly. We must calculate
   * adjusted open/high/low ourselves using the adjustment factor.
   *
   * Adjustment factor = adjusted close / unadjusted close
   * Adjusted open = unadjusted open × adjustment factor
   * Adjusted high = unadjusted high × adjustment factor
   * Adjusted low = unadjusted low × adjustment factor
   *
   * This ensures:
   * - All prices account for stock splits and dividends
   * - Candle patterns (bullish/bearish) remain accurate
   * - Order block calculations work correctly
   * - Historical backtesting is accurate
   *
   * Only data from 2016-01-01 onwards is included to reduce memory usage
   * and focus on recent market conditions.
   *
   * @param minDate Only include data from this date onwards (default: 2016-01-01)
   * @return List of StockQuote with ALL ADJUSTED PRICES from minDate onwards, sorted by date (oldest first)
   */
  fun toStockQuotes(minDate: LocalDate = LocalDate.of(2016, 1, 1)): List<StockQuote> {
    val symbol = metaData?.symbol ?: ""

    return timeSeriesDaily
      ?.mapNotNull { (dateString, data) ->
        val date = LocalDate.parse(dateString)

        // Only include data from 2016-01-01 onwards
        if (date.isBefore(minDate)) {
          return@mapNotNull null
        }

        // AlphaVantage provides these values from the API
        val rawOpen = data.open.toDoubleOrNull() ?: 0.0
        val rawHigh = data.high.toDoubleOrNull() ?: 0.0
        val rawLow = data.low.toDoubleOrNull() ?: 0.0
        val rawClose = data.close.toDoubleOrNull() ?: 0.0
        val adjustedClose = data.adjustedClose.toDoubleOrNull() ?: 0.0

        // Calculate adjustment factor to convert raw prices to adjusted prices
        // This accounts for all splits and dividends
        val adjustmentFactor =
          if (rawClose > 0.0) {
            adjustedClose / rawClose
          } else {
            1.0
          }

        // Apply adjustment factor to ALL OHLC prices
        StockQuote(
          symbol = symbol,
          date = date,
          openPrice = rawOpen * adjustmentFactor, // ADJUSTED
          closePrice = adjustedClose, // ADJUSTED (from API)
          high = rawHigh * adjustmentFactor, // ADJUSTED
          low = rawLow * adjustmentFactor, // ADJUSTED
          volume = data.volume.toLongOrNull() ?: 0L,
          // Technical indicators will be calculated later by TechnicalIndicatorService
        )
      }?.sortedBy { it.date } ?: emptyList()
  }

  /**
   * Get symbol from metadata
   */
  fun getSymbol(): String? = metaData?.symbol
}

/**
 * Metadata for the adjusted time series response
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AdjustedMetaData(
  @JsonProperty("1. Information")
  val information: String,
  @JsonProperty("2. Symbol")
  val symbol: String,
  @JsonProperty("3. Last Refreshed")
  val lastRefreshed: String,
  @JsonProperty("4. Output Size")
  val outputSize: String,
  @JsonProperty("5. Time Zone")
  val timeZone: String,
)

/**
 * Daily adjusted OHLCV data with dividend and split information
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DailyAdjustedData(
  @JsonProperty("1. open")
  val open: String,
  @JsonProperty("2. high")
  val high: String,
  @JsonProperty("3. low")
  val low: String,
  @JsonProperty("4. close")
  val close: String,
  @JsonProperty("5. adjusted close")
  val adjustedClose: String,
  @JsonProperty("6. volume")
  val volume: String,
  @JsonProperty("7. dividend amount")
  val dividendAmount: String,
  @JsonProperty("8. split coefficient")
  val splitCoefficient: String,
)
