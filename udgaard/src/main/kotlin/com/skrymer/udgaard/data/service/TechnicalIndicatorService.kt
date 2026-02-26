package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.StockQuote
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
 * - ATR (Average True Range) - Wilder's smoothing method
 * - ADX (Average Directional Index) - trend strength via Wilder's method
 * - Donchian Channels - support/resistance levels
 * - Trend determination - uptrend/downtrend classification
 */
@Service
class TechnicalIndicatorService {
  /**
   * Enrich stock quotes with all technical indicators.
   *
   * This is the main method that calculates and adds:
   * - EMAs (5, 10, 20, 50, 100, 200)
   * - ATR (14-period, Wilder's smoothing)
   * - ADX (14-period, Wilder's smoothing)
   * - Donchian upper band
   * - Trend classification
   *
   * @param quotes - List of StockQuote sorted by date (oldest first)
   * @param symbol - Stock symbol for logging
   * @return Enriched list of StockQuote with indicators populated
   */
  fun enrichWithIndicators(
    quotes: List<StockQuote>,
    symbol: String,
  ): List<StockQuote> {
    if (quotes.isEmpty()) {
      logger.warn("No quotes provided for $symbol, skipping indicator calculation")
      return quotes
    }

    // Indicators are pre-populated from Midgaard — only compute trend
    logger.info("Computing trend for $symbol (${quotes.size} quotes)")
    return quotes.map { quote ->
      quote.apply {
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
  fun calculateEMA(
    prices: List<Double>,
    period: Int,
  ): List<Double> {
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
   * Calculate ATR (Average True Range) using Wilder's smoothing method.
   *
   * Formula:
   * - True Range = max(high - low, |high - prevClose|, |low - prevClose|)
   * - First ATR = SMA of first 'period' True Range values
   * - Subsequent ATR = ((prevATR × (period - 1)) + TR) / period
   *
   * Wilder's smoothing differs from standard EMA: it uses multiplier 1/N instead of 2/(N+1).
   *
   * @param quotes - List of StockQuote sorted by date (oldest first)
   * @param period - ATR period (default 14)
   * @return List of ATR values (same length as quotes, first value is 0.0 since no previous close)
   */
  fun calculateATR(
    quotes: List<StockQuote>,
    period: Int = 14,
  ): List<Double> {
    if (quotes.size < period + 1) {
      logger.warn("Not enough data points (${quotes.size}) for ATR calculation (need ${period + 1})")
      return List(quotes.size) { 0.0 }
    }

    // Calculate True Range for each quote (skip first since we need previous close)
    val trueRanges = mutableListOf(0.0) // First quote has no previous close
    for (i in 1 until quotes.size) {
      val high = quotes[i].high
      val low = quotes[i].low
      val prevClose = quotes[i - 1].closePrice
      val tr = maxOf(high - low, Math.abs(high - prevClose), Math.abs(low - prevClose))
      trueRanges.add(tr)
    }

    // Calculate ATR using Wilder's smoothing
    val atrValues = MutableList(quotes.size) { 0.0 }

    // First ATR = SMA of first 'period' True Range values (indices 1..period)
    val firstAtr = trueRanges.subList(1, period + 1).average()
    atrValues[period] = firstAtr

    // Subsequent ATR = ((prevATR × (period - 1)) + TR) / period
    var atr = firstAtr
    for (i in period + 1 until quotes.size) {
      atr = ((atr * (period - 1)) + trueRanges[i]) / period
      atrValues[i] = atr
    }

    return atrValues
  }

  /**
   * Calculate ADX (Average Directional Index) using Wilder's smoothing method.
   *
   * ADX measures trend strength (not direction). Values:
   * - 0-20: Weak/absent trend
   * - 20-50: Strong trend
   * - 50+: Very strong trend
   *
   * Calculation steps:
   * 1. Calculate +DM (positive directional movement) and -DM (negative directional movement)
   * 2. Calculate True Range (TR)
   * 3. Smooth TR, +DM, -DM using Wilder's method over 'period' bars
   * 4. +DI = 100 × smoothed(+DM) / smoothed(TR)
   * 5. -DI = 100 × smoothed(-DM) / smoothed(TR)
   * 6. DX = 100 × |+DI - -DI| / (+DI + -DI)
   * 7. ADX = Wilder-smoothed DX over 'period' bars
   *
   * First meaningful ADX appears at index 2×period (needs period bars to smooth DI,
   * then period more bars to smooth DX into ADX).
   *
   * @param quotes - List of StockQuote sorted by date (oldest first)
   * @param period - ADX period (default 14)
   * @return List of ADX values (same length as quotes, 0.0 where insufficient data)
   */
  fun calculateADX(
    quotes: List<StockQuote>,
    period: Int = 14,
  ): List<Double> {
    val minRequired = 2 * period + 1
    if (quotes.size < minRequired) {
      logger.warn("Not enough data points (${quotes.size}) for ADX calculation (need $minRequired)")
      return List(quotes.size) { 0.0 }
    }

    // Step 1: Calculate +DM, -DM, and TR for each bar (starting from index 1)
    val plusDM = mutableListOf(0.0)
    val minusDM = mutableListOf(0.0)
    val trueRanges = mutableListOf(0.0)

    for (i in 1 until quotes.size) {
      val highDiff = quotes[i].high - quotes[i - 1].high
      val lowDiff = quotes[i - 1].low - quotes[i].low

      // +DM: upward movement is larger and positive
      val pdm = if (highDiff > lowDiff && highDiff > 0) highDiff else 0.0
      // -DM: downward movement is larger and positive
      val mdm = if (lowDiff > highDiff && lowDiff > 0) lowDiff else 0.0

      plusDM.add(pdm)
      minusDM.add(mdm)

      val tr = maxOf(
        quotes[i].high - quotes[i].low,
        Math.abs(quotes[i].high - quotes[i - 1].closePrice),
        Math.abs(quotes[i].low - quotes[i - 1].closePrice),
      )
      trueRanges.add(tr)
    }

    // Step 2: Wilder-smooth TR, +DM, -DM (first value = sum of first 'period' values)
    // First smoothed values at index 'period' (using indices 1..period)
    var smoothedTR = trueRanges.subList(1, period + 1).sum()
    var smoothedPlusDM = plusDM.subList(1, period + 1).sum()
    var smoothedMinusDM = minusDM.subList(1, period + 1).sum()

    // Step 3: Calculate DI and DX starting from index 'period'
    val dxValues = MutableList(quotes.size) { 0.0 }

    // First DI/DX at index 'period'
    val firstPlusDI = if (smoothedTR > 0) 100.0 * smoothedPlusDM / smoothedTR else 0.0
    val firstMinusDI = if (smoothedTR > 0) 100.0 * smoothedMinusDM / smoothedTR else 0.0
    val diSum = firstPlusDI + firstMinusDI
    dxValues[period] = if (diSum > 0) 100.0 * Math.abs(firstPlusDI - firstMinusDI) / diSum else 0.0

    // Continue smoothing and calculating DX for subsequent bars
    for (i in period + 1 until quotes.size) {
      // Wilder's smoothing: smoothed = prev - (prev / period) + current
      smoothedTR = smoothedTR - (smoothedTR / period) + trueRanges[i]
      smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / period) + plusDM[i]
      smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / period) + minusDM[i]

      val pdi = if (smoothedTR > 0) 100.0 * smoothedPlusDM / smoothedTR else 0.0
      val mdi = if (smoothedTR > 0) 100.0 * smoothedMinusDM / smoothedTR else 0.0
      val sum = pdi + mdi
      dxValues[i] = if (sum > 0) 100.0 * Math.abs(pdi - mdi) / sum else 0.0
    }

    // Step 4: Calculate ADX by Wilder-smoothing the DX values
    val adxValues = MutableList(quotes.size) { 0.0 }

    // First ADX = average of DX values from index 'period' to '2*period - 1'
    val firstADX = dxValues.subList(period, 2 * period).average()
    adxValues[2 * period - 1] = firstADX

    // Subsequent ADX = ((prevADX × (period - 1)) + DX) / period
    var adx = firstADX
    for (i in 2 * period until quotes.size) {
      adx = ((adx * (period - 1)) + dxValues[i]) / period
      adxValues[i] = adx
    }

    return adxValues
  }

  /**
   * Calculate Donchian Upper Band (highest high over lookback period).
   *
   * @param quotes - All quotes for the stock
   * @param currentIndex - Index of current quote
   * @param periods - Lookback period (default 5)
   * @return Donchian upper band value
   */
  fun calculateDonchianUpperBand(
    quotes: List<StockQuote>,
    currentIndex: Int,
    periods: Int = 5,
  ): Double {
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
  fun calculateDonchianLowerBand(
    quotes: List<StockQuote>,
    currentIndex: Int,
    periods: Int = 5,
  ): Double {
    if (currentIndex < periods) {
      val window = quotes.subList(0, currentIndex + 1)
      return window.minOfOrNull { it.low } ?: 0.0
    }

    val window = quotes.subList(currentIndex - periods + 1, currentIndex + 1)
    return window.minOfOrNull { it.low } ?: 0.0
  }

  /**
   * Calculate Donchian bands (upper and lower) for a generic series of values.
   * Upper = highest value over lookback, Lower = lowest value over lookback.
   *
   * @param values - List of values (oldest first)
   * @param periods - Lookback period (default 20)
   * @return Pair of lists: (upperBands, lowerBands), same length as input
   */
  fun calculateDonchianBands(
    values: List<Double>,
    periods: Int = 20,
  ): Pair<List<Double>, List<Double>> {
    val upper = mutableListOf<Double>()
    val lower = mutableListOf<Double>()

    for (i in values.indices) {
      val start = maxOf(0, i - periods + 1)
      val window = values.subList(start, i + 1)
      upper.add(window.max())
      lower.add(window.min())
    }

    return upper to lower
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
    val emaAligned = (quote.closePriceEMA5 > quote.closePriceEMA10)
      .and(quote.closePriceEMA10 > quote.closePriceEMA20)

    val aboveEma50 = quote.closePrice > quote.closePriceEMA50

    return if (emaAligned && aboveEma50) "Uptrend" else "Downtrend"
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(TechnicalIndicatorService::class.java)
  }
}
