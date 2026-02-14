package com.skrymer.udgaard.data.integration

import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate

/**
 * Interface for stock price and volume data providers
 *
 * Implementations should provide OHLCV (Open, High, Low, Close, Volume) data
 * for stocks, preferably adjusted for corporate actions like splits and dividends.
 *
 * All methods are suspend functions to support rate limiting with true backpressure.
 */
interface StockProvider {
  /**
   * Get daily time series data for a stock symbol
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @param outputSize Size of the dataset ("compact" for recent data, "full" for historical)
   * @param minDate Only include data from this date onwards (default: 2020-01-01)
   * @return List of stock quotes with price and volume data, or null if unavailable
   */
  suspend fun getDailyAdjustedTimeSeries(
    symbol: String,
    outputSize: String = "full",
    minDate: LocalDate = LocalDate.of(2020, 1, 1),
  ): List<StockQuote>?
}
