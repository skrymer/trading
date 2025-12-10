package com.skrymer.udgaard.controller.dto

/**
 * Result of evaluating a single trading condition.
 * Provides detailed information about why a condition passed or failed.
 */
data class ConditionEvaluationResult(
  /**
   * Type of condition (e.g., "UptrendCondition", "HeatmapCondition")
   */
  val conditionType: String,
  /**
   * Human-readable description of the condition
   */
  val description: String,
  /**
   * Whether the condition passed (true) or failed (false)
   */
  val passed: Boolean,
  /**
   * The actual value observed (e.g., "65.5" for heatmap)
   * Null if not applicable to this condition type
   */
  val actualValue: String? = null,
  /**
   * The threshold or requirement (e.g., "< 70" for heatmap)
   * Null if not applicable to this condition type
   */
  val threshold: String? = null,
  /**
   * Additional human-readable explanation
   * Example: "Price $56.15 is 2.5% below order block at $57.50 (18 days old)"
   */
  val message: String? = null,
)

/**
 * Detailed breakdown of an entry signal evaluation.
 * Contains all condition results for a specific entry strategy.
 */
data class EntrySignalDetails(
  /**
   * Name of the entry strategy (e.g., "PlanEtf")
   */
  val strategyName: String,
  /**
   * Human-readable description of the strategy
   */
  val strategyDescription: String,
  /**
   * Results for each condition in the strategy
   */
  val conditions: List<ConditionEvaluationResult>,
  /**
   * Whether all conditions were met (true = valid entry signal)
   */
  val allConditionsMet: Boolean,
)
