package com.skrymer.udgaard.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.StockQuote
import java.time.LocalDate

/**
 * Alpha Vantage TIME_SERIES_DAILY response
 *
 * Example response:
 * {
 *   "Meta Data": {
 *     "1. Information": "Daily Prices (open, high, low, close) and Volumes",
 *     "2. Symbol": "AAPL",
 *     "3. Last Refreshed": "2024-11-15",
 *     "4. Output Size": "Full size",
 *     "5. Time Zone": "US/Eastern"
 *   },
 *   "Time Series (Daily)": {
 *     "2024-11-15": {
 *       "1. open": "228.5000",
 *       "2. high": "229.8900",
 *       "3. low": "227.5200",
 *       "4. close": "229.8700",
 *       "5. volume": "43696854"
 *     },
 *     ...
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageTimeSeriesDaily(
  @JsonProperty(value = "Meta Data")
  val metaData: MetaData? = null,
  @JsonProperty("Time Series (Daily)")
  val timeSeriesDaily: Map<String, DailyData>? = null,
  @JsonProperty("Error Message")
  val errorMessage: String? = null,
  @JsonProperty("Note")
  val note: String? = null,
) {
  fun hasError(): Boolean = errorMessage != null || note != null

  fun getErrorDescription(): String =
    when {
      errorMessage != null -> errorMessage
      note != null -> note
      else -> "Unknown error"
    }

  fun isValid(): Boolean = metaData != null && timeSeriesDaily != null

  fun toStockQuotes(): List<StockQuote> =
    timeSeriesDaily
      ?.map { (date, data) ->
        StockQuote(
          date = LocalDate.parse(date),
          openPrice = data.open.toDoubleOrNull() ?: 0.0,
          closePrice = data.close.toDoubleOrNull() ?: 0.0,
          high = data.high.toDoubleOrNull() ?: 0.0,
          low = data.low.toDoubleOrNull() ?: 0.0,
          volume = data.volume.toLongOrNull() ?: 0L,
          atr = 0.0,
          closePriceEMA5 = 0.0,
          closePriceEMA10 = 0.0,
          closePriceEMA20 = 0.0,
          closePriceEMA50 = 0.0,
          heatmap = 0.0,
          sectorHeatmap = 0.0,
          lastBuySignal = null,
          lastSellSignal = null,
        )
      }?.sortedBy { it.date } ?: emptyList()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaData(
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailyData(
  @JsonProperty("1. open")
  val open: String,
  @JsonProperty("2. high")
  val high: String,
  @JsonProperty("3. low")
  val low: String,
  @JsonProperty("4. close")
  val close: String,
  @JsonProperty("5. volume")
  val volume: String,
)
