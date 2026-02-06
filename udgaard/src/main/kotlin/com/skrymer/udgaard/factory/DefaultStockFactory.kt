package com.skrymer.udgaard.factory

import com.skrymer.udgaard.domain.BreadthDomain
import com.skrymer.udgaard.domain.EarningDomain
import com.skrymer.udgaard.domain.OrderBlockDomain
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.service.OvtlyrEnrichmentService
import com.skrymer.udgaard.service.TechnicalIndicatorService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Default implementation of StockFactory.
 * Creates Stock entities from AlphaVantage data enriched with Ovtlyr indicators.
 *
 * NEW ARCHITECTURE:
 * - AlphaVantage is the PRIMARY data source (OHLCV + volume + ATR)
 * - We calculate technical indicators (EMAs, Donchian, trend)
 * - Ovtlyr enriches with signals and heatmaps
 */
@Component
class DefaultStockFactory(
  private val technicalIndicatorService: TechnicalIndicatorService,
  private val ovtlyrEnrichmentService: OvtlyrEnrichmentService,
) : StockFactory {
  private val logger = LoggerFactory.getLogger(DefaultStockFactory::class.java)

  override fun enrichQuotes(
    symbol: String,
    stockQuotes: List<StockQuoteDomain>,
    atrMap: Map<LocalDate, Double>?,
    adxMap: Map<LocalDate, Double>?,
    marketBreadth: BreadthDomain?,
    sectorBreadth: BreadthDomain?,
    spy: OvtlyrStockInformation?,
    skipOvtlyrEnrichment: Boolean,
  ): List<StockQuoteDomain>? {
    if (stockQuotes.isEmpty()) {
      logger.warn("No AlphaVantage quotes provided for $symbol")
      return null
    }

    // Step 1: Enrich with calculated technical indicators (EMAs, Donchian, trend)
    val quotesWithIndicators = technicalIndicatorService.enrichWithIndicators(stockQuotes, symbol)

    // Step 2: Enrich with ATR data from AlphaVantage
    val quotesWithATR = enrichWithATR(quotesWithIndicators, atrMap, symbol)

    // Step 3: Enrich with ADX data from AlphaVantage
    val quotesWithADX = enrichWithADX(quotesWithATR, adxMap, symbol)

    // Step 4: Enrich with Ovtlyr signals, heatmaps, and sector data (if not skipped)
    val enrichedQuotes =
      if (skipOvtlyrEnrichment) {
        // Skip Ovtlyr enrichment - use default values
        ovtlyrEnrichmentService.enrichWithOvtlyr(
          quotesWithADX,
          symbol,
          marketBreadth,
          sectorBreadth,
          spy,
          skipOvtlyrEnrichment = true,
        )
      } else {
        // Perform full Ovtlyr enrichment
        ovtlyrEnrichmentService.enrichWithOvtlyr(
          quotesWithADX,
          symbol,
          marketBreadth,
          sectorBreadth,
          spy,
          skipOvtlyrEnrichment = false,
        )
      }

    if (enrichedQuotes == null) {
      logger.error("Failed to enrich $symbol - enrichment returned null")
      return null
    }

    return enrichedQuotes
  }

  override fun createStock(
    symbol: String,
    sectorSymbol: String?,
    enrichedQuotes: List<StockQuoteDomain>,
    orderBlocks: List<OrderBlockDomain>,
    earnings: List<EarningDomain>,
  ): StockDomain {
    val stock =
      StockDomain(
        symbol = symbol,
        sectorSymbol = sectorSymbol,
        quotes = enrichedQuotes.toMutableList(),
        orderBlocks = orderBlocks.toMutableList(),
        earnings = earnings.toMutableList(),
        ovtlyrPerformance = 0.0,
      )

    return stock
  }

  // ===================================================================
  // ATR ENRICHMENT
  // ===================================================================

  /**
   * Enrich quotes with ATR data from AlphaVantage.
   * Mutates the quote objects to add ATR data.
   */
  private fun enrichWithATR(
    quotes: List<StockQuoteDomain>,
    alphaATR: Map<LocalDate, Double>?,
    symbol: String,
  ): List<StockQuoteDomain> {
    if (alphaATR == null) {
      logger.warn("No AlphaVantage ATR available for $symbol - ATR will be 0.0")
      return quotes
    }

    var matchedCount = 0
    var unmatchedCount = 0

    quotes.forEach { quote ->
      val matchingATR = alphaATR[quote.date]
      if (matchingATR != null) {
        quote.atr = matchingATR
        matchedCount++
      } else {
        unmatchedCount++
      }
    }

    return quotes
  }

  // ===================================================================
  // ADX ENRICHMENT
  // ===================================================================

  /**
   * Enrich quotes with ADX data from AlphaVantage.
   * Mutates the quote objects to add ADX data.
   */
  private fun enrichWithADX(
    quotes: List<StockQuoteDomain>,
    alphaADX: Map<LocalDate, Double>?,
    symbol: String,
  ): List<StockQuoteDomain> {
    if (alphaADX == null) {
      logger.warn("No AlphaVantage ADX available for $symbol - ADX will be null")
      return quotes
    }

    var matchedCount = 0
    var unmatchedCount = 0

    quotes.forEach { quote ->
      val matchingADX = alphaADX[quote.date]
      if (matchingADX != null) {
        quote.adx = matchingADX
        matchedCount++
      } else {
        unmatchedCount++
      }
    }

    return quotes
  }
}
