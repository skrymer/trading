package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.DetailedEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.RegisteredStrategy
import com.skrymer.udgaard.backtesting.strategy.StrategyType
import com.skrymer.udgaard.backtesting.strategy.entryStrategy
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Test DetailedEntryStrategy fixture used to exercise the populated near-miss + condition-failure
 * paths in the scan endpoint without depending on a production strategy (e.g., Vcp) that may
 * change. Two conditions, deterministic outcomes against `BacktestTestDataGenerator` data:
 *
 * - `minimumPrice(0.01)` — passes for every fixture stock (none are sub-penny).
 * - `marketBreadthAbove(99.0)` — fails for every fixture day (the generator coerces breadth to
 *   the 40..90 band, so the threshold is provably never met).
 *
 * Net: 0 full matches, 55 stocks become near-misses (each with `conditionsPassed=1, total=2`),
 * and `conditionFailureSummary` reports 1 entry for `marketBreadthAbove` with
 * `stocksBlocked = totalStocksEvaluated = 55`.
 */
@RegisteredStrategy(name = "TestDetailedEntry", type = StrategyType.ENTRY)
class TestDetailedEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      minimumPrice(0.01)
      marketBreadthAbove(99.0)
    }

  override fun description() = "Test DetailedEntryStrategy — one always-passes condition, one always-fails"

  override fun test(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = compositeStrategy.test(stock, quote, context)

  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): EntrySignalDetails = compositeStrategy.testWithDetails(stock, quote, context)
}
