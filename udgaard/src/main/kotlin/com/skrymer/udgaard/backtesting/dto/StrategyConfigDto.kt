package com.skrymer.udgaard.backtesting.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig

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
  val ranker: String? = null,
  val useUnderlyingAssets: Boolean = true, // Enable automatic underlying asset detection
  val customUnderlyingMap: Map<String, String>? = null, // Custom symbol → underlying mappings
  val cooldownDays: Int = 0, // Global cooldown period in trading days after exit (0 = disabled)
  val entryDelayDays: Int = 0, // Delay entry by N trading days after signal (0 = enter on signal day)
  val positionSizing: PositionSizingConfig? = null, // Optional ATR-based position sizing
  val rankerConfig: RankerConfig? = null, // Optional ranker-specific parameters
  val randomSeed: Long? = null, // Fixed seed for deterministic tie-breaking in ranker (null = random each run)
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
  // "number", "boolean", "string", or "stringList" (for List<String> params like ranker sectorRanking)
  val type: String,
  val defaultValue: Any?,
  val min: Number? = null,
  val max: Number? = null,
  val options: List<String>? = null, // For enum-like parameters
)

data class RankerMetadata(
  val type: String, // canonical name used in BacktestRequest.ranker
  val displayName: String,
  val description: String,
  val parameters: List<ParameterMetadata>,
  val category: String, // "Score-Based", "Sector-Priority", "Random"
  val usesRandomTieBreaks: Boolean = false,
)

/**
 * Walk-forward validation request.
 *
 * Window sizes and step can be expressed in years (existing defaults 5/1/1) or months
 * (optional, finer-grained). If a `*Months` field is set, it overrides the corresponding
 * `*Years` field. Month-based fields enable sub-year stepping (e.g. quarterly step = 3)
 * without breaking existing year-based callers.
 *
 * `positionSizing` and `randomSeed` are forwarded verbatim to each IS/OOS backtest. Absent →
 * walk-forward runs unlimited-mode as before. Set → walk-forward runs position-sized with
 * deterministic tie-breaking, matching how a live trader actually sizes trades.
 */
data class WalkForwardRequest(
  val entryStrategy: StrategyConfig,
  val exitStrategy: StrategyConfig,
  val stockSymbols: List<String>? = null,
  val assetTypes: List<String>? = null,
  val includeSectors: List<String>? = null,
  val excludeSectors: List<String>? = null,
  val startDate: String? = null,
  val endDate: String? = null,
  val inSampleYears: Int = 5,
  val outOfSampleYears: Int = 1,
  val stepYears: Int = 1,
  val inSampleMonths: Int? = null,
  val outOfSampleMonths: Int? = null,
  val stepMonths: Int? = null,
  val maxPositions: Int? = null,
  val ranker: String? = null,
  val rankerConfig: RankerConfig? = null,
  val useUnderlyingAssets: Boolean = true,
  val customUnderlyingMap: Map<String, String>? = null,
  val cooldownDays: Int = 0,
  val entryDelayDays: Int = 0,
  val randomSeed: Long? = null,
  val positionSizing: PositionSizingConfig? = null,
)
