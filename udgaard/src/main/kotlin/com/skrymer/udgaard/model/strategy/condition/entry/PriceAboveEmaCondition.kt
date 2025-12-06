package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if the price is above a specific EMA.
 */
class PriceAboveEmaCondition(private val emaPeriod: Int) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return when (emaPeriod) {
            5 -> quote.closePrice > quote.closePriceEMA5
            10 -> quote.closePrice > quote.closePriceEMA10
            20 -> quote.closePrice > quote.closePriceEMA20
            50 -> quote.closePrice > quote.closePriceEMA50
            else -> false
        }
    }

    override fun description(): String = "Price > ${emaPeriod}EMA"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "priceAboveEma",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val price = quote.closePrice
        val emaValue = when (emaPeriod) {
            5 -> quote.closePriceEMA5
            10 -> quote.closePriceEMA10
            20 -> quote.closePriceEMA20
            50 -> quote.closePriceEMA50
            else -> 0.0
        }
        val passed = price > emaValue

        val message = if (passed) {
            "Price ${"%.2f".format(price)} > EMA${emaPeriod} ${"%.2f".format(emaValue)} ✓"
        } else {
            "Price ${"%.2f".format(price)} ≤ EMA${emaPeriod} ${"%.2f".format(emaValue)} ✗"
        }

        return ConditionEvaluationResult(
            conditionType = "PriceAboveEmaCondition",
            description = description(),
            passed = passed,
            actualValue = "Price: ${"%.2f".format(price)}, EMA${emaPeriod}: ${"%.2f".format(emaValue)}",
            threshold = "Price > EMA${emaPeriod}",
            message = message
        )
    }
}
