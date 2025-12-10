package com.skrymer.udgaard.factory

import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.Breadth
import com.skrymer.udgaard.model.Earning
import com.skrymer.udgaard.model.OrderBlock
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
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
    stockQuotes: List<StockQuote>,
    atrMap: Map<LocalDate, Double>?,
    marketBreadth: Breadth?,
    sectorBreadth: Breadth?,
    spy: OvtlyrStockInformation,
  ): List<StockQuote>? {
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

    // Step 3: Enrich with Ovtlyr signals, heatmaps, and sector data
    val enrichedQuotes =
      ovtlyrEnrichmentService.enrichWithOvtlyr(
        quotesWithATR,
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
    enrichedQuotes: List<StockQuote>,
    orderBlocks: List<OrderBlock>,
    earnings: List<Earning>,
  ): Stock {
    logger.info(
      "Creating Stock entity for $symbol with ${enrichedQuotes.size} quotes, ${orderBlocks.size} order blocks, and ${earnings.size} earnings",
    )

    val stock =
      Stock(
        symbol = symbol,
        sectorSymbol = sectorSymbol,
        quotes = enrichedQuotes.toMutableList(),
        orderBlocks = orderBlocks.toMutableList(),
        earnings = earnings.toMutableList(),
        ovtlyrPerformance = 0.0,
      )

    // Set the stock reference on all earnings for proper JPA relationship
    stock.earnings.forEach { it.stock = stock }

    // Set the stock reference on all order blocks for proper JPA relationship
    stock.orderBlocks.forEach { it.stock = stock }

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
    quotes: List<StockQuote>,
    alphaATR: Map<LocalDate, Double>?,
    symbol: String,
  ): List<StockQuote> {
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
}
