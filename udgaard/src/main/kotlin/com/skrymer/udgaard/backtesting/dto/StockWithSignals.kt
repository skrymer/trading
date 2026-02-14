package com.skrymer.udgaard.backtesting.dto

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * A quote annotated with strategy signals.
 */
data class QuoteWithSignal(
  val quote: StockQuote,
  val entrySignal: Boolean = false,
  val entryDetails: EntrySignalDetails? = null,
  val exitSignal: Boolean = false,
  val exitReason: String? = null,
)

/**
 * Stock data enhanced with entry/exit signals for specific strategies.
 */
data class StockWithSignals(
  val stock: Stock,
  val entryStrategyName: String,
  val exitStrategyName: String,
  val quotesWithSignals: List<QuoteWithSignal>,
)
