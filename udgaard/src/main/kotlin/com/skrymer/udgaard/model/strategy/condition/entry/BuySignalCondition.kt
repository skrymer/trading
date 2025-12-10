package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Condition that checks if there is a buy signal within a specified age.
 * @param daysOld Maximum age of the buy signal in days (0 = current day only, -1 = any age)
 */
@Component
class BuySignalCondition(private val daysOld: Int = -1) : EntryCondition {
  override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
    if (!quote.hasBuySignal()) return false

    // If daysOld is -1, accept any buy signal regardless of age
    if (daysOld < 0) return true

    // Calculate signal age
    val signalAge = if (quote.lastBuySignal != null && quote.date != null) {
      java.time.temporal.ChronoUnit.DAYS.between(quote.lastBuySignal, quote.date).toInt()
    } else {
      return false
    }

    return signalAge <= daysOld
  }

  override fun description(): String = when {
    daysOld < 0 -> "Has buy signal"
    daysOld == 0 -> "Has current buy signal (today)"
    daysOld == 1 -> "Has buy signal (≤ 1 day old)"
    else -> "Has buy signal (≤ $daysOld days old)"
  }

  override fun getMetadata() = ConditionMetadata(
    type = "buySignal",
    displayName = "Buy Signal",
    description = "Stock has a buy signal within specified age",
    parameters = listOf(
      ParameterMetadata(
        name = "daysOld",
        displayName = "Max Age (Days)",
        type = "number",
        defaultValue = -1,
        min = -1,
        max = 100,
        options = listOf("-1", "0", "1", "2", "3", "5", "7", "10", "14", "21", "30")
      )
    ),
    category = "Stock"
  )

  override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)

    // Calculate signal age from lastBuySignal date and current quote date
    val signalAge = if (quote.lastBuySignal != null && quote.date != null) {
      java.time.temporal.ChronoUnit.DAYS.between(quote.lastBuySignal, quote.date).toInt()
    } else {
      -1
    }

    val message = when {
      !quote.hasBuySignal() -> "No buy signal present ✗"
      daysOld < 0 && signalAge >= 0 -> "Buy signal present (${signalAge} days old) ✓"
      daysOld in 0..<signalAge -> "Buy signal is ${signalAge} days old (requires ≤ ${daysOld} days) ✗"
      daysOld >= 0 && signalAge >= 0 -> "Buy signal is ${signalAge} days old (≤ ${daysOld} days) ✓"
      else -> "Buy signal status unknown ✗"
    }

    val thresholdText = when {
      daysOld < 0 -> "Present"
      daysOld == 0 -> "Today"
      else -> "≤ ${daysOld} days"
    }

    return ConditionEvaluationResult(
      conditionType = "BuySignalCondition",
      description = description(),
      passed = passed,
      actualValue = if (quote.hasBuySignal() && signalAge >= 0) "${signalAge} days old" else "No signal",
      threshold = thresholdText,
      message = message
    )
  }
}
