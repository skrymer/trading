package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if there is a buy signal.
 * @param currentOnly If true, only accepts buy signals less than 1 day old
 */
class BuySignalCondition(private val currentOnly: Boolean = false) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return if (currentOnly) {
            quote.hasCurrentBuySignal()
        } else {
            quote.hasBuySignal()
        }
    }

    override fun description(): String = if (currentOnly) {
        "Has current buy signal (< 1 day old)"
    } else {
        "Has buy signal"
    }

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "buySignal",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val passed = if (currentOnly) {
            quote.hasCurrentBuySignal()
        } else {
            quote.hasBuySignal()
        }

        // Calculate signal age from lastBuySignal date and current quote date
        val signalAge = if (quote.lastBuySignal != null && quote.date != null) {
            java.time.temporal.ChronoUnit.DAYS.between(quote.lastBuySignal, quote.date).toInt()
        } else {
            -1
        }

        val message = when {
            !quote.hasBuySignal() -> "No buy signal present"
            currentOnly && signalAge > 0 -> "Buy signal is ${signalAge} days old (requires ≤ 1 day)"
            currentOnly && signalAge <= 1 -> "Current buy signal present (${signalAge} days old) ✓"
            signalAge >= 0 -> "Buy signal present (${signalAge} days old) ✓"
            else -> "Buy signal status unknown"
        }

        return ConditionEvaluationResult(
            conditionType = "BuySignalCondition",
            description = description(),
            passed = passed,
            actualValue = if (quote.hasBuySignal() && signalAge >= 0) "${signalAge} days old" else "No signal",
            threshold = if (currentOnly) "≤ 1 day" else "Present",
            message = message
        )
    }
}
