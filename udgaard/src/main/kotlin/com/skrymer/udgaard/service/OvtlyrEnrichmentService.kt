package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.BreadthDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockQuote
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for enriching AlphaVantage stock data with Ovtlyr-specific indicators.
 *
 * Ovtlyr provides proprietary indicators that are REQUIRED for trading strategies:
 * - Buy/Sell signals based on Ovtlyr's algorithm
 * - Fear & Greed heatmaps (0-100 scale)
 * - Sector sentiment and breadth
 *
 * This service matches AlphaVantage quotes with Ovtlyr data by date and copies over
 * the Ovtlyr-specific fields, while keeping the AlphaVantage OHLCV data intact.
 *
 * IMPORTANT: If Ovtlyr data is unavailable, this service will return null,
 * indicating that the stock cannot be properly enriched and should not be used.
 */
@Service
class OvtlyrEnrichmentService(
  private val ovtlyrClient: OvtlyrClient,
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(OvtlyrEnrichmentService::class.java)
  }

  /**
   * Enrich AlphaVantage quotes with Ovtlyr signals, heatmaps, and sector data.
   *
   * Matches quotes by date and copies Ovtlyr-specific fields:
   * - Buy/sell signals (signal, lastBuySignal, lastSellSignal)
   * - Stock heatmaps (heatmap, previousHeatmap)
   * - Sector heatmaps (sectorHeatmap, previousSectorHeatmap)
   * - Sector statistics (sectorStocksInUptrend, sectorStocksInDowntrend, sectorBullPercentage)
   * - Sector uptrend status
   *
   * @param quotes - Stock quotes from AlphaVantage (OHLCV + EMAs already calculated)
   * @param symbol - Stock symbol
   * @param marketBreadth - Market breadth data
   * @param sectorBreadth - Sector breadth data
   * @param spy - SPY reference data
   * @return Enriched quotes with Ovtlyr data, or null if Ovtlyr data unavailable
   */
  fun enrichWithOvtlyr(
    quotes: List<StockQuoteDomain>,
    symbol: String,
    marketBreadth: BreadthDomain?,
    sectorBreadth: BreadthDomain?,
    spy: OvtlyrStockInformation,
  ): List<StockQuoteDomain>? {
    if (quotes.isEmpty()) {
      logger.warn("No AlphaVantage quotes provided for $symbol")
      return null
    }

    logger.info("Enriching $symbol with Ovtlyr data")

    // Fetch Ovtlyr data - REQUIRED, not optional
    val ovtlyrStock = ovtlyrClient.getStockInformation(symbol)

    if (ovtlyrStock == null) {
      logger.error("FAILED: No Ovtlyr data available for $symbol - cannot enrich stock")
      return null
    }

    logger.info("Found Ovtlyr data for $symbol with ${ovtlyrStock.getQuotes().size} quotes")

    // Create a map of Ovtlyr quotes by date for fast lookup
    val ovtlyrQuotesByDate =
      ovtlyrStock
        .getQuotes()
        .filterNotNull()
        .associateBy { it.getDate() }

    var enrichedCount = 0
    var skippedCount = 0

    // Enrich each AlphaVantage quote with Ovtlyr data if available for that date
    val enrichedQuotes =
      quotes.mapNotNull { quote ->
        val ovtlyrQuote = ovtlyrQuotesByDate[quote.date]

        if (ovtlyrQuote != null) {
          enrichQuoteWithOvtlyr(quote, ovtlyrQuote, ovtlyrStock, marketBreadth, sectorBreadth, spy)
          enrichedCount++
          quote
        } else {
          // No Ovtlyr data for this date - skip this quote
          skippedCount++
          null
        }
      }

    logger.info("Enriched $enrichedCount quotes for $symbol, skipped $skippedCount dates without Ovtlyr data")

    return enrichedQuotes
  }

  /**
   * Enrich a single quote with Ovtlyr data.
   */
  private fun enrichQuoteWithOvtlyr(
    stockQuoteDomain: StockQuoteDomain,
    ovtlyrQuote: OvtlyrStockQuote,
    ovtlyrStock: OvtlyrStockInformation,
    marketBreadth: BreadthDomain?,
    sectorBreadth: BreadthDomain?,
    spy: OvtlyrStockInformation,
  ) {
    val previousQuote = ovtlyrStock.getPreviousQuote(ovtlyrQuote)
    val previousPreviousQuote = if (previousQuote != null) ovtlyrStock.getPreviousQuote(previousQuote) else null

    // Market breadth context (previous day's data)
    val marketBreadthQuote = marketBreadth?.getPreviousQuote(marketBreadth.getQuoteForDate(ovtlyrQuote.getDate()))
    val marketInUptrend = marketBreadthQuote?.isInUptrend() ?: false
    val marketDonkeyScore = marketBreadthQuote?.donkeyChannelScore ?: 0

    // Sector breadth context (previous day's data)
    val sectorBreadthQuote = sectorBreadth?.getPreviousQuote(sectorBreadth.getQuoteForDate(ovtlyrQuote.getDate()))
    val sectorInUptrend = sectorBreadthQuote?.isInUptrend() ?: false
    val sectorDonkeyScore = sectorBreadthQuote?.donkeyChannelScore ?: 0

    // SPY context
    val currentSpySignal = spy.getCurrentSignalFrom(ovtlyrQuote.getDate())
    val spyInUptrendValue = spy.getQuoteForDate(ovtlyrQuote.getDate())?.isInUptrend ?: false
    val spyQuote = spy.getPreviousQuote(spy.getQuoteForDate(ovtlyrQuote.getDate()))
    val spyHeatmapValue = spyQuote?.heatmap ?: 0.0
    val spyPreviousHeatmapValue = spy.getPreviousQuote(spyQuote)?.heatmap ?: 0.0

    // Calculate market advancing percent from breadth data
    val marketAdvancing = calculateMarketAdvancingPercent(marketBreadth, ovtlyrQuote.getDate())

    // Enrich stock quote domain with Ovtlyr-specific data
    stockQuoteDomain.apply {
      // Buy/sell signals
      signal = ovtlyrQuote.signal
      lastBuySignal = ovtlyrStock.getLastBuySignal(ovtlyrQuote.getDate())
      lastSellSignal = ovtlyrStock.getLastSellSignal(ovtlyrQuote.getDate())

      // Stock heatmaps (fear/greed)
      heatmap = previousQuote?.heatmap ?: 0.0
      previousHeatmap = previousPreviousQuote?.heatmap ?: 0.0

      // Sector heatmaps
      sectorHeatmap = previousQuote?.sectorHeatmap ?: 0.0
      previousSectorHeatmap = previousPreviousQuote?.sectorHeatmap ?: 0.0

      // Sector statistics
      sectorStocksInUptrend = ovtlyrQuote.sectorUptrend
      sectorStocksInDowntrend = ovtlyrQuote.sectorDowntrend
      sectorBullPercentage = previousQuote?.sectorBullPercentage ?: 0.0
      sectorIsInUptrend = sectorInUptrend
      sectorDonkeyChannelScore = sectorDonkeyScore

      // Market context
      marketIsInUptrend = marketInUptrend
      marketDonkeyChannelScore = marketDonkeyScore
      marketAdvancingPercent = marketAdvancing
      marketBullPercentage = marketBreadthQuote?.bullStocksPercentage ?: 0.0
      marketBullPercentage_10ema = marketBreadthQuote?.ema_10 ?: 0.0

      // SPY context
      spySignal = currentSpySignal
      spyInUptrend = spyInUptrendValue
      spyHeatmap = spyHeatmapValue
      spyPreviousHeatmap = spyPreviousHeatmapValue

      // Previous quote date
      previousQuoteDate = previousQuote?.getDate()
    }
  }

  /**
   * Calculate market breadth - percentage of stocks advancing (above their uptrend status)
   */
  private fun calculateMarketAdvancingPercent(
    marketBreadth: BreadthDomain?,
    date: LocalDate,
  ): Double {
    if (marketBreadth == null) return 0.0

    val breadthQuote = marketBreadth.getQuoteForDate(date) ?: return 0.0

    val total = breadthQuote.numberOfStocksInUptrend + breadthQuote.numberOfStocksInDowntrend

    if (total == 0) return 0.0

    return (breadthQuote.numberOfStocksInUptrend.toDouble() / total) * 100.0
  }
}
