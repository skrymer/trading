package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * How close an exit condition is to triggering on the current bar.
 *
 * @property conditionType stable identifier (e.g. "stopLoss", "emaCross", "stagnation")
 * @property proximity 0.0 = far from triggering, 1.0 = would trigger now. Clamped into [0, 1].
 *   Invariant: whenever the corresponding condition's `shouldExit` returns true, proximity
 *   must be >= 1.0.
 * @property detail human-readable single-line explanation (e.g.
 *   "ema10=98.14, ema20=97.92, gap 0.21 (0.06 ATR)").
 */
data class ExitProximity(
  val conditionType: String,
  val proximity: Double,
  val detail: String,
)

/**
 * Represents an exit condition that can be evaluated.
 *
 * All implementations must be annotated with @Component to be auto-discovered by Spring.
 */
interface ExitCondition {
  /**
   * Determines if the exit condition is met.
   */
  fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean

  /**
   * Determines if exit condition is met, with backtest context.
   * Default delegates to the context-free method. Override for breadth-dependent conditions.
   */
  fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = shouldExit(stock, entryQuote, quote)

  /**
   * Returns the reason for exiting.
   */
  fun exitReason(): String

  /**
   * Returns a description of the exit condition.
   */
  fun description(): String

  /**
   * Get metadata for this condition including parameters and UI information.
   * This metadata is used by the API to expose conditions to the frontend.
   *
   * All implementations MUST override this method to provide:
   * - type: Unique identifier (e.g., "stopLoss")
   * - displayName: User-friendly name (e.g., "Stop Loss")
   * - description: Detailed explanation
   * - parameters: List of configurable parameters with metadata
   * - category: Grouping category (e.g., "StopLoss", "ProfitTaking", "Signal")
   *
   * @return Condition metadata for UI consumption
   */
  fun getMetadata(): com.skrymer.udgaard.backtesting.dto.ConditionMetadata

  /**
   * Evaluates the condition and returns detailed results.
   * Includes actual values, thresholds, and explanatory messages.
   *
   * Default implementation provides basic pass/fail. Override to provide
   * rich diagnostic information with actual values and thresholds.
   */
  fun evaluateWithDetails(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ConditionEvaluationResult =
    ConditionEvaluationResult(
      conditionType = this::class.simpleName ?: "Unknown",
      description = description(),
      passed = shouldExit(stock, entryQuote, quote),
    )

  /**
   * Evaluates with details and backtest context.
   * Default delegates to the context-free method.
   */
  fun evaluateWithDetails(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult = evaluateWithDetails(stock, entryQuote, quote)

  /**
   * How close this condition is to triggering on the given bar.
   *
   * Returning null means the proximity is not meaningful or not computable for this input
   * (e.g. `entryQuote` is null, the condition is not a proximity-aware one, or required
   * fields like entry ATR are missing/zero). Consumers should treat null as "no signal"
   * rather than 0.0 — 0.0 is a real value meaning "as far from triggering as possible".
   *
   * Default is null so existing non-proximity-aware conditions stay silent.
   */
  fun proximity(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ExitProximity? = null
}
