package com.skrymer.udgaard.controller.dto

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

/**
 * A quote annotated with strategy signals.
 */
data class QuoteWithSignal(
  val quote: StockQuoteDomain,
  val entrySignal: Boolean = false,
  val entryDetails: EntrySignalDetails? = null,
  val exitSignal: Boolean = false,
  val exitReason: String? = null,
)

/**
 * Stock data enhanced with entry/exit signals for specific strategies.
 */
data class StockWithSignals(
  val stock: StockDomain,
  val entryStrategyName: String,
  val exitStrategyName: String,
  val quotesWithSignals: List<QuoteWithSignal>,
)
