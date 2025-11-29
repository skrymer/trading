package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition that triggers when price has been below a specific EMA for N consecutive days.
 * This is more conservative than [PriceBelowEmaExit] as it waits for confirmation over multiple days.
 *
 * Example: priceBelowEmaForDays(10, 4) exits only if close price has been under 10 EMA for 4 days straight.
 *
 * @param emaPeriod The EMA period to check against (5, 10, 20, or 50)
 * @param consecutiveDays Number of consecutive days price must be below EMA before exiting
 */
class PriceBelowEmaForDaysExit(
    private val emaPeriod: Int = 10,
    private val consecutiveDays: Int = 3
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        if (consecutiveDays <= 0) {
            return false
        }

        // Get the EMA value for the current quote
        val emaValue = getEmaValue(quote) ?: return false

        // Check if current price is below EMA
        if (quote.closePrice >= emaValue) {
            return false
        }

        // If we only need 1 day, we're done
        if (consecutiveDays == 1) {
            return true
        }

        // Get sorted quotes to look back
        val sortedQuotes = stock.quotes.sortedBy { it.date }
        val currentIndex = sortedQuotes.indexOfFirst { it.date == quote.date }

        if (currentIndex < consecutiveDays - 1) {
            // Not enough historical data
            return false
        }

        // Check the previous (consecutiveDays - 1) quotes
        // We already checked the current quote above
        // IMPORTANT: Skip the entry quote - it should not be counted in consecutive days
        for (i in 1 until consecutiveDays) {
            val previousQuote = sortedQuotes[currentIndex - i]

            // Skip the entry quote - don't count it in consecutive days
            if (entryQuote != null && previousQuote.date == entryQuote.date) {
                return false
            }

            val previousEma = getEmaValue(previousQuote) ?: return false

            if (previousQuote.closePrice >= previousEma) {
                return false
            }
        }

        return true
    }

    /**
     * Get the EMA value for the specified period from a quote.
     * Returns null for unsupported periods or if EMA is 0 (not calculated).
     */
    private fun getEmaValue(quote: StockQuote): Double? {
        val emaValue = when (emaPeriod) {
            5 -> quote.closePriceEMA5
            10 -> quote.closePriceEMA10
            20 -> quote.closePriceEMA20
            50 -> quote.closePriceEMA50
            else -> return null
        }

        // Treat 0 as uninitialized/invalid EMA
        return if (emaValue == 0.0) null else emaValue
    }

    override fun exitReason(): String =
        "Price has been under the $emaPeriod EMA for $consecutiveDays consecutive days"

    override fun description(): String =
        "Price below $emaPeriod EMA for $consecutiveDays days"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "priceBelowEma",
        description = description()
    )
}
