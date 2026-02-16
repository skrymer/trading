package com.skrymer.udgaard.backtesting.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Request DTO for backtesting with either predefined or custom strategies
 */
data class BacktestRequest(
  val stockSymbols: List<String>? = null,
  val assetTypes: List<String>? = null, // Filter by asset type: STOCK, ETF, LEVERAGED_ETF, INDEX, BOND_ETF, COMMODITY_ETF
  val entryStrategy: StrategyConfig, // Can be predefined or custom
  val exitStrategy: StrategyConfig, // Can be predefined or custom
  val startDate: String? = null,
  val endDate: String? = null,
  val maxPositions: Int? = null,
  val ranker: String = "Adaptive",
  val useUnderlyingAssets: Boolean = true, // Enable automatic underlying asset detection
  val customUnderlyingMap: Map<String, String>? = null, // Custom symbol → underlying mappings
  val cooldownDays: Int = 0, // Global cooldown period in trading days after exit (0 = disabled)
)

/**
 * Base class for strategy configuration
 * Can be either a predefined strategy or a custom-built one
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
  JsonSubTypes.Type(value = PredefinedStrategyConfig::class, name = "predefined"),
  JsonSubTypes.Type(value = CustomStrategyConfig::class, name = "custom"),
)
sealed class StrategyConfig

/**
 * Reference to a predefined strategy by name
 */
data class PredefinedStrategyConfig(
  val name: String, // e.g., "PlanEtf", "PlanAlpha"
) : StrategyConfig()

/**
 * Custom strategy built from conditions
 */
data class CustomStrategyConfig(
  val conditions: List<ConditionConfig>,
  val operator: String = "", // Empty string = use default (AND for entry, OR for exit)
  val description: String? = null,
) : StrategyConfig()

/**
 * Configuration for a single trading condition
 */
data class ConditionConfig(
  val type: String, // e.g., "uptrend", "priceAboveEma", "stopLoss"
  val parameters: Map<String, Any> = emptyMap(), // Condition-specific params
)

/**
 * Response with available conditions and their parameters
 */
data class AvailableConditionsResponse(
  val entryConditions: List<ConditionMetadata>,
  val exitConditions: List<ConditionMetadata>,
)

data class ConditionMetadata(
  val type: String,
  val displayName: String,
  val description: String,
  val parameters: List<ParameterMetadata>,
  val category: String, // "Stock", "Market", "SPY", "Sector", etc.
)

data class ParameterMetadata(
  val name: String,
  val displayName: String,
  val type: String, // "number", "boolean", "string"
  val defaultValue: Any?,
  val min: Number? = null,
  val max: Number? = null,
  val options: List<String>? = null, // For enum-like parameters
)
