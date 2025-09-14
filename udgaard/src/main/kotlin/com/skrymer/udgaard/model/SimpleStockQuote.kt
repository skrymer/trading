package com.skrymer.udgaard.model

/**
 * Simple stock quote.
 */
data class SimpleStockQuote(
  /**
   * The value at open in US dollars.
   */
  val open: Double = 0.0,
  /**
   * The high value in US dollars.
   */
  val high: Double = 0.0,
  /**
   * The low value in US dollars.
   */
  val low: Double = 0.0,
  /**
   * The value at close in US dollars.
   */
  val close: Double = 0.0,
  /**
   * The volume.
   */
  val volume: Int = 0
)
