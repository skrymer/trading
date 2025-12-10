package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.controller.dto.EntrySignalDetails
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Interface for entry strategies that support detailed condition evaluation.
 * This allows strategies to provide diagnostic information about why an entry signal
 * was triggered or failed, including individual condition results.
 */
interface DetailedEntryStrategy : EntryStrategy {
  /**
   * Evaluates the strategy and returns detailed condition results.
   * Useful for understanding why an entry signal triggered or failed.
   *
   * @param stock The stock being evaluated
   * @param quote The specific quote being evaluated
   * @return Detailed entry signal information including all condition results
   */
  fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): EntrySignalDetails
}
