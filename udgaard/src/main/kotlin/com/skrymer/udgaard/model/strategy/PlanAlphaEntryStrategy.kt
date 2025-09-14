package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class PlanAlphaEntryStrategy: EntryStrategy {
  override fun description() = "Plan Alpha entry strategy"

  override fun test(
    stock: Stock,
    quote: StockQuote
  ): Boolean {
    val previousQuote = stock.getPreviousQuote(quote)

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

    // SECTOR:
    // Sector bull % is over 10ema
    quote.sectorIsInUptrend() &&
    // Sector constituent heatmap rising
    quote.sectorIsGettingGreedier() &&
    // Sector constituent heatmap is below 70
    quote.sectorHeatmap < 70 &&
    // AS1 or AS2  (donkey channels)
    (quote.marketDonkeyChannelScore >= 1 && quote.sectorDonkeyChannelScore >=1)
      .or(quote.marketDonkeyChannelScore == 2) &&
    // Sector constituent heatmap is greater than SPY heatmap
    quote.sectorHeatmap > quote.spyHeatmap &&

    // STOCK:
    // Buy signal
    quote.hasCurrentBuySignal() &&
    // Close price is over 10ema
    quote.closePrice > quote.closePriceEMA10 &&
    // Stock is in an uptrend
    quote.isInUptrend() &&
    // Stock heatmap is rising
    quote.heatmap > (previousQuote?.heatmap ?: 0.0) &&
    // Above previous low
    quote.closePrice > (previousQuote?.low ?: 0.0)
    // quote not inside order block older than 120 days
//    !stock.withinOrderBlock(quote, 120)
  }

}