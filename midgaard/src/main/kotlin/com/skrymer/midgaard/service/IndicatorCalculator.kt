package com.skrymer.midgaard.service

import com.skrymer.midgaard.model.RawBar
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Computes technical indicators for OHLCV data.
 *
 * Used in two modes:
 * 1. Full calculation — compute all indicators from scratch (initial ingest from OHLCV)
 * 2. Extension — extend indicators from existing baseline (daily updates)
 *
 * Algorithms match Wilder's smoothing (ATR/ADX) and standard EMA formulas.
 */
@Service
class IndicatorCalculator {
    /**
     * Calculate all EMAs from scratch for a list of bars.
     * Returns Map of period -> List of EMA values (same length as bars).
     */
    fun calculateAllEMAs(bars: List<RawBar>): Map<Int, List<Double>> {
        val closePrices = bars.map { it.close }
        return EMA_PERIODS.associateWith { period -> calculateEMA(closePrices, period) }
    }

    /**
     * Calculate EMA for a series of prices.
     * First EMA = SMA of first 'period' prices (bootstrap).
     * EMA = (Close - PreviousEMA) * Multiplier + PreviousEMA
     */
    fun calculateEMA(
        prices: List<Double>,
        period: Int,
    ): List<Double> {
        if (prices.size < period) {
            return List(prices.size) { 0.0 }
        }

        val multiplier = 2.0 / (period + 1)
        val emaValues = mutableListOf<Double>()

        var ema = prices.take(period).average()
        repeat(period - 1) { emaValues.add(0.0) }
        emaValues.add(ema)

        for (i in period until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
            emaValues.add(ema)
        }

        return emaValues
    }

    /**
     * Extend EMAs for new bars, seeded from the last known EMA values.
     * Returns Map of period -> List of new EMA values (one per new bar).
     */
    fun extendEMAs(
        lastEmas: Map<Int, Double>,
        newBars: List<RawBar>,
    ): Map<Int, List<Double>> =
        EMA_PERIODS.associateWith { period ->
            val seedEma = lastEmas[period] ?: 0.0
            val multiplier = 2.0 / (period + 1)
            var ema = seedEma

            newBars.map { bar ->
                ema = (bar.close - ema) * multiplier + ema
                ema
            }
        }

    /**
     * Calculate ATR from scratch using Wilder's smoothing.
     * Returns list of ATR values (same length as bars, 0.0 for initial period).
     */
    fun calculateATR(
        bars: List<RawBar>,
        period: Int = ATR_PERIOD,
    ): List<Double> {
        if (bars.size < period + 1) {
            return List(bars.size) { 0.0 }
        }

        val trueRanges = mutableListOf(0.0)
        for (i in 1 until bars.size) {
            val tr =
                maxOf(
                    bars[i].high - bars[i].low,
                    Math.abs(bars[i].high - bars[i - 1].close),
                    Math.abs(bars[i].low - bars[i - 1].close),
                )
            trueRanges.add(tr)
        }

        val atrValues = MutableList(bars.size) { 0.0 }
        val firstAtr = trueRanges.subList(1, period + 1).average()
        atrValues[period] = firstAtr

        var atr = firstAtr
        for (i in period + 1 until bars.size) {
            atr = ((atr * (period - 1)) + trueRanges[i]) / period
            atrValues[i] = atr
        }

        return atrValues
    }

    /**
     * Extend ATR for new bars, seeded from last known ATR and previous close.
     * Returns list of ATR values (one per new bar).
     */
    fun extendATR(
        lastAtr: Double,
        previousClose: Double,
        newBars: List<RawBar>,
        period: Int = ATR_PERIOD,
    ): List<Double> {
        var atr = lastAtr
        var prevClose = previousClose

        return newBars.map { bar ->
            val tr =
                maxOf(
                    bar.high - bar.low,
                    Math.abs(bar.high - prevClose),
                    Math.abs(bar.low - prevClose),
                )
            atr = ((atr * (period - 1)) + tr) / period
            prevClose = bar.close
            atr
        }
    }

    /**
     * Calculate ADX from scratch using Wilder's smoothing.
     * Returns list of ADX values (same length as bars, 0.0 where insufficient data).
     */
    fun calculateADX(
        bars: List<RawBar>,
        period: Int = ADX_PERIOD,
    ): List<Double> {
        val minRequired = 2 * period + 1
        if (bars.size < minRequired) return List(bars.size) { 0.0 }
        val dxValues = computeDXValues(bars, period)
        return smoothDXToADX(dxValues, bars.size, period)
    }

    private fun computeDirectionalMovements(bars: List<RawBar>): Triple<List<Double>, List<Double>, List<Double>> {
        val plusDM = mutableListOf(0.0)
        val minusDM = mutableListOf(0.0)
        val trueRanges = mutableListOf(0.0)
        for (i in 1 until bars.size) {
            val highDiff = bars[i].high - bars[i - 1].high
            val lowDiff = bars[i - 1].low - bars[i].low
            plusDM.add(if (highDiff > lowDiff && highDiff > 0) highDiff else 0.0)
            minusDM.add(if (lowDiff > highDiff && lowDiff > 0) lowDiff else 0.0)
            val tr =
                maxOf(
                    bars[i].high - bars[i].low,
                    Math.abs(bars[i].high - bars[i - 1].close),
                    Math.abs(bars[i].low - bars[i - 1].close),
                )
            trueRanges.add(tr)
        }
        return Triple(plusDM, minusDM, trueRanges)
    }

    private fun computeDXValues(
        bars: List<RawBar>,
        period: Int,
    ): MutableList<Double> {
        val (plusDM, minusDM, trueRanges) = computeDirectionalMovements(bars)
        var smoothedTR = trueRanges.subList(1, period + 1).sum()
        var smoothedPlusDM = plusDM.subList(1, period + 1).sum()
        var smoothedMinusDM = minusDM.subList(1, period + 1).sum()
        val dxValues = MutableList(bars.size) { 0.0 }
        dxValues[period] = computeDX(smoothedPlusDM, smoothedMinusDM, smoothedTR)
        for (i in period + 1 until bars.size) {
            smoothedTR = smoothedTR - (smoothedTR / period) + trueRanges[i]
            smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / period) + plusDM[i]
            smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / period) + minusDM[i]
            dxValues[i] = computeDX(smoothedPlusDM, smoothedMinusDM, smoothedTR)
        }
        return dxValues
    }

    private fun computeDX(
        smoothedPlusDM: Double,
        smoothedMinusDM: Double,
        smoothedTR: Double,
    ): Double {
        val pdi = if (smoothedTR > 0) 100.0 * smoothedPlusDM / smoothedTR else 0.0
        val mdi = if (smoothedTR > 0) 100.0 * smoothedMinusDM / smoothedTR else 0.0
        val sum = pdi + mdi
        return if (sum > 0) 100.0 * Math.abs(pdi - mdi) / sum else 0.0
    }

    private fun smoothDXToADX(
        dxValues: List<Double>,
        size: Int,
        period: Int,
    ): List<Double> {
        val adxValues = MutableList(size) { 0.0 }
        val firstADX = dxValues.subList(period, 2 * period).average()
        adxValues[2 * period - 1] = firstADX
        var adx = firstADX
        for (i in 2 * period until size) {
            adx = ((adx * (period - 1)) + dxValues[i]) / period
            adxValues[i] = adx
        }
        return adxValues
    }

    /**
     * Extend ADX for new bars, seeded from last known smoothed state.
     *
     * Requires the last N+1 bars (existing quotes) to reconstruct smoothing state,
     * then continues the calculation for new bars.
     * For simplicity, we recompute ADX from the tail of existing + new bars.
     */
    fun extendADX(
        existingBars: List<RawBar>,
        newBars: List<RawBar>,
        period: Int = ADX_PERIOD,
    ): List<Double> {
        val allBars = existingBars + newBars
        val allAdx = calculateADX(allBars, period)
        return allAdx.takeLast(newBars.size)
    }

    /**
     * Calculate Donchian upper band (highest high over lookback) from scratch.
     * Returns list same length as bars.
     */
    fun calculateDonchianUpper(
        bars: List<RawBar>,
        periods: Int = DONCHIAN_PERIOD,
    ): List<Double> =
        bars.indices.map { i ->
            val start = maxOf(0, i - periods + 1)
            bars.subList(start, i + 1).maxOf { it.high }
        }

    /**
     * Extend Donchian upper band for new bars.
     * Needs last (periods-1) existing bars for lookback window.
     */
    fun extendDonchianUpper(
        recentBars: List<RawBar>,
        newBars: List<RawBar>,
        periods: Int = DONCHIAN_PERIOD,
    ): List<Double> {
        val combined = recentBars + newBars
        val startIdx = recentBars.size
        return (startIdx until combined.size).map { i ->
            val windowStart = maxOf(0, i - periods + 1)
            combined.subList(windowStart, i + 1).maxOf { it.high }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IndicatorCalculator::class.java)
        val EMA_PERIODS = listOf(5, 10, 20, 50, 100, 200)
        const val ATR_PERIOD = 14
        const val ADX_PERIOD = 14
        const val DONCHIAN_PERIOD = 5

        fun Double.toBigDecimal4(): BigDecimal = BigDecimal.valueOf(this).setScale(4, RoundingMode.HALF_UP)
    }
}
