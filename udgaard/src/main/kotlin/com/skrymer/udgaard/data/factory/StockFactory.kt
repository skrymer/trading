package com.skrymer.udgaard.data.factory

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate

/**
 * Factory for creating Stock domain objects from AlphaVantage data.
 *
 * Processing pipeline:
 * 1. AlphaVantage provides: OHLCV data (adjusted prices + volume)
 * 2. We calculate: EMAs, Donchian channels, trend
 * 3. AlphaVantage provides: ATR and ADX values
 * 4. Order blocks: Calculated from volume data
 */
interface StockFactory {
  /**
   * Create fully enriched quotes from AlphaVantage data.
   *
   * Processing pipeline:
   * 1. Stock quotes (OHLCV + volume) - input
   * 2. Calculate EMAs (5, 10, 20, 50) using TechnicalIndicatorService
   * 3. Match ATR data from AlphaVantage by date
   * 4. Match ADX data from AlphaVantage by date
   * 5. Calculate Donchian channels and trend
   *
   * @param symbol - Stock symbol
   * @param stockQuotes - OHLCV quotes from AlphaVantage (PRIMARY data source)
   * @param atrMap - ATR values from AlphaVantage keyed by date
   * @param adxMap - ADX values from AlphaVantage keyed by date
   * @return Fully enriched StockQuote list, or null if enrichment fails
   */
  fun enrichQuotes(
    symbol: String,
    stockQuotes: List<StockQuote>,
    atrMap: Map<LocalDate, Double>?,
    adxMap: Map<LocalDate, Double>?,
  ): List<StockQuote>?

  /**
   * Create a Stock entity from enriched quotes, order blocks, and earnings.
   *
   * @param symbol - Stock symbol
   * @param sectorSymbol - Sector symbol
   * @param enrichedQuotes - Fully enriched quotes (from enrichQuotes)
   * @param orderBlocks - Order blocks calculated from volume data
   * @param earnings - Quarterly earnings history (from AlphaVantage)
   * @return Fully constructed Stock entity
   */
  fun createStock(
    symbol: String,
    sectorSymbol: String?,
    enrichedQuotes: List<StockQuote>,
    orderBlocks: List<OrderBlock>,
    earnings: List<Earning>,
  ): Stock
}
