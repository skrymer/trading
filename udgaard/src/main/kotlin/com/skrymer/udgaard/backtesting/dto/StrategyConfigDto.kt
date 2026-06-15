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
  val riskFreeRatePct: Double? = null, // Annualized RF in percent for Sharpe (excess return) and Sortino MAR. Null = 0 = raw Sharpe.
  // Round-trip transaction cost (commission + slippage) in bps, netted into per-trade P&L. Net-by-default; 0 = gross.
  val costBps: Double = 10.0,
  // Credit idle (uninvested) cash the historical short rate (ADR 0016). Null = default ON; false reproduces 0%-cash.
  val creditIdleCash: Boolean? = null,
  // Gate entries to the tradable universe (point-in-time price/liquidity/age, ADR 0026). Default ON;
  // false reproduces the pre-#173 unfiltered-universe runs.
  val applyLiquidityFilter: Boolean = true,
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
  val name: String, // e.g., "MyEntryStrategy", "MyExitStrategy"
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
 * A node in a custom strategy's condition tree — either a leaf or a group.
 *
 * - Leaf: a single condition identified by [type] with optional [parameters].
 * - Group: a nested boolean group of child [conditions] joined by [operator]
 *   (AND/OR/NOT). A group lets a custom strategy express `(A AND B) OR (C AND D)`,
 *   which a flat single-operator list cannot. Groups nest to arbitrary depth.
 *
 * A node is treated as a group when [conditions] is non-null; otherwise it is a leaf.
 * Existing flat configs (only [type] + [parameters]) keep working unchanged.
 */
data class ConditionConfig(
  val type: String = "", // e.g., "uptrend", "priceAboveEma", "stopLoss" (empty for groups)
  val parameters: Map<String, Any> = emptyMap(), // Condition-specific params
  val operator: String? = null, // Group only: AND/OR/NOT joining child conditions
  val conditions: List<ConditionConfig>? = null, // Group only: child nodes (leaf or group)
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
  // Annualized risk-free rate (percent) for Sharpe/Sortino. Default 0 (raw Sharpe), matching
  // the single-backtest endpoint's default. Used by WalkForwardService when computing per-window
  // and stitched-aggregate risk-adjusted metrics per ADR-0005.
  val riskFreeRatePct: Double? = null,
  // Credit idle cash the historical short rate within each OOS window (ADR 0016). Null = default ON.
  val creditIdleCash: Boolean? = null,
  // Gate entries to the tradable universe (point-in-time price/liquidity/age, ADR 0026). Default ON;
  // false reproduces the pre-#173 unfiltered-universe runs.
  val applyLiquidityFilter: Boolean = true,
)
