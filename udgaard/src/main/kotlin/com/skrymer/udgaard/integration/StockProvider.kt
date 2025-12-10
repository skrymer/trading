package com.skrymer.udgaard.integration

import com.skrymer.udgaard.model.StockQuote

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
   * @param outputSize Size of the dataset ("compact" for recent data, "full" for historical)
   * @return List of stock quotes with price and volume data, or null if unavailable
   */
  fun getDailyAdjustedTimeSeries(
    symbol: String,
    outputSize: String = "full",
  ): List<StockQuote>?
}
