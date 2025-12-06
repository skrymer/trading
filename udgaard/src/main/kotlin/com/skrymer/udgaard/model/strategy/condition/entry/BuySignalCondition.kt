package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if there is a buy signal within a specified age.
 * @param daysOld Maximum age of the buy signal in days (0 = current day only, -1 = any age)
 */
class BuySignalCondition(private val daysOld: Int = -1) : TradingCondition {
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

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "buySignal",
        description = description()
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
            daysOld >= 0 && signalAge > daysOld -> "Buy signal is ${signalAge} days old (requires ≤ ${daysOld} days) ✗"
            daysOld >= 0 && signalAge >= 0 && signalAge <= daysOld -> "Buy signal is ${signalAge} days old (≤ ${daysOld} days) ✓"
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
