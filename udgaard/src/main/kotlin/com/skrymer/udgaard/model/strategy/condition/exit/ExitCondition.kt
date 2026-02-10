package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

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
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean

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
  fun getMetadata(): com.skrymer.udgaard.controller.dto.ConditionMetadata

  /**
   * Evaluates the condition and returns detailed results.
   * Includes actual values, thresholds, and explanatory messages.
   *
   * Default implementation provides basic pass/fail. Override to provide
   * rich diagnostic information with actual values and thresholds.
   */
  fun evaluateWithDetails(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult =
    ConditionEvaluationResult(
      conditionType = this::class.simpleName ?: "Unknown",
      description = description(),
      passed = shouldExit(stock, entryQuote, quote),
    )
}
