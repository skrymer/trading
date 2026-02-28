package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if recent price action is contracted relative to ATR.
 *
 * Measures how tight the price range over a lookback period is compared to the stock's ATR.
 * When (maxHigh - minLow) / ATR <= maxAtrMultiple, volatility is contracted — the VCP "squeeze".
 *
 * This is a key component of Mark Minervini's Volatility Contraction Pattern (VCP),
 * identifying stocks that have consolidated with decreasing volatility before a breakout.
 *
 * @param lookbackDays Number of recent trading days to measure the price range (default: 10)
 * @param maxAtrMultiple Maximum allowed range as ATR multiple — lower = tighter contraction (default: 2.5)
 */
@Component
class VolatilityContractedCondition(
  private val lookbackDays: Int = 10,
  private val maxAtrMultiple: Double = 2.5,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val atr = quote.atr
    if (atr <= 0.0) return false

    val recentQuotes = getRecentQuotes(stock, quote)
    if (recentQuotes.size < lookbackDays) return false

    val maxHigh = recentQuotes.maxOf { it.high }
    val minLow = recentQuotes.minOf { it.low }
    val rangeAtrMultiple = (maxHigh - minLow) / atr

    return rangeAtrMultiple <= maxAtrMultiple
  }

  override fun description(): String =
    "Volatility contracted (range ≤ ${"%.1f".format(maxAtrMultiple)}× ATR over $lookbackDays days)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "volatilityContracted",
      displayName = "Volatility Contracted",
      description = "Price range over lookback period is small relative to ATR (VCP squeeze)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "lookbackDays",
            displayName = "Lookback Days",
            type = "number",
            defaultValue = 10,
            min = 3,
            max = 60,
          ),
          ParameterMetadata(
            name = "maxAtrMultiple",
            displayName = "Max ATR Multiple",
            type = "number",
            defaultValue = 2.5,
            min = 0.5,
            max = 10.0,
          ),
        ),
      category = "Volatility",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val atr = quote.atr
    if (atr <= 0.0) {
      return failedResult("ATR not available")
    }

    val recentQuotes = getRecentQuotes(stock, quote)
    if (recentQuotes.size < lookbackDays) {
      return failedResult("Insufficient data (${recentQuotes.size}/$lookbackDays days)")
    }

    val maxHigh = recentQuotes.maxOf { it.high }
    val minLow = recentQuotes.minOf { it.low }
    val rangeAtrMultiple = (maxHigh - minLow) / atr
    val passed = rangeAtrMultiple <= maxAtrMultiple

    val message =
      if (passed) {
        "Range ${"%.2f".format(maxHigh - minLow)} = ${"%.1f".format(rangeAtrMultiple)}× ATR (≤ ${"%.1f".format(maxAtrMultiple)}×) ✓"
      } else {
        "Range ${"%.2f".format(maxHigh - minLow)} = ${"%.1f".format(rangeAtrMultiple)}× ATR (needs ≤ ${"%.1f".format(maxAtrMultiple)}×) ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "VolatilityContractedCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.1f".format(rangeAtrMultiple)}×",
      threshold = "≤ ${"%.1f".format(maxAtrMultiple)}×",
      message = message,
    )
  }

  /**
   * Get the most recent N quotes up to and including the current quote.
   */
  private fun getRecentQuotes(
    stock: Stock,
    currentQuote: StockQuote,
  ): List<StockQuote> =
    stock.quotes
      .filter { it.date <= currentQuote.date }
      .sortedByDescending { it.date }
      .take(lookbackDays)

  private fun failedResult(reason: String): ConditionEvaluationResult =
    ConditionEvaluationResult(
      conditionType = "VolatilityContractedCondition",
      description = description(),
      passed = false,
      actualValue = null,
      threshold = null,
      message = "$reason ✗",
    )
}
