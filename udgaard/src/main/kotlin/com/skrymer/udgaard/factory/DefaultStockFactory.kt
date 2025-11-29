package com.skrymer.udgaard.factory

import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockQuote
import com.skrymer.udgaard.model.Breadth
import com.skrymer.udgaard.model.OrderBlock
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.service.TechnicalIndicatorCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Default implementation of StockFactory.
 * Creates Stock entities from Ovtlyr data with volume and order block enrichment.
 *
 * This factory contains ALL the logic for converting Ovtlyr DTOs to domain models.
 * Technical indicator calculations are delegated to TechnicalIndicatorCalculator.
 */
@Component
class DefaultStockFactory(
    private val technicalIndicatorCalculator: TechnicalIndicatorCalculator
) : StockFactory {

    private val logger = LoggerFactory.getLogger(DefaultStockFactory::class.java)

    override fun createQuotes(
        stockInformation: OvtlyrStockInformation,
        marketBreadth: Breadth?,
        sectorBreadth: Breadth?,
        spy: OvtlyrStockInformation,
        alphaQuotes: List<StockQuote>?,
        alphaATR: Map<java.time.LocalDate, Double>?
    ): List<StockQuote> {
        // Step 1: Convert Ovtlyr quotes to StockQuotes
        val quotes = stockInformation.getQuotes().mapNotNull { ovtlyrQuote ->
            ovtlyrQuote?.let {
                createStockQuote(it, stockInformation, marketBreadth, sectorBreadth, spy)
            }
        }

        // Step 2: Enrich quotes with AlphaVantage volume data
        val volumeEnrichedQuotes = enrichQuotesWithVolume(
            quotes,
            alphaQuotes,
            stockInformation.stockName ?: "Unknown"
        )

        // Step 3: Enrich quotes with AlphaVantage ATR data
        return enrichQuotesWithATR(
            volumeEnrichedQuotes,
            alphaATR,
            stockInformation.stockName ?: "Unknown"
        )
    }

    override fun createStock(
        stockInformation: OvtlyrStockInformation,
        marketBreadth: Breadth?,
        sectorBreadth: Breadth?,
        spy: OvtlyrStockInformation,
        alphaQuotes: List<StockQuote>?,
        calculatedOrderBlocks: List<OrderBlock>,
        alphaATR: Map<java.time.LocalDate, Double>?
    ): Stock {
        // Step 1: Create and enrich quotes
        val enrichedQuotes = createQuotes(
            stockInformation,
            marketBreadth,
            sectorBreadth,
            spy,
            alphaQuotes,
            alphaATR
        )

        // Step 2: Convert Ovtlyr order blocks
        val ovtlyrOrderBlocks = stockInformation.orderBlocks.map {
            it.toModel(stockInformation)
        }

        // Step 3: Combine all order blocks
        val allOrderBlocks = ovtlyrOrderBlocks + calculatedOrderBlocks

        // Step 4: Create Stock entity
        return Stock(
            symbol = stockInformation.stockName,
            sectorSymbol = stockInformation.sectorSymbol,
            quotes = enrichedQuotes.toMutableList(),
            orderBlocks = allOrderBlocks.toMutableList(),
            ovtlyrPerformance = 0.0
        )
    }

    // ===================================================================
    // CORE CONVERSION METHOD
    // ===================================================================

    /**
     * Convert a single OvtlyrStockQuote to StockQuote domain model.
     * Contains ALL the business logic previously in OvtlyrStockQuote.toModel()
     */
    private fun createStockQuote(
        ovtlyrQuote: OvtlyrStockQuote,
        stockInformation: OvtlyrStockInformation,
        marketBreadth: Breadth?,
        sectorBreadth: Breadth?,
        spy: OvtlyrStockInformation
    ): StockQuote {
        val date = ovtlyrQuote.getDate()

        // Get historical context
        val previousQuote = stockInformation.getPreviousQuote(ovtlyrQuote)
        val previousPreviousQuote = previousQuote?.let {
            stockInformation.getPreviousQuote(it)
        }

        // Analyze market breadth
        val marketBreadthQuote = marketBreadth?.getPreviousQuote(
            marketBreadth.getQuoteForDate(date)
        )
        val marketIsInUptrend = marketBreadthQuote?.isInUptrend() ?: false
        val marketDonkeyChannelScore = marketBreadthQuote?.donkeyChannelScore ?: 0

        // Analyze sector breadth
        val sectorBreadthQuote = sectorBreadth?.getPreviousQuote(
            sectorBreadth.getQuoteForDate(date)
        )
        val sectorIsInUptrend = sectorBreadthQuote?.isInUptrend() ?: false
        val sectorDonkeyChannelScore = sectorBreadthQuote?.donkeyChannelScore ?: 0

        // Get signal history
        val lastBuySignal = stockInformation.getLastBuySignal(date)
        val lastSellSignal = stockInformation.getLastSellSignal(date)

        // Analyze SPY context
        val spySignal = spy.getCurrentSignalFrom(date)
        val spyIsInUptrend = spy.getQuoteForDate(date)?.isInUptrend ?: false
        val spyQuote = spy.getPreviousQuote(spy.getQuoteForDate(date))
        val spyHeatmap = spyQuote?.heatmap ?: 0.0
        val spyPreviousHeatmap = spy.getPreviousQuote(spyQuote)?.heatmap ?: 0.0

        // ===================================================================
        // CALCULATE TECHNICAL INDICATORS USING THE SERVICE
        // ===================================================================

        // Market regime indicators
        val spyEMA200 = technicalIndicatorCalculator.calculateEMA(spy, date, 200)
        val spySMA200 = technicalIndicatorCalculator.calculateSMA(spy, date, 200)
        val spyEMA50 = technicalIndicatorCalculator.calculateEMA(spy, date, 50)
        val spyDaysAbove200SMA = technicalIndicatorCalculator.calculateDaysAboveSMA(spy, date, 200)
        val marketAdvancingPercent = technicalIndicatorCalculator.calculateAdvancingPercent(marketBreadth, date)

        // Stock EMAs (use Ovtlyr values if available, otherwise calculate)
        val ema5 = ovtlyrQuote.getClosePriceEMA5()
            ?: technicalIndicatorCalculator.calculateEMA(stockInformation, date, 5)
        val ema10 = ovtlyrQuote.closePriceEMA10
            ?: technicalIndicatorCalculator.calculateEMA(stockInformation, date, 10)
        val ema20 = ovtlyrQuote.getClosePriceEMA20()
            ?: technicalIndicatorCalculator.calculateEMA(stockInformation, date, 20)
        val ema50 = ovtlyrQuote.getClosePriceEMA50()
            ?: technicalIndicatorCalculator.calculateEMA(stockInformation, date, 50)

        // ATR
        val atr = technicalIndicatorCalculator.calculateATR(stockInformation, date)

        // Donchian channels
        val donchianUpperBand = technicalIndicatorCalculator.calculateDonchianBand(
            stockInformation, ovtlyrQuote, 5, isUpper = true
        )
        val donchianUpperBandMarket = technicalIndicatorCalculator.calculateDonchianBandBreadth(
            marketBreadth, date, 4, isUpper = true
        )
        val donchianUpperBandSector = technicalIndicatorCalculator.calculateDonchianBandBreadth(
            sectorBreadth, date, 4, isUpper = true
        )
        val donchianLowerBandMarket = technicalIndicatorCalculator.calculateDonchianBandBreadth(
            marketBreadth, date, 4, isUpper = false
        )
        val donchianLowerBandSector = technicalIndicatorCalculator.calculateDonchianBandBreadth(
            sectorBreadth, date, 4, isUpper = false
        )

        // Construct StockQuote
        return StockQuote(
            symbol = ovtlyrQuote.getSymbol() ?: "",
            date = date,
            closePrice = ovtlyrQuote.getClosePrice(),
            openPrice = ovtlyrQuote.getOpenPrice() ?: 0.0,
            heatmap = previousQuote?.heatmap ?: 0.0,
            previousHeatmap = previousPreviousQuote?.heatmap ?: 0.0,
            sectorHeatmap = previousQuote?.sectorHeatmap ?: 0.0,
            previousSectorHeatmap = previousPreviousQuote?.sectorHeatmap ?: 0.0,
            sectorIsInUptrend = sectorIsInUptrend,
            sectorDonkeyChannelScore = sectorDonkeyChannelScore,
            signal = ovtlyrQuote.signal,
            closePriceEMA10 = ema10,
            closePriceEMA20 = ema20,
            closePriceEMA5 = ema5,
            closePriceEMA50 = ema50,
            trend = ovtlyrQuote.trend,
            lastBuySignal = lastBuySignal,
            lastSellSignal = lastSellSignal,
            spySignal = spySignal,
            spyIsInUptrend = spyIsInUptrend,
            spyHeatmap = spyHeatmap,
            spyPreviousHeatmap = spyPreviousHeatmap,
            spyEMA200 = spyEMA200,
            spySMA200 = spySMA200,
            spyEMA50 = spyEMA50,
            spyDaysAbove200SMA = spyDaysAbove200SMA,
            marketAdvancingPercent = marketAdvancingPercent,
            marketIsInUptrend = marketIsInUptrend,
            marketDonkeyChannelScore = marketDonkeyChannelScore,
            previousQuoteDate = previousQuote?.getDate(),
            atr = atr,
            sectorStocksInDowntrend = ovtlyrQuote.sectorDowntrend,
            sectorStocksInUptrend = ovtlyrQuote.sectorUptrend,
            sectorBullPercentage = previousQuote?.sectorBullPercentage ?: 0.0,
            high = ovtlyrQuote.high,
            low = ovtlyrQuote.low,
            donchianUpperBand = donchianUpperBand,
            donchianUpperBandMarket = donchianUpperBandMarket,
            donchianUpperBandSector = donchianUpperBandSector,
            donchianLowerBandMarket = donchianLowerBandMarket,
            donchianLowerBandSector = donchianLowerBandSector
        )
    }

    // ===================================================================
    // VOLUME ENRICHMENT
    // ===================================================================

    /**
     * Enrich quotes with volume data from AlphaVantage.
     * Mutates the quote objects to add volume data.
     */
    private fun enrichQuotesWithVolume(
        quotes: List<StockQuote>,
        alphaQuotes: List<StockQuote>?,
        symbol: String
    ): List<StockQuote> {
        if (alphaQuotes == null) {
            logger.warn("No AlphaVantage quotes available for volume enrichment for $symbol")
            return quotes
        }

        logger.info("Enriching $symbol with volume data from Alpha Vantage (${alphaQuotes.size} Alpha quotes available)")
        logger.debug("Ovtlyr date range: ${quotes.firstOrNull()?.date} to ${quotes.lastOrNull()?.date}")
        logger.debug("Alpha Vantage date range: ${alphaQuotes.firstOrNull()?.date} to ${alphaQuotes.lastOrNull()?.date}")

        var matchedCount = 0
        var unmatchedCount = 0

        quotes.forEach { quote ->
            val matchingAlphaQuote = alphaQuotes.find { it.date == quote.date }
            if (matchingAlphaQuote != null) {
                quote.volume = matchingAlphaQuote.volume
                matchedCount++
            } else {
                unmatchedCount++
            }
        }

        logger.info("Volume enrichment complete for $symbol: $matchedCount quotes matched, $unmatchedCount unmatched")

        // Log sample of enriched quotes
        val quotesWithVolume = quotes.filter { it.volume > 0 }
        logger.info("Quotes with volume > 0: ${quotesWithVolume.size} out of ${quotes.size}")

        return quotes
    }

    // ===================================================================
    // ATR ENRICHMENT
    // ===================================================================

    /**
     * Enrich quotes with ATR data from AlphaVantage.
     * Mutates the quote objects to add ATR data.
     */
    private fun enrichQuotesWithATR(
        quotes: List<StockQuote>,
        alphaATR: Map<java.time.LocalDate, Double>?,
        symbol: String
    ): List<StockQuote> {
        if (alphaATR == null) {
            logger.warn("No AlphaVantage ATR available for enrichment for $symbol - using calculated ATR")
            return quotes
        }

        logger.info("Enriching $symbol with ATR data from Alpha Vantage (${alphaATR.size} ATR values available)")
        logger.debug("Quotes date range: ${quotes.firstOrNull()?.date} to ${quotes.lastOrNull()?.date}")
        logger.debug("Alpha Vantage ATR date range: ${alphaATR.keys.minOrNull()} to ${alphaATR.keys.maxOrNull()}")

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

        // Log sample of enriched quotes
        val quotesWithATR = quotes.filter { it.atr > 0 }
        logger.info("Quotes with ATR > 0: ${quotesWithATR.size} out of ${quotes.size}")
        if (quotesWithATR.isNotEmpty()) {
            val sampleQuote = quotesWithATR.first()
            logger.debug("Sample ATR enriched quote: date=${sampleQuote.date}, atr=${sampleQuote.atr}")
        }

        return quotes
    }
}
