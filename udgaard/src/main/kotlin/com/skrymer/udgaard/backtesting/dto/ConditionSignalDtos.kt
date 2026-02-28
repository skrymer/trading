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
