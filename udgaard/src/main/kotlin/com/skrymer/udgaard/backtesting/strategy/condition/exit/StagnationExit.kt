package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when price has not progressed beyond a threshold
 * after a specified number of trading days since entry.
 *
 * This exits stagnant trades that tie up capital without making progress,
 * freeing it for deployment into fresh breakout signals.
 *
 * Only fires once: exactly when the window elapses and price is below the threshold.
 * Does not fire before the window (too early) or after (let EMA cross handle it).
 *
 * @param thresholdPercent Minimum % gain from entry required to stay in the trade (default 3.0)
 * @param windowDays Number of trading days after entry before checking (default 15)
 */
@Component
class StagnationExit(
  private val thresholdPercent: Double = 3.0,
  private val windowDays: Int = 15,
) : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean {
    if (entryQuote == null) return false

    val tradingDaysSinceEntry = stock.countTradingDaysBetween(entryQuote.date, quote.date)
    if (tradingDaysSinceEntry != windowDays) return false

    val gainPercent = ((quote.closePrice - entryQuote.closePrice) / entryQuote.closePrice) * 100.0
    return gainPercent < thresholdPercent
  }

  override fun exitReason(): String =
    "Price stagnation (less than $thresholdPercent% gain after $windowDays trading days)"

  override fun description(): String = "Stagnation ($thresholdPercent% / ${windowDays}d)"

  override fun proximity(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ExitProximity? {
    if (entryQuote == null || windowDays <= 0) return null

    val tradingDaysSinceEntry = stock.countTradingDaysBetween(entryQuote.date, quote.date)
    val gainPercent = ((quote.closePrice - entryQuote.closePrice) / entryQuote.closePrice) * 100.0
    val barFactor = (tradingDaysSinceEntry.toDouble() / windowDays).coerceIn(0.0, 1.0)
    // Stagnation fires on a discrete threshold — the gain is either below the cutoff
    // (trigger) or above it (no trigger). Treat the gain-side as a binary gate so a trade
    // below threshold on the penultimate bar surfaces as "imminent" regardless of how
    // close the gain sits to the cutoff. A graduated factor here would silently mute
    // warnings for trades that will definitely fire tomorrow.
    val belowThreshold = if (gainPercent < thresholdPercent) 1.0 else 0.0
    val prox = (barFactor * belowThreshold).coerceIn(0.0, 1.0)
    return ExitProximity(
      conditionType = "stagnation",
      proximity = prox,
      detail = "day %d of %d, gain %.2f%% vs %.1f%% threshold".format(
        tradingDaysSinceEntry,
        windowDays,
        gainPercent,
        thresholdPercent,
      ),
    )
  }

  override fun evaluateWithDetails(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    if (entryQuote == null) {
      return ConditionEvaluationResult(
        conditionType = "stagnation",
        description = description(),
        passed = false,
        message = "No entry quote",
      )
    }

    val tradingDaysSinceEntry = stock.countTradingDaysBetween(entryQuote.date, quote.date)
    val gainPercent = ((quote.closePrice - entryQuote.closePrice) / entryQuote.closePrice) * 100.0
    val passed = shouldExit(stock, entryQuote, quote)

    return ConditionEvaluationResult(
      conditionType = "stagnation",
      description = description(),
      passed = passed,
      actualValue = "%.2f%% gain after %d days".format(gainPercent, tradingDaysSinceEntry),
      threshold = ">= %.2f%% within %d days".format(thresholdPercent, windowDays),
      message = when {
        tradingDaysSinceEntry < windowDays -> "Window not reached ($tradingDaysSinceEntry/$windowDays days)"
        tradingDaysSinceEntry > windowDays -> "Window already passed"
        passed -> "Stagnation detected: %.2f%% < %.2f%% threshold".format(gainPercent, thresholdPercent)
        else -> "Sufficient progress: %.2f%% >= %.2f%%".format(gainPercent, thresholdPercent)
      },
    )
  }

  override fun getMetadata() =
    ConditionMetadata(
      type = "stagnation",
      displayName = "Price Stagnation",
      description = "Exit when price hasn't gained enough after N trading days (frees capital for better opportunities)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "thresholdPercent",
            displayName = "Min Gain %",
            type = "number",
            defaultValue = 3.0,
            min = 1.0,
            max = 10.0,
          ),
          ParameterMetadata(
            name = "windowDays",
            displayName = "Window (trading days)",
            type = "number",
            defaultValue = 15,
            min = 5,
            max = 30,
          ),
        ),
      category = "CapitalEfficiency",
    )
}
