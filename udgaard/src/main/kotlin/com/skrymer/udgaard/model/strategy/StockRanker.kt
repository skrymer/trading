package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Ranks stocks when multiple entry signals occur on the same day.
 * Used to pick the top N stocks when position limits apply.
 */
interface StockRanker {
    /**
     * Calculate a score for a stock at entry. Higher score = better.
     * @param stock - the stock
     * @param entryQuote - the quote where entry signal triggered
     * @return score (higher is better)
     */
    fun score(stock: Stock, entryQuote: StockQuote): Double

    /**
     * Description of this ranking strategy
     */
    fun description(): String
}

/**
 * Ranks stocks by heatmap value (lower = better, more fearful = better entry)
 * Theory: Buy fear, sell greed. Lower heatmap = better entry point.
 */
class HeatmapRanker : StockRanker {
    override fun score(stock: Stock, entryQuote: StockQuote): Double {
        // Lower heatmap is better, so return negative (will sort descending)
        // Stock with heatmap 10 scores 90, stock with heatmap 60 scores 40
        return 100.0 - entryQuote.heatmap
    }

    override fun description() = "Heatmap ranking (lower heatmap = better)"
}

/**
 * Ranks stocks by relative strength vs sector.
 * Theory: Buy the strongest stocks in strong sectors.
 */
class RelativeStrengthRanker : StockRanker {
    override fun score(stock: Stock, entryQuote: StockQuote): Double {
        // Compare stock heatmap to sector heatmap
        // Higher stock heatmap relative to sector = stronger stock
        val relativeStrength = entryQuote.heatmap - entryQuote.sectorHeatmap
        return relativeStrength
    }

    override fun description() = "Relative strength (stock heatmap - sector heatmap)"
}

/**
 * Ranks stocks by ATR as percentage of price (volatility).
 * Theory: Higher volatility = larger potential moves.
 */
class VolatilityRanker : StockRanker {
    override fun score(stock: Stock, entryQuote: StockQuote): Double {
        if (entryQuote.closePrice == 0.0) return 0.0
        // ATR as percentage of price
        return (entryQuote.atr / entryQuote.closePrice) * 100.0
    }

    override fun description() = "ATR as % of price (higher volatility = better)"
}

/**
 * Ranks stocks by distance from 10 EMA.
 * Theory: Closer to 10 EMA = better entry (less extended).
 */
class DistanceFrom10EmaRanker : StockRanker {
    override fun score(stock: Stock, entryQuote: StockQuote): Double {
        if (entryQuote.closePriceEMA10 == 0.0) return 0.0
        // Distance as percentage
        val distance = ((entryQuote.closePrice - entryQuote.closePriceEMA10) / entryQuote.closePriceEMA10) * 100.0
        // Closer to EMA is better, so return negative distance
        return -Math.abs(distance)
    }

    override fun description() = "Distance from 10 EMA (closer = better)"
}

/**
 * Composite ranker that combines multiple ranking factors.
 * Default: Heatmap (40%) + Relative Strength (30%) + Volatility (30%)
 */
class CompositeRanker(
    private val heatmapWeight: Double = 0.4,
    private val relativeStrengthWeight: Double = 0.3,
    private val volatilityWeight: Double = 0.3
) : StockRanker {

    private val heatmapRanker = HeatmapRanker()
    private val rsRanker = RelativeStrengthRanker()
    private val volatilityRanker = VolatilityRanker()

    override fun score(stock: Stock, entryQuote: StockQuote): Double {
        val heatmapScore = heatmapRanker.score(stock, entryQuote)
        val rsScore = rsRanker.score(stock, entryQuote)
        val volatilityScore = volatilityRanker.score(stock, entryQuote)

        // Normalize scores to 0-100 range
        val normalizedHeatmap = normalize(heatmapScore, 0.0, 100.0)
        val normalizedRS = normalize(rsScore, -50.0, 50.0)
        val normalizedVolatility = normalize(volatilityScore, 0.0, 10.0)

        return (normalizedHeatmap * heatmapWeight) +
               (normalizedRS * relativeStrengthWeight) +
               (normalizedVolatility * volatilityWeight)
    }

    private fun normalize(value: Double, min: Double, max: Double): Double {
        if (max == min) return 50.0
        return ((value - min) / (max - min)) * 100.0
    }

    override fun description() = "Composite (Heatmap ${heatmapWeight*100}%, RS ${relativeStrengthWeight*100}%, Vol ${volatilityWeight*100}%)"
}

/**
 * Ranks stocks by sector strength.
 * Theory: Trade stocks in the strongest sectors.
 */
class SectorStrengthRanker : StockRanker {
    override fun score(stock: Stock, entryQuote: StockQuote): Double {
        // Sector heatmap + sector bull percentage
        return entryQuote.sectorHeatmap + entryQuote.sectorBullPercentage
    }

    override fun description() = "Sector strength (sector heatmap + bull %)"
}

/**
 * Simple random ranker for baseline comparison.
 */
class RandomRanker : StockRanker {
    override fun score(stock: Stock, entryQuote: StockQuote): Double {
        return Math.random() * 100.0
    }

    override fun description() = "Random (baseline)"
}

/**
 * Adaptive ranker that switches between strategies based on market conditions.
 * Theory: Use Volatility ranker in trending markets, DistanceFrom10Ema in choppy markets.
 *
 * Market Regime Detection:
 * - Trending: SPY above 200 SMA for 20+ days AND 50 EMA > 200 EMA
 * - Choppy: Otherwise
 */
class AdaptiveRanker : StockRanker {
    private val volatilityRanker = VolatilityRanker()
    private val distanceRanker = DistanceFrom10EmaRanker()

    override fun score(stock: Stock, entryQuote: StockQuote): Double {
        return if (isMarketTrending(entryQuote)) {
            // Use Volatility ranker in trending markets (favors big movers)
            volatilityRanker.score(stock, entryQuote)
        } else {
            // Use DistanceFrom10Ema ranker in choppy markets (favors pullbacks)
            distanceRanker.score(stock, entryQuote)
        }
    }

    private fun isMarketTrending(quote: StockQuote): Boolean {
        // Market is trending if:
        // 1. SPY sustained above 200 SMA for at least 20 days
        // 2. Golden cross maintained (50 EMA > 200 EMA)
        val sustainedAbove200 = quote.spyDaysAbove200SMA >= 20
        val goldenCross = quote.spyEMA50 > quote.spyEMA200

        return sustainedAbove200 && goldenCross
    }

    override fun description() = "Adaptive (Volatility in trends, DistanceFrom10Ema in chop)"
}
