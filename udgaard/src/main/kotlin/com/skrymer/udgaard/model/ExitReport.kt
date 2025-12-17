package com.skrymer.udgaard.model

import com.skrymer.udgaard.domain.StockQuoteDomain

data class ExitReport(
  /**
   *  The exit reason
   */
  val exitReason: String = "",
  /**
   * The quotes that did not match the exit strategy including the quote that matched
   */
  val quotes: List<StockQuoteDomain> = emptyList(),
  /**
   * The exit price
   */
  val exitPrice: Double = 0.0,
)
