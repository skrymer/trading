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
            FearfulExitStrategy(),
            SellSignalExitStrategy(),
            HeatmapExitStrategy(),
            MarketAndSectorBreadthReversesExitStrategy()
        )
    }

    override fun test(
        entryQuote: StockQuote,
        quote: StockQuote
    ): Boolean {
        return exitStrategies.map { it.test(entryQuote, quote) }.any { it }

//        HalfAtrExitStrategy().test(entryQuote, quote)
//            .and(PriceUnder10EmaExitStrategy().test(entryQuote, quote))
//            .and(TenEMACrossingUnderTwentyEMA().test(entryQuote, quote))
//            .and(PriceUnderFiftyEMA().test(entryQuote, quote))
//            .and(FearfulExitStrategy().test(entryQuote, quote))
//            .and(SellSignalExitStrategy().test(entryQuote, quote))
//            .and(HeatmapExitStrategy().test(entryQuote, quote))
//            .and(MarketAndSectorBreadthReversesExitStrategy().test(entryQuote, quote))
    }

    override fun reason(entryQuote: StockQuote, quote: StockQuote): String {
        return exitStrategies.firstNotNullOf { it.testAndExitReason(entryQuote, quote).second }
    }

    override fun description() = "Main exit strategy"
}