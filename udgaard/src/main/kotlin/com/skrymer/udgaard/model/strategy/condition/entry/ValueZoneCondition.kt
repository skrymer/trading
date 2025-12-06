package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.ConditionMetadata
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if price is within the value zone.
 * Value zone is defined as being above 20 EMA and below 20 EMA + (ATR multiplier * ATR).
 */
class ValueZoneCondition(private val atrMultiplier: Double = 2.0) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.closePrice > quote.closePriceEMA20 &&
               quote.closePrice < (quote.closePriceEMA20 + (atrMultiplier * quote.atr))
    }

    override fun description(): String = "Price within value zone (20EMA < price < 20EMA + ${atrMultiplier}ATR)"

    override fun getMetadata() = ConditionMetadata(
        type = "valueZone",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val price = quote.closePrice
        val ema20 = quote.closePriceEMA20
        val atr = quote.atr
        val upperBound = ema20 + (atrMultiplier * atr)

        val aboveEma20 = price > ema20
        val belowUpperBound = price < upperBound
        val passed = aboveEma20 && belowUpperBound

        val message = buildString {
            append("Price ${"%.2f".format(price)} ")
            if (aboveEma20) {
                append("> EMA20 (${"%.2f".format(ema20)}) ✓")
            } else {
                append("≤ EMA20 (${"%.2f".format(ema20)}) ✗")
            }
            append(" AND ")
            if (belowUpperBound) {
                append("< Upper Bound (${"%.2f".format(upperBound)}) ✓")
            } else {
                append("≥ Upper Bound (${"%.2f".format(upperBound)}) ✗")
            }
        }

        return ConditionEvaluationResult(
            conditionType = "ValueZoneCondition",
            description = description(),
            passed = passed,
            actualValue = "%.2f".format(price),
            threshold = "%.2f - %.2f".format(ema20, upperBound),
            message = message
        )
    }
}
