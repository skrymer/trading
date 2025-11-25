package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockQuote
import com.skrymer.udgaard.model.Breadth
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for calculating technical indicators (EMAs, SMAs, ATR, Donchian channels, etc.).
 *
 * This service contains pure calculation logic that can be tested independently
 * and reused across the application (backtesting, alerts, analysis, etc.).
 *
 * All methods are stateless and side-effect free.
 */
@Service
class TechnicalIndicatorCalculator {

    // ===================================================================
    // MOVING AVERAGES
    // ===================================================================

    /**
     * Calculate Exponential Moving Average (EMA) for any period.
     *
     * EMA formula:
     * - Multiplier = 2 / (period + 1)
     * - EMA = (Close - Previous EMA) × Multiplier + Previous EMA
     * - First EMA starts with SMA
     *
     * @param prices List of prices (must be sorted chronologically)
     * @param period Number of periods (e.g., 5, 10, 20, 50, 200)
     * @return The EMA value, or 0.0 if insufficient data
     */
    fun calculateEMA(prices: List<Double>, period: Int): Double {
        if (prices.size < period) return 0.0

        val multiplier = 2.0 / (period + 1)
        var ema = prices.take(period).average() // Start with SMA

        for (i in period until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
        }

        return ema
    }

    /**
     * Calculate EMA from stock quotes up to a specific date.
     *
     * @param stock Stock information containing historical quotes
     * @param currentDate Calculate EMA up to and including this date
     * @param period Number of periods
     * @return The EMA value, or 0.0 if insufficient data
     */
    fun calculateEMA(
        stock: OvtlyrStockInformation,
        currentDate: LocalDate,
        period: Int
    ): Double {
        val prices = extractPrices(stock, currentDate)
        return calculateEMA(prices, period)
    }

    /**
     * Calculate Simple Moving Average (SMA) for any period.
     *
     * SMA formula: Average of the last N prices
     *
     * @param prices List of prices (must be sorted chronologically)
     * @param period Number of periods
     * @return The SMA value, or 0.0 if insufficient data
     */
    fun calculateSMA(prices: List<Double>, period: Int): Double {
        if (prices.size < period) return 0.0
        return prices.takeLast(period).average()
    }

    /**
     * Calculate SMA from stock quotes up to a specific date.
     *
     * @param stock Stock information containing historical quotes
     * @param currentDate Calculate SMA up to and including this date
     * @param period Number of periods
     * @return The SMA value, or 0.0 if insufficient data
     */
    fun calculateSMA(
        stock: OvtlyrStockInformation,
        currentDate: LocalDate,
        period: Int
    ): Double {
        val prices = extractPrices(stock, currentDate)
        return calculateSMA(prices, period)
    }

    // ===================================================================
    // AVERAGE TRUE RANGE (ATR)
    // ===================================================================

    /**
     * Calculate Average True Range (ATR) using Wilder's smoothing method.
     *
     * ATR measures market volatility by decomposing the entire range of an asset price.
     *
     * Wilder's ATR formula:
     * - First ATR = Average of first N True Range values (SMA)
     * - Subsequent ATR = [(Previous ATR × (N-1)) + Current TR] / N
     *
     * This is equivalent to an exponential smoothing with α = 1/N
     *
     * @param stock Stock information containing historical quotes
     * @param currentDate Calculate ATR up to and including this date
     * @param periods Number of periods (default 14)
     * @return The ATR value, or 0.0 if insufficient data
     */
    fun calculateATR(
        stock: OvtlyrStockInformation,
        currentDate: LocalDate,
        periods: Int = 14
    ): Double {
        val allQuotes = stock.getQuotes()
            .sortedBy { it?.getDate() }  // Sort chronologically

        // Find the index of the current date
        val currentIndex = allQuotes.indexOfFirst { it?.getDate() == currentDate }
        if (currentIndex < 0) return 0.0

        // We need at least 'periods + 1' quotes (including one before for TR calculation)
        if (currentIndex < periods) return 0.0

        // Calculate TR values starting from index 1 (we need previous quote for TR)
        val trValues = mutableListOf<Double>()
        for (i in 1..currentIndex) {
            val quote = allQuotes[i] ?: continue
            val previousQuote = allQuotes[i - 1] ?: continue

            val previousClose = previousQuote.getClosePrice()
            val highLowDiff = quote.high - quote.low
            val highPreviousCloseDiff = kotlin.math.abs(quote.high - previousClose)
            val lowPreviousCloseDiff = kotlin.math.abs(quote.low - previousClose)
            val tr = highLowDiff.coerceAtLeast(highPreviousCloseDiff.coerceAtLeast(lowPreviousCloseDiff))

            trValues.add(tr)
        }

        if (trValues.size < periods) return 0.0

        // First ATR is the simple average of the first 'periods' TR values
        var atr = trValues.take(periods).average()

        // Apply Wilder's smoothing for subsequent values
        for (i in periods until trValues.size) {
            atr = ((atr * (periods - 1)) + trValues[i]) / periods
        }

        return atr
    }

    /**
     * Calculate True Range (TR) for a single quote.
     *
     * TR formula: max[(High - Low), abs(High - Previous Close), abs(Low - Previous Close)]
     *
     * @param stock Stock information to find previous quote
     * @param quote Current quote
     * @return The True Range value
     */
    fun calculateTR(stock: OvtlyrStockInformation, quote: OvtlyrStockQuote): Double {
        val previousQuote = stock.getPreviousQuote(quote)
        val previousClose = previousQuote?.getClosePrice() ?: quote.high

        val highLowDiff = quote.high - quote.low
        val highPreviousCloseDiff = kotlin.math.abs(quote.high - previousClose)
        val lowPreviousCloseDiff = kotlin.math.abs(quote.low - previousClose)

        return highLowDiff.coerceAtLeast(highPreviousCloseDiff.coerceAtLeast(lowPreviousCloseDiff))
    }

    // ===================================================================
    // DONCHIAN CHANNELS
    // ===================================================================

    /**
     * Calculate Donchian Channel upper or lower band for stock prices.
     *
     * Donchian Channels show the highest high and lowest low over N periods.
     *
     * @param stock Stock information
     * @param currentQuote Current quote
     * @param periods Number of periods to look back (default 5)
     * @param isUpper True for upper band (max), false for lower band (min)
     * @return The band value, or 0.0 if insufficient data
     */
    fun calculateDonchianBand(
        stock: OvtlyrStockInformation,
        currentQuote: OvtlyrStockQuote,
        periods: Int = 5,
        isUpper: Boolean = true
    ): Double {
        val previousQuotes = stock.getPreviousQuotes(currentQuote, periods)

        if (previousQuotes.isEmpty()) return 0.0

        return if (isUpper) {
            previousQuotes.maxOfOrNull { it.getClosePrice() } ?: 0.0
        } else {
            previousQuotes.minOfOrNull { it.getClosePrice() } ?: 0.0
        }
    }

    /**
     * Calculate Donchian Channel band for breadth data (market/sector).
     *
     * Uses number of stocks in uptrend instead of price.
     *
     * @param breadth Market or sector breadth data
     * @param currentDate Calculate up to this date
     * @param periods Number of periods to look back (default 4)
     * @param isUpper True for upper band (max), false for lower band (min)
     * @return The band value, or 0.0 if insufficient data
     */
    fun calculateDonchianBandBreadth(
        breadth: Breadth?,
        currentDate: LocalDate,
        periods: Int = 4,
        isUpper: Boolean = true
    ): Double {
        val previousQuotes = breadth?.getPreviousQuotes(currentDate, periods) ?: return 0.0

        if (previousQuotes.isEmpty()) return 0.0

        val values = previousQuotes.map { it.numberOfStocksInUptrend.toDouble() }

        return if (isUpper) {
            values.maxOrNull() ?: 0.0
        } else {
            values.minOrNull() ?: 0.0
        }
    }

    // ===================================================================
    // MARKET REGIME INDICATORS
    // ===================================================================

    /**
     * Count consecutive days a stock has been above its SMA.
     *
     * This is useful for market regime filters (e.g., SPY above 200-day SMA).
     *
     * @param stock Stock information (typically SPY)
     * @param currentDate Count up to this date
     * @param period SMA period to compare against (default 200)
     * @return Number of consecutive days above SMA
     */
    fun calculateDaysAboveSMA(
        stock: OvtlyrStockInformation,
        currentDate: LocalDate,
        period: Int = 200
    ): Int {
        val quotes = stock.getQuotes()
            .sortedByDescending { it?.getDate() }
            .filter { it?.getDate()?.isBefore(currentDate) == true || it?.getDate()?.equals(currentDate) == true }

        if (quotes.size < period) return 0

        var count = 0
        for (i in quotes.indices) {
            val quote = quotes[i] ?: continue

            // Calculate SMA for this point in time
            val pricesUpToThisPoint = quotes.subList(i, quotes.size)
                .mapNotNull { it?.getClosePrice() }
                .reversed()

            if (pricesUpToThisPoint.size < period) break

            val sma = pricesUpToThisPoint.takeLast(period).average()

            if (quote.getClosePrice() > sma) {
                count++
            } else {
                break
            }
        }

        return count
    }

    /**
     * Calculate percentage of stocks advancing (market breadth percentage).
     *
     * @param marketBreadth Market breadth data
     * @param currentDate Date to calculate for
     * @return Percentage of stocks in uptrend (0.0 to 100.0)
     */
    fun calculateAdvancingPercent(
        marketBreadth: Breadth?,
        currentDate: LocalDate
    ): Double {
        if (marketBreadth == null) return 0.0

        val breadthQuote = marketBreadth.getQuoteForDate(currentDate) ?: return 0.0
        val total = breadthQuote.numberOfStocksInUptrend + breadthQuote.numberOfStocksInDowntrend

        if (total == 0) return 0.0

        return (breadthQuote.numberOfStocksInUptrend.toDouble() / total) * 100.0
    }

    // ===================================================================
    // HELPER METHODS
    // ===================================================================

    /**
     * Extract price data from stock quotes up to a specific date.
     * Prices are returned in chronological order.
     */
    private fun extractPrices(
        stock: OvtlyrStockInformation,
        currentDate: LocalDate
    ): List<Double> {
        return stock.getQuotes()
            .sortedBy { it?.getDate() }
            .filter { it?.getDate()?.isBefore(currentDate) == true || it?.getDate()?.equals(currentDate) == true }
            .mapNotNull { it?.getClosePrice() }
    }
}
