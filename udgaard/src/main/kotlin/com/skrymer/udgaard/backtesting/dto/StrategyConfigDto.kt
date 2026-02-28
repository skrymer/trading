package com.skrymer.udgaard.backtesting.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig

/**
 * Request DTO for backtesting with either predefined or custom strategies
 */
/**
 * Optional configuration for ranker-specific parameters.
 */
data class RankerConfig(
  val sectorRanking: List<String>? = null, // Ordered sector symbols (first = highest priority)
)

data class BacktestRequest(
  val stockSymbols: List<String>? = null,
  val assetTypes: List<String>? = null, // Filter by asset type: STOCK, ETF, LEVERAGED_ETF, INDEX, BOND_ETF, COMMODITY_ETF
  val includeSectors: List<String>? = null, // Only include stocks in these sectors (e.g., ["XLK", "XLF"])
  val excludeSectors: List<String>? = null, // Exclude stocks in these sectors (e.g., ["XLE", "XLP"])
  val entryStrategy: StrategyConfig, // Can be predefined or custom
  val exitStrategy: StrategyConfig, // Can be predefined or custom
  val startDate: String? = null,
  val endDate: String? = null,
  val maxPositions: Int? = null,
  val ranker: String = "Adaptive",
  val useUnderlyingAssets: Boolean = true, // Enable automatic underlying asset detection
  val customUnderlyingMap: Map<String, String>? = null, // Custom symbol → underlying mappings
  val cooldownDays: Int = 0, // Global cooldown period in trading days after exit (0 = disabled)
  val entryDelayDays: Int = 0, // Delay entry by N trading days after signal (0 = enter on signal day)
  val positionSizing: PositionSizingConfig? = null, // Optional ATR-based position sizing
  val rankerConfig: RankerConfig? = null, // Optional ranker-specific parameters
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
