package com.skrymer.udgaard.data.factory

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Factory for creating Stock domain objects from AlphaVantage data.
 *
 * Processing pipeline:
 * 1. AlphaVantage provides: OHLCV data (adjusted prices + volume)
 * 2. We calculate: EMAs, ATR, ADX, Donchian channels, trend
 * 3. Order blocks: Calculated from volume data
 */
interface StockFactory {
  /**
   * Create fully enriched quotes from OHLCV data.
   *
   * Processing pipeline:
   * 1. Stock quotes (OHLCV + volume) - input
   * 2. Calculate EMAs, ATR, ADX, Donchian channels, and trend using TechnicalIndicatorService
   *
   * @param symbol - Stock symbol
   * @param stockQuotes - OHLCV quotes from AlphaVantage (PRIMARY data source)
   * @return Fully enriched StockQuote list, or null if enrichment fails
   */
  fun enrichQuotes(
    symbol: String,
    stockQuotes: List<StockQuote>,
  ): List<StockQuote>?

  /**
   * Create a Stock entity from enriched quotes, order blocks, and earnings.
   *
   * @param symbol - Stock symbol
   * @param enrichedQuotes - Fully enriched quotes (from enrichQuotes)
   * @param orderBlocks - Order blocks calculated from volume data
   * @param earnings - Quarterly earnings history (from AlphaVantage)
   * @return Fully constructed Stock entity
   */
  fun createStock(
    symbol: String,
    enrichedQuotes: List<StockQuote>,
    orderBlocks: List<OrderBlock>,
    earnings: List<Earning>,
  ): Stock
}
