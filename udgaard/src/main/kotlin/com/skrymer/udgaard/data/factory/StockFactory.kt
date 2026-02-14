package com.skrymer.udgaard.data.factory

import com.skrymer.udgaard.data.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.data.model.Breadth
import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate

/**
 * Factory for creating Stock domain objects from AlphaVantage data enriched with Ovtlyr indicators.
 *
 * NEW ARCHITECTURE (AlphaVantage-primary):
 * 1. AlphaVantage provides: OHLCV data (adjusted prices + volume)
 * 2. We calculate: EMAs, Donchian channels, trend
 * 3. AlphaVantage provides: ATR values
 * 4. Ovtlyr enriches: Buy/sell signals, fear/greed heatmaps, sector sentiment
 * 5. Order blocks: Calculated from volume data
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
   * 6. Enrich with Ovtlyr signals and heatmaps using OvtlyrEnrichmentService (if not skipped)
   * 7. Add market/sector context from breadth data
   *
   * @param symbol - Stock symbol
   * @param stockQuotes - OHLCV quotes from AlphaVantage (PRIMARY data source)
   * @param atrMap - ATR values from AlphaVantage keyed by date
   * @param adxMap - ADX values from AlphaVantage keyed by date
   * @param marketBreadth - Market breadth data for context
   * @param sectorBreadth - Sector breadth data for context
   * @param spy - SPY reference data (null when skipOvtlyrEnrichment is true)
   * @param skipOvtlyrEnrichment - Whether to skip Ovtlyr enrichment (default: false)
   * @return Fully enriched StockQuote list, or null if enrichment fails
   */
  fun enrichQuotes(
    symbol: String,
    stockQuotes: List<StockQuote>,
    atrMap: Map<LocalDate, Double>?,
    adxMap: Map<LocalDate, Double>?,
    marketBreadth: Breadth?,
    sectorBreadth: Breadth?,
    spy: OvtlyrStockInformation?,
    skipOvtlyrEnrichment: Boolean = false,
  ): List<StockQuote>?

  /**
   * Create a Stock entity from enriched quotes, order blocks, and earnings.
   *
   * @param symbol - Stock symbol
   * @param sectorSymbol - Sector symbol (from Ovtlyr)
   * @param enrichedQuotes - Fully enriched quotes (from createQuotes)
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
