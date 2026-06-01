package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.data.model.StockQuote

/**
 * Periods for which a pre-computed moving average is persisted, keyed by MA type.
 * The single source of truth for what [movingAverage] can resolve — conditions validate
 * their configured periods against this so an unsupported period fails loudly at
 * construction rather than silently failing every evaluation.
 */
internal fun supportedMaPeriods(maType: String): Set<Int> =
  when (maType.uppercase()) {
    "SMA" -> setOf(50, 150, 200)
    "EMA" -> setOf(5, 10, 20, 50, 100, 200)
    else -> emptySet()
  }

/**
 * Reads a pre-computed moving average off a quote by type and period.
 *
 * Shared by the moving-average trend conditions so the "unavailable means insufficient
 * history" rule stays identical across them: SMA fields are null until their window fills,
 * and EMA fields sit at their 0.0 default until computed — both map to `null` here, which
 * the conditions treat as a fail. Values are read, never recomputed.
 *
 * @return the MA value, or null when the type/period is unsupported or the field is unavailable.
 */
internal fun StockQuote.movingAverage(
  maType: String,
  period: Int,
): Double? =
  when (maType.uppercase()) {
    "SMA" ->
      when (period) {
        50 -> sma50
        150 -> sma150
        200 -> sma200
        else -> null
      }
    "EMA" ->
      when (period) {
        5 -> closePriceEMA5
        10 -> closePriceEMA10
        20 -> closePriceEMA20
        50 -> closePriceEMA50
        100 -> closePriceEMA100
        200 -> ema200
        else -> null
      }?.takeIf { it != 0.0 }
    else -> null
  }
