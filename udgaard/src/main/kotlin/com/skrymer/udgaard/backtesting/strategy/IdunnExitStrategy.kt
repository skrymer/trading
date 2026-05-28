package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitProximity
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Idunn exit strategy. Exits on any of:
 *  - **+10% gain** from entry (`PercentGainExit`)
 *  - **Close below 50-EMA** (`PriceBelowEmaExit(50)`)
 *  - **2.5×ATR stop loss** (`StopLossExit`)
 */
@RegisteredStrategy(name = "IdunnExitStrategy", type = StrategyType.EXIT)
class IdunnExitStrategy : ExitStrategy {
  private val compositeStrategy =
    exitStrategy {
      percentGain(10.0)
      priceBelowEma(50)
      stopLoss(atrMultiplier = 2.5)
    }

  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = compositeStrategy.match(stock, entryQuote, quote)

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): String? = compositeStrategy.reason(stock, entryQuote, quote)

  override fun description() = "Idunn — +10% gain OR EMA50 break OR 2.5×ATR stop"

  override fun exitPrice(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Double =
    if (quote.closePrice < 1.0) {
      stock.getPreviousQuote(quote)?.closePrice ?: 0.0
    } else {
      quote.closePrice
    }

  override fun exitProximities(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): List<ExitProximity> = compositeStrategy.exitProximities(stock, entryQuote, quote)

  /**
   * Get the underlying composite strategy for metadata extraction.
   */
  fun getCompositeStrategy(): CompositeExitStrategy = compositeStrategy
}
