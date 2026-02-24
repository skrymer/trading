package com.skrymer.udgaard.data.integration.massive.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.data.model.StockQuote
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Massive API (Polygon) Aggregates (Bars) response
 *
 * Endpoint: GET /v2/aggs/ticker/{ticker}/range/1/day/{from}/{to}?adjusted=true&limit=50000&apiKey={key}
 *
 * Returns split-adjusted OHLCV data when adjusted=true (default).
 * Unlike AlphaVantage, all prices are already fully adjusted — no manual adjustment math needed.
 *
 * Example response:
 * {
 *   "ticker": "AAPL",
 *   "adjusted": true,
 *   "status": "OK",
 *   "resultsCount": 2500,
 *   "results": [
 *     { "o": 150.0, "h": 155.0, "l": 149.0, "c": 154.0, "v": 80000000, "vw": 152.5, "n": 500000, "t": 1609459200000 }
 *   ],
 *   "next_url": "https://api.polygon.io/v2/aggs/..."
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MassiveAggregatesResponse(
  @JsonProperty("ticker")
  val ticker: String? = null,
  @JsonProperty("adjusted")
  val adjusted: Boolean? = null,
  @JsonProperty("status")
  val status: String? = null,
  @JsonProperty("resultsCount")
  val resultsCount: Int? = null,
  @JsonProperty("results")
  val results: List<AggregateBar>? = null,
  @JsonProperty("next_url")
  val nextUrl: String? = null,
  @JsonProperty("error")
  val error: String? = null,
  @JsonProperty("message")
  val message: String? = null,
) {
  fun hasError(): Boolean = status == "ERROR" || error != null || message != null

  fun getErrorDescription(): String =
    when {
      error != null -> error
      message != null -> message
      else -> "Unknown error"
    }

  fun isValid(): Boolean = results != null && ticker != null

  /**
   * Convert aggregate bars to StockQuote domain objects.
   *
   * All prices are already split-adjusted by the Massive API (adjusted=true),
   * so we map them directly without any adjustment math.
   *
   * @param symbol Stock symbol to set on each quote
   * @param minDate Only include data from this date onwards
   * @return List of StockQuote sorted by date ascending
   */
  fun toStockQuotes(
    symbol: String,
    minDate: LocalDate = LocalDate.of(2016, 1, 1),
  ): List<StockQuote> {
    return results
      ?.mapNotNull { bar ->
        val date = Instant.ofEpochMilli(bar.timestamp).atZone(EASTERN_TIME).toLocalDate()

        if (date.isBefore(minDate)) {
          return@mapNotNull null
        }

        StockQuote(
          symbol = symbol,
          date = date,
          openPrice = bar.open,
          high = bar.high,
          low = bar.low,
          closePrice = bar.close,
          volume = bar.volume,
        )
      }?.sortedBy { it.date } ?: emptyList()
  }

  companion object {
    private val EASTERN_TIME: ZoneId = ZoneId.of("America/New_York")
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AggregateBar(
  @JsonProperty("o")
  val open: Double = 0.0,
  @JsonProperty("h")
  val high: Double = 0.0,
  @JsonProperty("l")
  val low: Double = 0.0,
  @JsonProperty("c")
  val close: Double = 0.0,
  @JsonProperty("v")
  val volume: Long = 0L,
  @JsonProperty("vw")
  val volumeWeighted: Double = 0.0,
  @JsonProperty("n")
  val numberOfTransactions: Int = 0,
  @JsonProperty("t")
  val timestamp: Long = 0L,
)
