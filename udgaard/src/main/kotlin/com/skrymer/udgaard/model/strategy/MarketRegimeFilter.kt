package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Market Regime Filter - determines if market conditions are favorable for trading.
 *
 * This filter helps avoid trading during choppy/volatile markets by requiring:
 * 1. SPY above 200-day SMA for sustained period (not just a spike)
 * 2. Golden cross maintained (50 EMA > 200 EMA)
 * 3. Strong market breadth (>60% of stocks advancing)
 *
 * Based on analysis showing strategy edge declines from 2.64% (2021-2024 avg) to 0.50% (2025)
 * in choppy market conditions. This filter aims to restore edge to 2.0%+ by avoiding
 * unfavorable market regimes.
 */
object MarketRegimeFilter {

    private val logger: Logger = LoggerFactory.getLogger(MarketRegimeFilter::class.java)

    /**
     * Minimum days SPY must be above 200-day SMA to consider it a sustained trend.
     * Set to 20 days to filter out brief spikes that quickly reverse.
     */
    private const val MIN_DAYS_ABOVE_200_SMA = 20

    /**
     * Minimum percentage of stocks that must be advancing for strong market breadth.
     * Set to 60% to ensure broad market participation, not just a few stocks.
     */
    private const val MIN_MARKET_BREADTH_PERCENT = 60.0

    /**
     * Check if market is in a favorable regime for trading.
     *
     * @param quote The current stock quote (contains market data)
     * @return true if market conditions are favorable, false otherwise
     */
    fun isMarketRegimeFavorable(quote: StockQuote): Boolean {
        // Check 1: SPY above 200-day SMA for sustained period
        val sustainedAbove200 = isSPYSustainedAbove200SMA(quote)

        // Check 2: Golden cross maintained (50 EMA > 200 EMA)
        val goldenCrossMaintained = isGoldenCrossMaintained(quote)

        // Check 3: Strong market breadth
        val strongBreadth = isMarketBreadthStrong(quote)

        // Log the regime check results for debugging
        if (!sustainedAbove200 || !goldenCrossMaintained || !strongBreadth) {
            logger.debug(
                "Market regime unfavorable on ${quote.date}: " +
                "sustainedAbove200=$sustainedAbove200 (${quote.spyDaysAbove200SMA} days), " +
                "goldenCross=$goldenCrossMaintained (50EMA=${quote.spyEMA50}, 200EMA=${quote.spyEMA200}), " +
                "breadth=$strongBreadth (${quote.marketAdvancingPercent}%)"
            )
        }

        return sustainedAbove200 && goldenCrossMaintained && strongBreadth
    }

    /**
     * Check if SPY has been above 200-day SMA for a sustained period.
     * This filters out brief spikes that quickly reverse.
     */
    private fun isSPYSustainedAbove200SMA(quote: StockQuote): Boolean {
        return quote.spyDaysAbove200SMA >= MIN_DAYS_ABOVE_200_SMA
    }

    /**
     * Check if golden cross is maintained (50 EMA > 200 EMA).
     * This indicates the intermediate trend is above the long-term trend.
     */
    private fun isGoldenCrossMaintained(quote: StockQuote): Boolean {
        return quote.spyEMA50 > quote.spyEMA200
    }

    /**
     * Check if market breadth is strong (>60% of stocks advancing).
     * This indicates broad market participation, not just a few stocks.
     */
    private fun isMarketBreadthStrong(quote: StockQuote): Boolean {
        return quote.marketAdvancingPercent >= MIN_MARKET_BREADTH_PERCENT
    }

    /**
     * Get a detailed description of current market regime.
     * Useful for logging and debugging.
     */
    fun getMarketRegimeDescription(quote: StockQuote): String {
        val sb = StringBuilder()
        sb.append("Market Regime Analysis for ${quote.date}:\n")
        sb.append("  SPY Days Above 200 SMA: ${quote.spyDaysAbove200SMA} (need $MIN_DAYS_ABOVE_200_SMA)\n")
        sb.append("  SPY 50 EMA: ${quote.spyEMA50}\n")
        sb.append("  SPY 200 EMA: ${quote.spyEMA200}\n")
        sb.append("  Golden Cross: ${if (quote.spyEMA50 > quote.spyEMA200) "Yes" else "No"}\n")
        sb.append("  Market Breadth: ${quote.marketAdvancingPercent}% (need $MIN_MARKET_BREADTH_PERCENT%)\n")
        sb.append("  Overall: ${if (isMarketRegimeFavorable(quote)) "FAVORABLE" else "UNFAVORABLE"}\n")
        return sb.toString()
    }
}
