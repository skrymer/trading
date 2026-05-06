package com.skrymer.udgaard.backtesting.dto

import java.time.LocalDate

/**
 * Request body for evaluating entry conditions on a stock.
 */
data class ConditionEvaluationRequest(
  val conditions: List<ConditionConfig>,
  val operator: String = "AND",
)

/**
 * A single quote annotated with condition evaluation results.
 */
data class QuoteWithConditions(
  val date: LocalDate,
  val closePrice: Double,
  val allConditionsMet: Boolean,
  val conditionResults: List<ConditionEvaluationResult>,
)

/**
 * Response for condition evaluation on a stock.
 * Only quotes where all conditions are met are included in quotesWithConditions.
 */
data class StockConditionSignals(
  val symbol: String,
  val operator: String,
  val conditionDescriptions: List<String>,
  val totalQuotes: Int,
  val matchingQuotes: Int,
  val quotesWithConditions: List<QuoteWithConditions>,
)

/**
 * Request body for evaluating exit conditions on a stock from a hypothetical entry date.
 *
 * `entryDate` must match an existing quote in the stock's history; the service rejects
 * dates with no matching quote so silent fall-throughs (entry-quote-needing conditions
 * returning false because `entryQuote == null`) can't surprise the caller.
 */
data class ExitConditionEvaluationRequest(
  val conditions: List<ConditionConfig>,
  val operator: String = "OR",
  val entryDate: LocalDate,
)

/**
 * Response for exit-condition evaluation on a stock.
 * `totalQuotes` counts only quotes strictly after `entryDate` (the evaluation window).
 */
data class StockExitConditionSignals(
  val symbol: String,
  val operator: String,
  val entryDate: LocalDate,
  val conditionDescriptions: List<String>,
  val totalQuotes: Int,
  val matchingQuotes: Int,
  val quotesWithConditions: List<QuoteWithConditions>,
)
