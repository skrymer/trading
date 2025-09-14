package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Entry strategy based on the ovtlyr 9 criteria:
 *
 * Market:
 *  Market breadth is in an uptrend
 *  Spy is in an uptrend
 *  Spy has a buy signal
 *  Donchian upper channel is moving up
 *
 * Sector:
 *  Sector breadth is in an uptrend
 *  Sector heatmap is getting greedier
 *  Donchian upper channel is moving up
 *
 * Stock:
 *  is in an uptrend
 *  has a buy signal
 *  heatmap is getting greedier
 *  close price is over the 10EMA
 */
class Ovtlyr9EntryStrategy : EntryStrategy {
  override fun test(stock: Stock, quote: StockQuote) =
    quote.isInUptrend()
      .and(quote.isSpyInUptrend())
      .and(quote.hasSpyBuySignal())
      .and(quote.sectorIsInUptrend())
      .and(quote.sectorIsGettingGreedier())
      .and(quote.isInUptrend())
      .and(quote.hasCurrentBuySignal())
      .and(quote.isGettingGreedier())
      .and(quote.closePrice > quote.closePriceEMA10)

  override fun description(): String {
    return "Ovtlyr 9 entry strategy"
  }
}
