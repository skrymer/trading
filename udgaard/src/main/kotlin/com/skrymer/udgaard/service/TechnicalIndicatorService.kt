package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.StockQuote
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for calculating technical indicators from OHLCV price data.
 *
 * All calculations are performed on historical price data to enrich stock quotes
 * with technical analysis indicators used by trading strategies.
 *
 * Indicators supported:
 * - EMA (Exponential Moving Average) - various periods (5, 10, 20, 50, 200)
 * - Donchian Channels - support/resistance levels
 * - Trend determination - uptrend/downtrend classification
 *
 * Note: ATR is fetched from AlphaVantage API, not calculated here.
 */
@Service
class TechnicalIndicatorService {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(TechnicalIndicatorService::class.java)
    }

    /**
     * Enrich stock quotes with all technical indicators.
     *
     * This is the main method that calculates and adds:
     * - EMAs (5, 10, 20, 50)
     * - Donchian upper band
     * - Trend classification
     *
     * Note: ATR should already be populated from AlphaVantage API data.
     *
     * @param quotes - List of StockQuote sorted by date (oldest first)
     * @param symbol - Stock symbol for logging
     * @return Enriched list of StockQuote with indicators populated
     */
    fun enrichWithIndicators(quotes: List<StockQuote>, symbol: String): List<StockQuote> {
        if (quotes.isEmpty()) {
            logger.warn("No quotes provided for $symbol, skipping indicator calculation")
            return quotes
        }

        logger.info("Calculating technical indicators for $symbol (${quotes.size} quotes)")

        // Extract price data for calculations
        val closePrices = quotes.map { it.closePrice }

        // Calculate all EMAs
        val ema5Values = calculateEMA(closePrices, 5)
        val ema10Values = calculateEMA(closePrices, 10)
        val ema20Values = calculateEMA(closePrices, 20)
        val ema50Values = calculateEMA(closePrices, 50)

        // Enrich each quote with calculated values
        return quotes.mapIndexed { index, quote ->
            quote.apply {
                closePriceEMA5 = ema5Values.getOrNull(index) ?: 0.0
                closePriceEMA10 = ema10Values.getOrNull(index) ?: 0.0
                closePriceEMA20 = ema20Values.getOrNull(index) ?: 0.0
                closePriceEMA50 = ema50Values.getOrNull(index) ?: 0.0
                donchianUpperBand = calculateDonchianUpperBand(quotes, index, 5)
                trend = determineTrend(quote)
            }
        }
    }

    /**
     * Calculate EMA (Exponential Moving Average) for a series of prices.
     *
     * Formula:
     * - EMA = (Close - PreviousEMA) × Multiplier + PreviousEMA
     * - Multiplier = 2 / (Period + 1)
     * - First EMA = SMA of first 'period' prices (bootstrap)
     *
     * @param prices - List of prices (oldest first)
     * @param period - EMA period (e.g., 5, 10, 20, 50, 200)
     * @return List of EMA values (same length as prices)
     */
    fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) {
            logger.warn("Not enough data points (${prices.size}) for EMA calculation (period: $period)")
            return List(prices.size) { 0.0 }
        }

        val multiplier = 2.0 / (period + 1)
        val emaValues = mutableListOf<Double>()

        // Start with SMA for the first 'period' prices (bootstrap the EMA)
        var ema = prices.take(period).average()

        // Fill in zeros for the initial period - 1 values
        repeat(period - 1) {
            emaValues.add(0.0)
        }

        // Add the first EMA (which is the SMA)
        emaValues.add(ema)

        // Calculate EMA for remaining prices
        for (i in period until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
            emaValues.add(ema)
        }

        return emaValues
    }

    /**
     * Calculate Donchian Upper Band (highest high over lookback period).
     *
     * @param quotes - All quotes for the stock
     * @param currentIndex - Index of current quote
     * @param periods - Lookback period (default 5)
     * @return Donchian upper band value
     */
    fun calculateDonchianUpperBand(quotes: List<StockQuote>, currentIndex: Int, periods: Int = 5): Double {
        if (currentIndex < periods) {
            // Not enough history, use available data
            val window = quotes.subList(0, currentIndex + 1)
            return window.maxOfOrNull { it.high } ?: 0.0
        }

        val window = quotes.subList(currentIndex - periods + 1, currentIndex + 1)
        return window.maxOfOrNull { it.high } ?: 0.0
    }

    /**
     * Calculate Donchian Lower Band (lowest low over lookback period).
     *
     * @param quotes - All quotes for the stock
     * @param currentIndex - Index of current quote
     * @param periods - Lookback period (default 5)
     * @return Donchian lower band value
     */
    fun calculateDonchianLowerBand(quotes: List<StockQuote>, currentIndex: Int, periods: Int = 5): Double {
        if (currentIndex < periods) {
            val window = quotes.subList(0, currentIndex + 1)
            return window.minOfOrNull { it.low } ?: 0.0
        }

        val window = quotes.subList(currentIndex - periods + 1, currentIndex + 1)
        return window.minOfOrNull { it.low } ?: 0.0
    }

    /**
     * Determine trend based on EMA alignment.
     *
     * Uptrend criteria:
     * - EMA5 > EMA10 > EMA20 AND
     * - Price > EMA50
     *
     * Otherwise: Downtrend
     *
     * @param quote - StockQuote with EMAs already calculated
     * @return "Uptrend" or "Downtrend"
     */
    fun determineTrend(quote: StockQuote): String {
        val emaAligned = quote.closePriceEMA5 > quote.closePriceEMA10 &&
                         quote.closePriceEMA10 > quote.closePriceEMA20

        val aboveEma50 = quote.closePrice > quote.closePriceEMA50

        return if (emaAligned && aboveEma50) "Uptrend" else "Downtrend"
    }

    /**
     * Calculate number of consecutive days price has been above EMA.
     *
     * @param prices - List of prices
     * @param ema - List of EMA values
     * @param currentIndex - Current index to count from
     * @return Number of consecutive days above EMA
     */
    fun calculateDaysAboveEMA(prices: List<Double>, ema: List<Double>, currentIndex: Int): Int {
        var count = 0

        for (i in currentIndex downTo 0) {
            if (i >= ema.size || ema[i] == 0.0) break

            if (prices[i] > ema[i]) {
                count++
            } else {
                break
            }
        }

        return count
    }

    /**
     * Enrich SPY quotes with 200-day EMA and 50-day EMA.
     * Used for market regime filter calculations.
     *
     * @param quotes - SPY quotes sorted by date
     * @return Enriched SPY quotes with long-term indicators
     */
    fun enrichSpyWithLongTermIndicators(quotes: List<StockQuote>): List<StockQuote> {
        if (quotes.isEmpty()) return quotes

        logger.info("Calculating long-term indicators for SPY (${quotes.size} quotes)")

        val closePrices = quotes.map { it.closePrice }

        val ema50Values = calculateEMA(closePrices, 50)
        val ema200Values = calculateEMA(closePrices, 200)

        return quotes.mapIndexed { index, quote ->
            quote.apply {
                spyEMA50 = ema50Values.getOrNull(index) ?: 0.0
                spyEMA200 = ema200Values.getOrNull(index) ?: 0.0
                spySMA200 = ema200Values.getOrNull(index) ?: 0.0 // Use EMA200 for SMA200 field
                spyDaysAbove200SMA = calculateDaysAboveEMA(closePrices, ema200Values, index)
            }
        }
    }
}
