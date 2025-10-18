package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class MainExitStrategy : ExitStrategy {
  var exitStrategies = emptyList<ExitStrategy>()

  init {
    exitStrategies = listOf(
      HalfAtrExitStrategy(),
      PriceUnder10EmaExitStrategy(),
      TenEmaCrossingUnderTwentyEma(),
      PriceUnder50EmaExitStrategy(),
      LessGreedyExitStrategy(),
      SellSignalExitStrategy(),
      HeatmapExitStrategy(),
    )
  }

  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = exitStrategies
    .map { it.match(stock, entryQuote, quote) }
    .any { it }


  override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
    exitStrategies
      .map { it.test(stock, entryQuote, quote) }
      .filter { it.match }
      .mapNotNull { it.exitReason }
      .reduce { s1, s2 -> "$s1, $s2" }

  override fun exitPrice(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
    exitStrategies
      .map { it.test(stock, entryQuote, quote) }
      .filter { it.match }
      .maxOf { it.exitPrice }

  override fun description() = "Main exit strategy"
}