package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

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
  fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double

  fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double = score(stock, entryQuote)

  /**
   * Description of this ranking strategy
   */
  fun description(): String
}

/**
 * Ranks stocks by ATR as percentage of price (volatility).
 * Theory: Higher volatility = larger potential moves.
 */
class VolatilityRanker : StockRanker {
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double {
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
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double {
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
 * Default: Volatility (40%) + DistanceFrom10Ema (30%) + SectorStrength (30%)
 */
class CompositeRanker(
  private val volatilityWeight: Double = 0.4,
  private val distanceWeight: Double = 0.3,
  private val sectorWeight: Double = 0.3,
) : StockRanker {
  private val volatilityRanker = VolatilityRanker()
  private val distanceRanker = DistanceFrom10EmaRanker()
  private val sectorRanker = SectorStrengthRanker()

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double {
    val volatilityScore = volatilityRanker.score(stock, entryQuote, context)
    val distanceScore = distanceRanker.score(stock, entryQuote, context)
    val sectorScore = sectorRanker.score(stock, entryQuote, context)

    // Normalize scores to 0-100 range
    val normalizedVolatility = normalize(volatilityScore, 0.0, 10.0)
    val normalizedDistance = normalize(distanceScore, -10.0, 0.0)
    val normalizedSector = normalize(sectorScore, 0.0, 100.0)

    return (normalizedVolatility * volatilityWeight) +
      (normalizedDistance * distanceWeight) +
      (normalizedSector * sectorWeight)
  }

  private fun normalize(
    value: Double,
    min: Double,
    max: Double,
  ): Double {
    if (max == min) return 50.0
    return ((value - min) / (max - min)) * 100.0
  }

  override fun description() =
    "Composite (Vol ${volatilityWeight * 100}%, Dist10EMA ${distanceWeight * 100}%, Sector ${sectorWeight * 100}%)"
}

/**
 * Ranks stocks by sector strength (bull percentage from context).
 * Theory: Trade stocks in the strongest sectors.
 */
class SectorStrengthRanker : StockRanker {
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double = context.getSectorBreadth(stock.sectorSymbol, entryQuote.date)?.bullPercentage ?: 0.0

  override fun description() = "Sector strength (bull %)"
}

/**
 * Simple random ranker for baseline comparison.
 */
class RandomRanker : StockRanker {
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = Math.random() * 100.0

  override fun description() = "Random (baseline)"
}

/**
 * Adaptive ranker that switches between strategies based on market conditions.
 * Theory: Use Volatility ranker in trending markets, DistanceFrom10Ema in choppy markets.
 *
 * Market Regime Detection:
 * - Trending: Market breadth above 60% (majority of stocks in uptrend)
 * - Choppy: Otherwise
 */
class AdaptiveRanker : StockRanker {
  private val volatilityRanker = VolatilityRanker()
  private val distanceRanker = DistanceFrom10EmaRanker()

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double =
    if (isMarketTrending(entryQuote, context)) {
      volatilityRanker.score(stock, entryQuote)
    } else {
      distanceRanker.score(stock, entryQuote)
    }

  private fun isMarketTrending(
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val marketBreadth = context.getMarketBreadth(quote.date)
    return marketBreadth != null && marketBreadth.breadthPercent > 60.0
  }

  override fun description() = "Adaptive (Volatility in trends, DistanceFrom10Ema in chop)"
}
