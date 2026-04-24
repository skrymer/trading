package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitProximity
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * VCP exit strategy: EMA cross + stop loss + stagnation exit for capital efficiency.
 *
 * The stagnation exit cuts trades that haven't gained 3% after 15 trading days,
 * freeing position slots for the SectorEdge ranker to deploy into fresh breakout signals.
 */
@RegisteredStrategy(name = "VcpExitStrategy", type = StrategyType.EXIT)
class VcpExitStrategy : ExitStrategy {
  private val compositeStrategy =
    exitStrategy {
      emaCross(10, 20)
      stopLoss(atrMultiplier = 2.5)
      stagnation(thresholdPercent = 3.0, windowDays = 15)
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

  override fun description() = "VCP exit strategy (EMA cross + stop loss + stagnation)"

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

  fun getCompositeStrategy(): CompositeExitStrategy = compositeStrategy
}
