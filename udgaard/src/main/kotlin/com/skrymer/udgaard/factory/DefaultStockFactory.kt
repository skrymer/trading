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
    spy: OvtlyrStockInformation,
  ): List<StockQuoteDomain>? {
    logger.info("Creating enriched quotes for $symbol from AlphaVantage data (${stockQuotes.size} quotes)")

    if (stockQuotes.isEmpty()) {
      logger.warn("No AlphaVantage quotes provided for $symbol")
      return null
    }

    // Step 1: Enrich with calculated technical indicators (EMAs, Donchian, trend)
    val quotesWithIndicators = technicalIndicatorService.enrichWithIndicators(stockQuotes, symbol)
    logger.info("Calculated technical indicators for $symbol")

    // Step 2: Enrich with ATR data from AlphaVantage
    val quotesWithATR = enrichWithATR(quotesWithIndicators, atrMap, symbol)
    logger.info("Enriched $symbol with ATR data")

    // Step 3: Enrich with ADX data from AlphaVantage
    val quotesWithADX = enrichWithADX(quotesWithATR, adxMap, symbol)
    logger.info("Enriched $symbol with ADX data")

    // Step 4: Enrich with Ovtlyr signals, heatmaps, and sector data
    val enrichedQuotes =
      ovtlyrEnrichmentService.enrichWithOvtlyr(
        quotesWithADX,
        symbol,
        marketBreadth,
        sectorBreadth,
        spy,
      )

    if (enrichedQuotes == null) {
      logger.error("Failed to enrich $symbol with Ovtlyr data")
      return null
    }

    logger.info("Successfully created ${enrichedQuotes.size} fully enriched quotes for $symbol")
    return enrichedQuotes
  }

  override fun createStock(
    symbol: String,
    sectorSymbol: String?,
    enrichedQuotes: List<StockQuoteDomain>,
    orderBlocks: List<OrderBlockDomain>,
    earnings: List<EarningDomain>,
  ): StockDomain {
    logger.info(
      "Creating Stock entity for $symbol with ${enrichedQuotes.size} quotes, ${orderBlocks.size} order blocks, and ${earnings.size} earnings",
    )

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

    logger.info("Enriching $symbol with ATR data from Alpha Vantage (${alphaATR.size} ATR values available)")

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

    logger.info("ATR enrichment complete for $symbol: $matchedCount quotes matched, $unmatchedCount unmatched")

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

    logger.info("Enriching $symbol with ADX data from Alpha Vantage (${alphaADX.size} ADX values available)")

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

    logger.info("ADX enrichment complete for $symbol: $matchedCount quotes matched, $unmatchedCount unmatched")

    return quotes
  }
}
