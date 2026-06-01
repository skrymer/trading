package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.intOr
import com.skrymer.udgaard.backtesting.service.numberOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

@Component
class AverageDollarVolumeAboveCondition(
  private val thresholdUsd: Double = 50_000_000.0,
  private val lookbackDays: Int = 20,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val window = trailingWindow(stock, quote)
    if (window.size < lookbackDays / 2) return false
    return averageDollarVolume(window) >= thresholdUsd
  }

  // Bars strictly before the entry, last [lookbackDays] of them. Lookahead-safe (excludes
  // the entry bar) and O(log N) via index math — never filter+sort the full quote list (P1).
  private fun trailingWindow(
    stock: Stock,
    quote: StockQuote,
  ): List<StockQuote> {
    val entryIdx = stock.indexOnOrAfter(quote.date)
    return stock.quotes.subList(maxOf(0, entryIdx - lookbackDays), entryIdx)
  }

  // Double math so close × volume (Long) cannot overflow.
  private fun averageDollarVolume(window: List<StockQuote>): Double =
    window.sumOf { it.closePrice * it.volume.toDouble() } / window.size

  override fun description(): String = "Avg \$ volume ≥ ${formatUsd(thresholdUsd)} ($lookbackDays days)"

  private fun formatUsd(value: Double): String = when {
    value >= 1_000_000_000 -> "\$${"%.1f".format(value / 1_000_000_000.0)}B"
    value >= 1_000_000 -> "\$${"%.1f".format(value / 1_000_000.0)}M"
    value >= 1_000 -> "\$${"%.1f".format(value / 1_000.0)}K"
    else -> "\$${"%.0f".format(value)}"
  }

  override fun getMetadata() =
    ConditionMetadata(
      type = "averageDollarVolumeAbove",
      displayName = "Average Dollar Volume Above Threshold",
      description = "Trailing-average daily dollar volume (close × volume) is above a USD threshold",
      parameters =
        listOf(
          ParameterMetadata(
            name = "thresholdUsd",
            displayName = "Min Avg Dollar Volume (USD)",
            type = "number",
            defaultValue = thresholdUsd,
            min = 1_000_000,
            max = 1_000_000_000,
          ),
          ParameterMetadata(
            name = "lookbackDays",
            displayName = "Lookback Days",
            type = "number",
            defaultValue = lookbackDays,
            min = 5,
            max = 100,
          ),
        ),
      category = "Volume",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val window = trailingWindow(stock, quote)
    if (window.size < lookbackDays / 2) {
      return ConditionEvaluationResult(
        conditionType = "AverageDollarVolumeAboveCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "Insufficient historical data (${window.size} bars) ✗",
      )
    }
    val avg = averageDollarVolume(window)
    val passed = avg >= thresholdUsd
    val message =
      if (passed) {
        "Avg \$ volume ${formatUsd(avg)} (≥ ${formatUsd(thresholdUsd)}) ✓"
      } else {
        "Avg \$ volume ${formatUsd(avg)} (needs ≥ ${formatUsd(thresholdUsd)}) ✗"
      }
    return ConditionEvaluationResult(
      conditionType = "AverageDollarVolumeAboveCondition",
      description = description(),
      passed = passed,
      actualValue = formatUsd(avg),
      threshold = "≥ ${formatUsd(thresholdUsd)}",
      message = message,
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    AverageDollarVolumeAboveCondition(
      thresholdUsd = parameters.numberOr("thresholdUsd", thresholdUsd),
      lookbackDays = parameters.intOr("lookbackDays", lookbackDays),
    )
}
