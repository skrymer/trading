package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.data.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.data.integration.ovtlyr.dto.OvtlyrStockQuote
import com.skrymer.udgaard.data.model.Breadth
import com.skrymer.udgaard.data.model.StockQuote
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
   * @param spy - SPY reference data (null when skipOvtlyrEnrichment is true)
   * @param skipOvtlyrEnrichment - Whether to skip Ovtlyr API call and use default values (default: false)
   * @return Enriched quotes with Ovtlyr data, or null if enrichment fails
   */
  fun enrichWithOvtlyr(
    quotes: List<StockQuote>,
    symbol: String,
    marketBreadth: Breadth?,
    sectorBreadth: Breadth?,
    spy: OvtlyrStockInformation?,
    skipOvtlyrEnrichment: Boolean = false,
  ): List<StockQuote>? {
    if (quotes.isEmpty()) {
      logger.warn("No AlphaVantage quotes provided for $symbol")
      return null
    }

    // Skip Ovtlyr API call if flag is set
    if (skipOvtlyrEnrichment) {
      logger.info("$symbol - Skipping Ovtlyr enrichment, using default values")
      quotes.forEach { setDefaultOvtlyrValues(it, marketBreadth, sectorBreadth) }
      return quotes
    }

    // Fetch Ovtlyr data - OPTIONAL, not required
    val ovtlyrStock = ovtlyrClient.getStockInformation(symbol)

    if (ovtlyrStock == null) {
      logger.warn("No Ovtlyr data available for $symbol - using AlphaVantage data with default Ovtlyr values")
      // Set default values for all quotes and return them
      quotes.forEach { setDefaultOvtlyrValues(it, marketBreadth, sectorBreadth) }
      return quotes
    }

    // Debug: Check raw quotes
    val rawQuotes = ovtlyrStock.getQuotes()
    logger.debug("$symbol - Ovtlyr raw quotes count: ${rawQuotes.size}")

    // Create a map of Ovtlyr quotes by date for fast lookup
    val ovtlyrQuotesByDate =
      ovtlyrStock
        .getQuotes()
        .filterNotNull()
        .associateBy { it.getDate() }

    // Log date ranges for debugging
    if (ovtlyrQuotesByDate.isEmpty()) {
      logger.warn("Ovtlyr returned stock data for $symbol but has NO quotes - using AlphaVantage data with default Ovtlyr values")
      // Set default values for all quotes and return them
      quotes.forEach { setDefaultOvtlyrValues(it, marketBreadth, sectorBreadth) }
      return quotes
    }

    val alphaDateRange = "${quotes.firstOrNull()?.date} to ${quotes.lastOrNull()?.date}"
    val ovtlyrDateRange = "${ovtlyrQuotesByDate.keys.minOrNull()} to ${ovtlyrQuotesByDate.keys.maxOrNull()}"
    logger.debug("$symbol - AlphaVantage quotes: ${quotes.size} ($alphaDateRange), Ovtlyr quotes: ${ovtlyrQuotesByDate.size} ($ovtlyrDateRange)")

    var enrichedCount = 0
    var defaultCount = 0

    // Keep ALL quotes - enrich with Ovtlyr where available, use defaults otherwise
    val enrichedQuotes =
      quotes.map { quote ->
        val ovtlyrQuote = ovtlyrQuotesByDate[quote.date]

        if (ovtlyrQuote != null) {
          enrichQuoteWithOvtlyr(quote, ovtlyrQuote, ovtlyrStock, marketBreadth, sectorBreadth, spy)
          enrichedCount++
        } else {
          // No Ovtlyr data for this date - use default values
          setDefaultOvtlyrValues(quote, marketBreadth, sectorBreadth)
          defaultCount++
        }
        quote
      }

    logger.info("$symbol - Enriched: $enrichedCount quotes with Ovtlyr data, $defaultCount quotes with defaults (${enrichedCount * 100 / quotes.size}% Ovtlyr coverage)")

    return enrichedQuotes
  }

  /**
   * Set default values for Ovtlyr-specific fields when Ovtlyr data is not available.
   */
  private fun setDefaultOvtlyrValues(
    stockQuoteDomain: StockQuote,
    marketBreadth: Breadth?,
    sectorBreadth: Breadth?,
  ) {
    // Market breadth context (previous day's data)
    val marketBreadthQuote = marketBreadth?.getPreviousQuote(marketBreadth.getQuoteForDate(stockQuoteDomain.date))
    val marketInUptrend = marketBreadthQuote?.isInUptrend() ?: false
    val marketDonkeyScore = marketBreadthQuote?.donkeyChannelScore ?: 0

    // Sector breadth context (previous day's data)
    val sectorBreadthQuote = sectorBreadth?.getPreviousQuote(sectorBreadth.getQuoteForDate(stockQuoteDomain.date))
    val sectorInUptrend = sectorBreadthQuote?.isInUptrend() ?: false
    val sectorDonkeyScore = sectorBreadthQuote?.donkeyChannelScore ?: 0

    // Calculate market advancing percent from breadth data
    val marketAdvancing = calculateMarketAdvancingPercent(marketBreadth, stockQuoteDomain.date)

    stockQuoteDomain.apply {
      // Default Ovtlyr-specific fields to null/zero
      signal = null
      lastBuySignal = null
      lastSellSignal = null
      heatmap = 0.0
      previousHeatmap = 0.0
      sectorHeatmap = 0.0
      previousSectorHeatmap = 0.0
      sectorStocksInUptrend = 0
      sectorStocksInDowntrend = 0
      sectorBullPercentage = 0.0
      sectorIsInUptrend = sectorInUptrend
      sectorDonkeyChannelScore = sectorDonkeyScore
      marketIsInUptrend = marketInUptrend
      marketDonkeyChannelScore = marketDonkeyScore
      marketAdvancingPercent = marketAdvancing
      marketBullPercentage = marketBreadthQuote?.bullStocksPercentage ?: 0.0
      marketBullPercentage_10ema = marketBreadthQuote?.ema_10 ?: 0.0
      spySignal = null
      spyInUptrend = false
      spyHeatmap = 0.0
      spyPreviousHeatmap = 0.0
      previousQuoteDate = null
    }
  }

  /**
   * Enrich a single quote with Ovtlyr data.
   */
  private fun enrichQuoteWithOvtlyr(
    stockQuoteDomain: StockQuote,
    ovtlyrQuote: OvtlyrStockQuote,
    ovtlyrStock: OvtlyrStockInformation,
    marketBreadth: Breadth?,
    sectorBreadth: Breadth?,
    spy: OvtlyrStockInformation?,
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

    // SPY context (null when skipOvtlyrEnrichment is true)
    val currentSpySignal = spy?.getCurrentSignalFrom(ovtlyrQuote.getDate())
    val spyInUptrendValue = spy?.getQuoteForDate(ovtlyrQuote.getDate())?.isInUptrend ?: false
    val spyQuote = spy?.getPreviousQuote(spy.getQuoteForDate(ovtlyrQuote.getDate()))
    val spyHeatmapValue = spyQuote?.heatmap ?: 0.0
    val spyPreviousHeatmapValue = spy?.getPreviousQuote(spyQuote)?.heatmap ?: 0.0

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
    marketBreadth: Breadth?,
    date: LocalDate,
  ): Double {
    if (marketBreadth == null) return 0.0

    val breadthQuote = marketBreadth.getQuoteForDate(date) ?: return 0.0

    val total = breadthQuote.numberOfStocksInUptrend + breadthQuote.numberOfStocksInDowntrend

    if (total == 0) return 0.0

    return (breadthQuote.numberOfStocksInUptrend.toDouble() / total) * 100.0
  }
}
