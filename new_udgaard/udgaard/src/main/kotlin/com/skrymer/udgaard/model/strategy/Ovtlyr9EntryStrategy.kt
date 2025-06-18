package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

/**
 * Entry strategy based on the ovtlyr 9 criteria:
 *
 * Market
 * Market breadth is in an uptrend
 * Spy is in an uptrend
 * Spy has a buy signal
 *
 * Sector:
 * Sector breadth is in an uptrend
 * Sector is getting greeder
 *
 * Stock:
 * is in an uptrend
 * has a buy signal within the last 2 days
 * is getting more greedy
 * close price is over the 10EMA
 */
class Ovtlyr9EntryStrategy : EntryStrategy {
    override fun test(quote: StockQuote?): Boolean {
        return StockIsInUptrend()
            .and(ClosePriceOver10Ema())
            .and(CurrentBuySignal())
            .and(StockGettingGreeder())
            .and(SectorIsInUptrend())
            .and(SectorIsGettingGreeder())
            .and(HasSpyBuySignal())
            .and(SpyIsInUptrend())
            .and(MarketIsInUptrend())
            .test(quote)
    }

    override fun description(): String {
        return "Ovtlyr 9 entry strategy"
    }
}
