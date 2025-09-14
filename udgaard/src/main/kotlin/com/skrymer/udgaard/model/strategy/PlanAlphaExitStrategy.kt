package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class PlanAlphaExitStrategy: ExitStrategy {
  var exitStrategies = emptyList<ExitStrategy>()

  init {
    exitStrategies = listOf(
      PriceUnder10EmaExitStrategy(),
      SellSignalExitStrategy(),
      // Earnings
      // Close inside OB older than 120 days
      // 5% Gap and crap.
    )
  }

  override fun match(
    entryQuote: StockQuote?,
    quote: StockQuote,
    previousQuote: StockQuote?
  ) = exitStrategies
    .map { it.match(entryQuote, quote, previousQuote) }
    .any { it }

  override fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
    exitStrategies
      .map { it.test(entryQuote, quote, previousQuote) }
      .filter { it.match }
      .mapNotNull { it.exitReason }
      .reduce { s1, s2 -> "$s1, $s2" }

  override fun exitPrice(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
    exitStrategies
      .map { it.test(entryQuote, quote, previousQuote) }
      .filter { it.match }
      .maxOf { it.exitPrice }

  override fun description() = "Main exit strategy"
}