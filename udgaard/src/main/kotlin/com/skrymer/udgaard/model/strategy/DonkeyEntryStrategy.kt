package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class DonkeyEntryStrategy: EntryStrategy {
  override fun description(): String {
    TODO("Not yet implemented")
  }

  override fun test(
    stock: Stock,
    quote: StockQuote
  ): Boolean {

    // MARKET
    // SPY has a buy signal
    return quote.hasSpyBuySignal() &&
      // SPY is in an uptrend 10 > 20 price > 50
      quote.spyInUptrend &&
      // Market stocks bull % over 10ema
      quote.isMarketInUptrend() &&
      // SPY heatmap value is less than 70
      quote.spyHeatmap < 70 &&
      // SPY heatmap value is rising
      quote.spyHeatmap > quote.spyPreviousHeatmap &&
      quote.hasCurrentBuySignal() &&
      // Close price is over 10ema
      quote.closePrice > quote.closePriceEMA10 &&
      // Stock is in an uptrend
      quote.isInUptrend() &&
      quote.marketDonkeyChannelScore == 2
  }
}