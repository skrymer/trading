package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class MainExitStrategy: ExitStrategy {
    var exitStrategies = emptyList<ExitStrategy>()

    init {
        exitStrategies = listOf(
            HalfAtrExitStrategy(),
            PriceUnder10EmaExitStrategy(),
            TenEMACrossingUnderTwentyEMA(),
            PriceUnderFiftyEMA(),
            LessGreedyExitStrategy(),
            SellSignalExitStrategy(),
            HeatmapExitStrategy(),
            MarketAndSectorBreadthReversesExitStrategy()
        )
    }

    override fun test(
        entryQuote: StockQuote?,
        quote: StockQuote
    ): Boolean {
        return exitStrategies.map { it.test(entryQuote, quote) }.any { it }
    }

    override fun reason(entryQuote: StockQuote?, quote: StockQuote): String {
        return exitStrategies.firstNotNullOf { it.testAndExitReason(entryQuote, quote).second }
    }

    override fun description() = "Main exit strategy"
}