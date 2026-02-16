package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks for N consecutive higher closes inside the value zone.
 *
 * This condition looks for:
 * 1. N+1 consecutive days where each day's close is higher than or equal to the previous day
 * 2. The first (oldest) quote is the entry point into the value zone (not checked for VZ)
 * 3. The next N quotes must all be within the value zone (EMA to EMA + ATR multiplier)
 *
 * This pattern indicates consistent upward momentum while price remains in a healthy
 * pullback zone relative to the trend. Uses closing prices to match Pine Script behavior.
 *
 * @param consecutiveDays Number of consecutive higher closes required IN the value zone (default: 3)
 * @param atrMultiplier ATR multiplier for value zone upper bound (default: 2.0)
 * @param emaPeriod EMA period for value zone lower bound (default: 20)
 */
@Component
class ConsecutiveHigherHighsInValueZoneCondition(
  private val consecutiveDays: Int = 3,
  private val atrMultiplier: Double = 2.0,
  private val emaPeriod: Int = 20,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    // Get N+1 quotes: first is entry point, next N must be in value zone
    val quotes = mutableListOf(quote)
    var currentQuote = quote

    // Need N additional quotes (total N+1)
    for (i in 1..consecutiveDays) {
      val prevQuote = stock.getPreviousQuote(currentQuote) ?: return false
      quotes.add(prevQuote)
      currentQuote = prevQuote
    }

    // Check for consecutive higher closes (all quotes)
    for (i in 0 until quotes.size - 1) {
      if (quotes[i].closePrice < quotes[i + 1].closePrice) {
        return false
      }
    }

    // Check that all quotes EXCEPT the oldest (first entry) are in the value zone
    // quotes[0..N-1] must be in VZ, quotes[N] is just the entry point
    return quotes.dropLast(1).all { isInValueZone(it) }
  }

  /**
   * Check if a quote is within the value zone
   */
  private fun isInValueZone(quote: StockQuote): Boolean {
    val emaValue = getEmaValue(quote, emaPeriod)
    val upperBound = emaValue + (atrMultiplier * quote.atr)
    return quote.closePrice > emaValue && quote.closePrice < upperBound
  }

  /**
   * Helper function to get the EMA value for the specified period
   */
  private fun getEmaValue(
    quote: StockQuote,
    period: Int,
  ): Double =
    when (period) {
      5 -> quote.closePriceEMA5
      10 -> quote.closePriceEMA10
      20 -> quote.closePriceEMA20
      50 -> quote.closePriceEMA50
      100 -> quote.closePriceEMA100
      200 -> 0.0 // 200 EMA not yet implemented
      else -> quote.closePriceEMA20 // Default to 20 EMA
    }

  override fun description(): String =
    "$consecutiveDays consecutive higher closes in value zone " +
      "(${emaPeriod}EMA to ${emaPeriod}EMA + ${atrMultiplier}ATR)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "consecutiveHigherHighsInValueZone",
      displayName = "Consecutive Higher Closes in Value Zone",
      description = "N consecutive days with higher closes, all within value zone",
      parameters =
        listOf(
          ParameterMetadata(
            name = "consecutiveDays",
            displayName = "Consecutive Days",
            type = "number",
            defaultValue = 3,
            min = 2,
            max = 10,
            options = listOf("2", "3", "4", "5"),
          ),
          ParameterMetadata(
            name = "emaPeriod",
            displayName = "EMA Period",
            type = "number",
            defaultValue = 20,
            options = listOf("5", "10", "20", "50", "100"),
          ),
          ParameterMetadata(
            name = "atrMultiplier",
            displayName = "ATR Multiplier",
            type = "number",
            defaultValue = 2.0,
            min = 0.5,
            max = 5.0,
          ),
        ),
      category = "Price Action",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    // Get N+1 quotes: first is entry point, next N must be in value zone
    val quotes = mutableListOf(quote)
    var currentQuote = quote

    for (i in 1..consecutiveDays) {
      val prevQuote = stock.getPreviousQuote(currentQuote)
      if (prevQuote == null) {
        return ConditionEvaluationResult(
          conditionType = "ConsecutiveHigherHighsInValueZoneCondition",
          description = description(),
          passed = false,
          actualValue = null,
          threshold = null,
          message = "Insufficient historical data (need at least ${consecutiveDays + 1} days) ✗",
        )
      }
      quotes.add(prevQuote)
      currentQuote = prevQuote
    }

    // Check for consecutive higher closes (all quotes)
    var hasHigherCloses = true
    for (i in 0 until quotes.size - 1) {
      if (quotes[i].closePrice < quotes[i + 1].closePrice) {
        hasHigherCloses = false
        break
      }
    }

    // Check value zone for all quotes EXCEPT the oldest (first entry)
    val quotesToCheck = quotes.dropLast(1)
    val valueZoneChecks = quotesToCheck.map { isInValueZone(it) }
    val allInValueZone = valueZoneChecks.all { it }
    val passed = hasHigherCloses && allInValueZone

    // Build detailed message
    val ema = getEmaValue(quote, emaPeriod)
    val upperBound = ema + (atrMultiplier * quote.atr)

    val closesString = quotes.joinToString(" >= ") { "%.2f".format(it.closePrice) }
    // Add indicator for oldest quote (entry point, not checked for VZ)
    val valueZoneString = valueZoneChecks.joinToString(", ") { if (it) "✓" else "✗" } + " (oldest: entry point)"

    val message =
      buildString {
        append("Closes: $closesString ")
        if (hasHigherCloses) {
          append("✓")
        } else {
          append("✗")
        }
        append(" | ")
        append("Value Zone (${"%.2f".format(ema)} - ${"%.2f".format(upperBound)}): ")
        append("[$valueZoneString]")
        if (allInValueZone) {
          append(" ✓")
        } else {
          append(" ✗")
        }
      }

    return ConditionEvaluationResult(
      conditionType = "ConsecutiveHigherHighsInValueZoneCondition",
      description = description(),
      passed = passed,
      actualValue = closesString,
      threshold = "Value zone: ${"%.2f".format(ema)} - ${"%.2f".format(upperBound)}",
      message = message,
    )
  }
}
