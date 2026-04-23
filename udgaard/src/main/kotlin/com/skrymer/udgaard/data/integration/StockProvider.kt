package com.skrymer.udgaard.data.integration

import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate

data class LatestQuote(
  val symbol: String,
  val price: Double,
  val previousClose: Double = 0.0,
  val change: Double = 0.0,
  val changePercent: Double = 0.0,
  val volume: Long = 0,
  val high: Double = 0.0,
  val low: Double = 0.0,
  // Trading day the quote belongs to, in America/New_York (US market calendar).
  // Nullable because legacy call sites and providers that don't carry a timestamp
  // will omit it — consumers should fall back to stored data when null.
  val date: LocalDate? = null,
)

/**
 * Interface for stock price and volume data providers
 *
 * Implementations should provide OHLCV (Open, High, Low, Close, Volume) data
 * for stocks, preferably adjusted for corporate actions like splits and dividends.
 */
interface StockProvider {
  /**
   * Get daily time series data for a stock symbol
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @return List of stock quotes with price and volume data, or null if unavailable
   */
  fun getDailyAdjustedTimeSeries(symbol: String): List<StockQuote>?

  /**
   * Get the latest live quote for a symbol
   */
  fun getLatestQuote(symbol: String): LatestQuote?

  /**
   * Get latest live quotes for multiple symbols in parallel
   */
  fun getLatestQuotes(symbols: List<String>): Map<String, LatestQuote>
}
