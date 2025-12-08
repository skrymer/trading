package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition
import com.skrymer.udgaard.model.strategy.condition.ConditionMetadata

/**
 * Exit condition that triggers on a sell signal.
 */
class SellSignalExit : ExitCondition {
    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        return quote.hasSellSignal()
    }

    override fun exitReason(): String = "Sell signal"

    override fun description(): String = "Sell signal"

    override fun getMetadata() = ConditionMetadata(
        type = "sellSignal",
        description = description()
    )
}
