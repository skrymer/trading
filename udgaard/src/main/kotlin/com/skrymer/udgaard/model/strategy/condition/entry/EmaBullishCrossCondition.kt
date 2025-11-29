package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that triggers when a faster EMA crosses above a slower EMA (bullish cross).
 *
 * This detects the actual crossover by checking:
 * - Current: Fast EMA > Slow EMA
 * - Previous: Fast EMA <= Slow EMA
 *
 * @param fastEma The faster EMA period (default 10)
 * @param slowEma The slower EMA period (default 20)
 */
class EmaBullishCrossCondition(
    private val fastEma: Int = 10,
    private val slowEma: Int = 20
) : TradingCondition {

    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        // Get current EMA values
        val currentFast = getEmaValue(quote, fastEma)
        val currentSlow = getEmaValue(quote, slowEma)

        // If either value is 0, we can't evaluate
        if (currentFast == 0.0 || currentSlow == 0.0) {
            return false
        }

        // Check if currently fast > slow (bullish)
        if (currentFast <= currentSlow) {
            return false
        }

        // Get previous quote to check if this is a crossover
        val previousQuote = stock.getPreviousQuote(quote)
        if (previousQuote == null) {
            // If no previous quote, just check if currently bullish
            return currentFast > currentSlow
        }

        // Get previous EMA values
        val previousFast = getEmaValue(previousQuote, fastEma)
        val previousSlow = getEmaValue(previousQuote, slowEma)

        // Crossover detection: was bearish (fast <= slow), now bullish (fast > slow)
        return previousFast <= previousSlow && currentFast > currentSlow
    }

    override fun description(): String = "${fastEma}EMA crosses above ${slowEma}EMA"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "emaBullishCross",
        description = description()
    )

    private fun getEmaValue(quote: StockQuote, period: Int): Double {
        return when (period) {
            5 -> quote.closePriceEMA5
            10 -> quote.closePriceEMA10
            20 -> quote.closePriceEMA20
            50 -> quote.closePriceEMA50
            else -> 0.0
        }
    }
}
