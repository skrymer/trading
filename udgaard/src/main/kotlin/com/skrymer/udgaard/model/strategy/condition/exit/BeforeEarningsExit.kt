package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition
import com.skrymer.udgaard.model.strategy.condition.ConditionMetadata

/**
 * Exit condition that triggers the day before an earnings announcement.
 *
 * This helps avoid earnings-related volatility by exiting positions one day
 * before the earnings are reported.
 *
 * @param daysBeforeEarnings Number of days before earnings to exit (default: 1)
 */
class BeforeEarningsExit(
    private val daysBeforeEarnings: Int = 1
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        val quoteDate = quote.date ?: return false
        return stock.hasEarningsWithinDays(quoteDate, daysBeforeEarnings)
    }

    override fun exitReason(): String {
        return if (daysBeforeEarnings == 1) {
            "Exit before earnings"
        } else {
            "Exit $daysBeforeEarnings days before earnings"
        }
    }

    override fun description(): String = exitReason()

    override fun getMetadata() = ConditionMetadata(
        type = "beforeEarnings",
        description = description()
    )

}
