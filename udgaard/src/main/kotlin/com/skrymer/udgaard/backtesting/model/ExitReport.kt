package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.data.model.StockQuote

data class ExitReport(
  /**
   *  The exit reason
   */
  val exitReason: String = "",
  /**
   * The quotes that did not match the exit strategy including the quote that matched
   */
  val quotes: List<StockQuote> = emptyList(),
  /**
   * The exit price
   */
  val exitPrice: Double = 0.0,
)
