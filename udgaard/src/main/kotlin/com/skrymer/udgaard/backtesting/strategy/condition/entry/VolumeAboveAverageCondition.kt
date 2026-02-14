package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if current volume is above average.
 *
 * This helps identify increased trading activity and institutional interest.
 * Higher volume often confirms price movements and indicates stronger conviction.
 *
 * @param multiplier Volume must be at least this multiple of the average (default: 1.3)
 * @param lookbackDays Number of days to calculate average volume (default: 20)
 */
@Component
class VolumeAboveAverageCondition(
  private val multiplier: Double = 1.3,
  private val lookbackDays: Int = 20,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    val currentVolume = quote.volume

    // Get quotes from the lookback period (excluding current day)
    val quoteDate = quote.date ?: return false
    val lookbackStartDate = quoteDate.minusDays(lookbackDays.toLong())

    val historicalQuotes =
      stock.quotes
        .filter { q ->
          val qDate = q.date
          qDate != null && qDate >= lookbackStartDate && qDate < quoteDate
        }.sortedByDescending { it.date }
        .take(lookbackDays)

    // Need at least half the lookback period to calculate average
    if (historicalQuotes.size < lookbackDays / 2) {
      return false
    }

    // Calculate average volume
    val avgVolume = historicalQuotes.map { it.volume }.average()

    return currentVolume >= (avgVolume * multiplier)
  }

  override fun description(): String = "Volume ≥ ${"%.1f".format(multiplier)}× avg ($lookbackDays days)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "volumeAboveAverage",
      displayName = "Volume Above Average",
      description = "Current volume is above average volume (indicates increased trading activity)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "multiplier",
            displayName = "Volume Multiplier",
            type = "number",
            defaultValue = 1.3,
            min = 1.0,
            max = 5.0,
          ),
          ParameterMetadata(
            name = "lookbackDays",
            displayName = "Lookback Days",
            type = "number",
            defaultValue = 20,
            min = 5,
            max = 100,
          ),
        ),
      category = "Volume",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val currentVolume = quote.volume
    val quoteDate = quote.date ?: return failedResult("No date available")

    val lookbackStartDate = quoteDate.minusDays(lookbackDays.toLong())

    val historicalQuotes =
      stock.quotes
        .filter { q ->
          val qDate = q.date
          qDate != null && qDate >= lookbackStartDate && qDate < quoteDate
        }.sortedByDescending { it.date }
        .take(lookbackDays)

    if (historicalQuotes.size < lookbackDays / 2) {
      return failedResult("Insufficient historical data (${historicalQuotes.size} quotes)")
    }

    val avgVolume = historicalQuotes.map { it.volume }.average()
    val ratio = currentVolume / avgVolume
    val passed = currentVolume >= (avgVolume * multiplier)

    val message =
      if (passed) {
        "Volume: ${formatVolume(currentVolume)} (${"%.2f".format(ratio)}× avg) ✓"
      } else {
        "Volume: ${formatVolume(currentVolume)} (${"%.2f".format(ratio)}× avg, needs ${"%.1f".format(multiplier)}×) ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "VolumeAboveAverageCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.2f".format(ratio)}×",
      threshold = "${"%.1f".format(multiplier)}×",
      message = message,
    )
  }

  private fun failedResult(reason: String): ConditionEvaluationResult =
    ConditionEvaluationResult(
      conditionType = "VolumeAboveAverageCondition",
      description = description(),
      passed = false,
      actualValue = null,
      threshold = null,
      message = "$reason ✗",
    )

  private fun formatVolume(volume: Long): String = when {
    volume >= 1_000_000 -> "${"%.1f".format(volume / 1_000_000.0)}M"
    volume >= 1_000 -> "${"%.1f".format(volume / 1_000.0)}K"
    else -> volume.toString()
  }
}
