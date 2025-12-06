package com.skrymer.udgaard.service

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.model.strategy.*
import com.skrymer.udgaard.model.strategy.condition.LogicalOperator
import com.skrymer.udgaard.model.strategy.condition.TradingCondition
import com.skrymer.udgaard.model.strategy.condition.entry.*
import com.skrymer.udgaard.model.strategy.condition.exit.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DynamicStrategyBuilder(
    private val strategyRegistry: StrategyRegistry
) {
    private val logger = LoggerFactory.getLogger(DynamicStrategyBuilder::class.java)

    /**
     * Build an entry strategy from configuration
     */
    fun buildEntryStrategy(config: StrategyConfig): EntryStrategy? {
        return when (config) {
            is PredefinedStrategyConfig -> strategyRegistry.createEntryStrategy(config.name)
            is CustomStrategyConfig -> buildCustomEntryStrategy(config)
        }
    }

    /**
     * Build an exit strategy from configuration
     */
    fun buildExitStrategy(config: StrategyConfig): ExitStrategy? {
        return when (config) {
            is PredefinedStrategyConfig -> strategyRegistry.createExitStrategy(config.name)
            is CustomStrategyConfig -> buildCustomExitStrategy(config)
        }
    }

    private fun buildCustomEntryStrategy(config: CustomStrategyConfig): CompositeEntryStrategy {
        val conditions = config.conditions.map { buildEntryCondition(it) }
        val operator = when (config.operator.uppercase()) {
            "AND" -> LogicalOperator.AND
            "OR" -> LogicalOperator.OR
            "NOT" -> LogicalOperator.NOT
            "" -> LogicalOperator.AND  // Default for entry strategies
            else -> LogicalOperator.AND
        }
        return CompositeEntryStrategy(conditions, operator, config.description)
    }

    private fun buildCustomExitStrategy(config: CustomStrategyConfig): CompositeExitStrategy {
        logger.info("Building custom exit strategy with ${config.conditions.size} conditions, operator: '${config.operator}'")
        val conditions = config.conditions.map { buildExitCondition(it) }
        // For exit strategies, default to OR (exit when ANY condition is met)
        val operator = when (config.operator.uppercase()) {
            "AND" -> {
                // Warn if AND is explicitly used for exits (usually not desired)
                logger.warn("Using AND operator for exit strategy - ALL conditions must be true simultaneously. This is rarely desired for exits.")
                LogicalOperator.AND
            }
            "OR" -> LogicalOperator.OR
            "NOT" -> LogicalOperator.NOT
            "" -> LogicalOperator.OR  // Default for exit strategies
            else -> LogicalOperator.OR  // Default for exits
        }
        logger.info("Built custom exit strategy with operator: $operator, conditions: ${conditions.map { it.description() }}")
        return CompositeExitStrategy(conditions, operator, config.description)
    }

    private fun buildEntryCondition(config: ConditionConfig): TradingCondition {
        return when (config.type.lowercase()) {
            "uptrend" -> UptrendCondition()
            "buysignal" -> BuySignalCondition(
                daysOld = (config.parameters["daysOld"] as? Number)?.toInt() ?: -1
            )
            "heatmap" -> HeatmapCondition(
                threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 70.0
            )
            "priceaboveema" -> PriceAboveEmaCondition(
                emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10
            )
            "valuezone" -> ValueZoneCondition(
                atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.0
            )
            "spybuysignal" -> SpyBuySignalCondition()
            "spyuptrend" -> SpyUptrendCondition()
            "marketuptrend" -> MarketUptrendCondition()
            "spyheatmap" -> SpyHeatmapThresholdCondition(
                threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 70.0
            )
            "spyheatmaprising" -> SpyHeatmapRisingCondition()
            "sectoruptrend" -> SectorUptrendCondition()
            "sectorheatmaprising" -> SectorHeatmapRisingCondition()
            "sectorheatmap" -> SectorHeatmapThresholdCondition(
                threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 70.0
            )
            "donkeychannel" -> DonkeyChannelCondition()
            "sectorheatmapgreaterthanspy" -> SectorHeatmapGreaterThanSpyCondition()
            "stockheatmaprising" -> StockHeatmapRisingCondition()
            "priceabovepreviouslow" -> PriceAbovePreviousLowCondition()
            "notinorderblock" -> NotInOrderBlockCondition(
                ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 120
            )
            "beloworderblock" -> BelowOrderBlockCondition(
                percentBelow = (config.parameters["percentBelow"] as? Number)?.toDouble() ?: 2.0,
                ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 30
            )
            "emabullishcross" -> EmaBullishCrossCondition(
                fastEma = (config.parameters["fastEma"] as? Number)?.toInt() ?: 10,
                slowEma = (config.parameters["slowEma"] as? Number)?.toInt() ?: 20
            )
            else -> throw IllegalArgumentException("Unknown entry condition type: ${config.type}")
        }
    }

    private fun buildExitCondition(config: ConditionConfig): ExitCondition {
        logger.info("Building exit condition: type=${config.type}, params=${config.parameters}")
        val condition = when (config.type.lowercase()) {
            "sellsignal" -> SellSignalExit()
            "emacross" -> {
                val fastEma = (config.parameters["fastEma"] as? Number)?.toInt() ?: 10
                val slowEma = (config.parameters["slowEma"] as? Number)?.toInt() ?: 20
                logger.info("  -> EmaCrossExit(fastEma=$fastEma, slowEma=$slowEma)")
                EmaCrossExit(fastEma, slowEma)
            }
            "profittarget" -> {
                val atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 3.0
                val emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 20
                logger.info("  -> ProfitTargetExit(atrMultiplier=$atrMultiplier, emaPeriod=$emaPeriod)")
                ProfitTargetExit(atrMultiplier, emaPeriod)
            }
            "orderblock" -> {
                val ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 120
                val source = config.parameters["source"] as? String ?: "CALCULATED"
                logger.info("  -> OrderBlockExit(ageInDays=$ageInDays, source=$source)")
                OrderBlockExit(ageInDays, source)
            }
            "stoploss" -> StopLossExit(
                atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.0
            )
            "trailingstoploss" -> {
                val atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.7
                logger.info("  -> ATRTrailingStopLoss(atrMultiplier=$atrMultiplier)")
                ATRTrailingStopLoss(atrMultiplier)
            }
            "pricebelowema" -> PriceBelowEmaExit(
                emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10
            )
            "belowpreviousdaylow" -> BelowPreviousDayLowExit()
            "heatmapthreshold" -> HeatmapThresholdExit()
            "heatmapdeclining" -> HeatmapDecliningExit()
            "marketandsectordowntrend" -> MarketAndSectorDowntrendExit()
            else -> throw IllegalArgumentException("Unknown exit condition type: ${config.type}")
        }
        return condition
    }

    /**
     * Get metadata about all available conditions for the UI
     */
    fun getAvailableConditions(): AvailableConditionsResponse {
        return AvailableConditionsResponse(
            entryConditions = getEntryConditionMetadata(),
            exitConditions = getExitConditionMetadata()
        )
    }

    private fun getEntryConditionMetadata(): List<ConditionMetadata> {
        return listOf(
            // Stock conditions
            ConditionMetadata(
                type = "uptrend",
                displayName = "Stock in Uptrend",
                description = "Stock trend is 'Uptrend'",
                parameters = emptyList(),
                category = "Stock"
            ),
            ConditionMetadata(
                type = "buySignal",
                displayName = "Buy Signal",
                description = "Stock has a buy signal within specified age",
                parameters = listOf(
                    ParameterMetadata(
                        name = "daysOld",
                        displayName = "Max Age (Days)",
                        type = "number",
                        defaultValue = -1,
                        min = -1,
                        max = 100,
                        options = listOf("-1", "0", "1", "2", "3", "5", "7", "10", "14", "21", "30")
                    )
                ),
                category = "Stock"
            ),
            ConditionMetadata(
                type = "heatmap",
                displayName = "Heatmap Below Threshold",
                description = "Stock heatmap is below the threshold",
                parameters = listOf(
                    ParameterMetadata(
                        name = "threshold",
                        displayName = "Threshold",
                        type = "number",
                        defaultValue = 70.0,
                        min = 0,
                        max = 100
                    )
                ),
                category = "Stock"
            ),
            ConditionMetadata(
                type = "priceAboveEma",
                displayName = "Price Above EMA",
                description = "Price is above the specified EMA",
                parameters = listOf(
                    ParameterMetadata(
                        name = "emaPeriod",
                        displayName = "EMA Period",
                        type = "number",
                        defaultValue = 10,
                        options = listOf("5", "10", "20", "50")
                    )
                ),
                category = "Stock"
            ),
            ConditionMetadata(
                type = "valueZone",
                displayName = "In Value Zone",
                description = "Price is within value zone (20 EMA to 20 EMA + ATR multiplier)",
                parameters = listOf(
                    ParameterMetadata(
                        name = "atrMultiplier",
                        displayName = "ATR Multiplier",
                        type = "number",
                        defaultValue = 2.0,
                        min = 0.5,
                        max = 5.0
                    )
                ),
                category = "Stock"
            ),
            ConditionMetadata(
                type = "stockHeatmapRising",
                displayName = "Stock Heatmap Rising",
                description = "Stock heatmap is increasing compared to previous day",
                parameters = emptyList(),
                category = "Stock"
            ),
            ConditionMetadata(
                type = "priceAbovePreviousLow",
                displayName = "Price Above Previous Low",
                description = "Current price is above previous day's low",
                parameters = emptyList(),
                category = "Stock"
            ),

            // SPY/Market conditions
            ConditionMetadata(
                type = "spyBuySignal",
                displayName = "SPY Buy Signal",
                description = "SPY has a buy signal",
                parameters = emptyList(),
                category = "SPY"
            ),
            ConditionMetadata(
                type = "spyUptrend",
                displayName = "SPY in Uptrend",
                description = "SPY trend is 'Uptrend'",
                parameters = emptyList(),
                category = "SPY"
            ),
            ConditionMetadata(
                type = "marketUptrend",
                displayName = "Market in Uptrend",
                description = "Market is in uptrend based on breadth",
                parameters = emptyList(),
                category = "Market"
            ),
            ConditionMetadata(
                type = "spyHeatmap",
                displayName = "SPY Heatmap Below Threshold",
                description = "SPY heatmap is below the threshold",
                parameters = listOf(
                    ParameterMetadata(
                        name = "threshold",
                        displayName = "Threshold",
                        type = "number",
                        defaultValue = 70.0,
                        min = 0,
                        max = 100
                    )
                ),
                category = "SPY"
            ),
            ConditionMetadata(
                type = "spyHeatmapRising",
                displayName = "SPY Heatmap Rising",
                description = "SPY heatmap is increasing",
                parameters = emptyList(),
                category = "SPY"
            ),

            // Sector conditions
            ConditionMetadata(
                type = "sectorUptrend",
                displayName = "Sector in Uptrend",
                description = "Stock's sector is in uptrend",
                parameters = emptyList(),
                category = "Sector"
            ),
            ConditionMetadata(
                type = "sectorHeatmap",
                displayName = "Sector Heatmap Below Threshold",
                description = "Sector heatmap is below the threshold",
                parameters = listOf(
                    ParameterMetadata(
                        name = "threshold",
                        displayName = "Threshold",
                        type = "number",
                        defaultValue = 70.0,
                        min = 0,
                        max = 100
                    )
                ),
                category = "Sector"
            ),
            ConditionMetadata(
                type = "sectorHeatmapRising",
                displayName = "Sector Heatmap Rising",
                description = "Sector heatmap is increasing",
                parameters = emptyList(),
                category = "Sector"
            ),
            ConditionMetadata(
                type = "sectorHeatmapGreaterThanSpy",
                displayName = "Sector Stronger Than SPY",
                description = "Sector heatmap is greater than SPY heatmap",
                parameters = emptyList(),
                category = "Sector"
            ),
            ConditionMetadata(
                type = "donkeyChannel",
                displayName = "Donkey Channel",
                description = "Stock is in donkey channel conditions",
                parameters = emptyList(),
                category = "Sector"
            ),

            // Order block conditions
            ConditionMetadata(
                type = "belowOrderBlock",
                displayName = "Below Order Block",
                description = "Price is below an order block by specified percentage",
                parameters = listOf(
                    ParameterMetadata(
                        name = "percentBelow",
                        displayName = "Percent Below",
                        type = "number",
                        defaultValue = 2.0,
                        min = 0.5,
                        max = 10.0
                    ),
                    ParameterMetadata(
                        name = "ageInDays",
                        displayName = "Age in Days",
                        type = "number",
                        defaultValue = 30,
                        min = 1,
                        max = 365
                    )
                ),
                category = "OrderBlock"
            ),
            ConditionMetadata(
                type = "notInOrderBlock",
                displayName = "Not in Order Block",
                description = "Price is not within an order block",
                parameters = listOf(
                    ParameterMetadata(
                        name = "ageInDays",
                        displayName = "Age in Days",
                        type = "number",
                        defaultValue = 120,
                        min = 1,
                        max = 365
                    )
                ),
                category = "OrderBlock"
            ),

            // EMA Cross condition
            ConditionMetadata(
                type = "emaBullishCross",
                displayName = "EMA Bullish Cross",
                description = "Fast EMA crosses above slow EMA (bullish crossover)",
                parameters = listOf(
                    ParameterMetadata(
                        name = "fastEma",
                        displayName = "Fast EMA",
                        type = "number",
                        defaultValue = 10,
                        options = listOf("5", "10", "20")
                    ),
                    ParameterMetadata(
                        name = "slowEma",
                        displayName = "Slow EMA",
                        type = "number",
                        defaultValue = 20,
                        options = listOf("10", "20", "50")
                    )
                ),
                category = "Trend"
            )
        )
    }

    private fun getExitConditionMetadata(): List<ConditionMetadata> {
        return listOf(
            ConditionMetadata(
                type = "sellSignal",
                displayName = "Sell Signal",
                description = "Exit when sell signal appears",
                parameters = emptyList(),
                category = "Signal"
            ),
            ConditionMetadata(
                type = "emaCross",
                displayName = "EMA Crossover",
                description = "Exit when fast EMA crosses below slow EMA",
                parameters = listOf(
                    ParameterMetadata(
                        name = "fastEma",
                        displayName = "Fast EMA",
                        type = "number",
                        defaultValue = 10,
                        options = listOf("5", "10", "20")
                    ),
                    ParameterMetadata(
                        name = "slowEma",
                        displayName = "Slow EMA",
                        type = "number",
                        defaultValue = 20,
                        options = listOf("10", "20", "50")
                    )
                ),
                category = "Trend"
            ),
            ConditionMetadata(
                type = "profitTarget",
                displayName = "Profit Target",
                description = "Exit when price extends above EMA + ATR multiplier",
                parameters = listOf(
                    ParameterMetadata(
                        name = "atrMultiplier",
                        displayName = "ATR Multiplier",
                        type = "number",
                        defaultValue = 3.0,
                        min = 1.0,
                        max = 10.0
                    ),
                    ParameterMetadata(
                        name = "emaPeriod",
                        displayName = "EMA Period",
                        type = "number",
                        defaultValue = 20,
                        options = listOf("10", "20", "50")
                    )
                ),
                category = "ProfitTaking"
            ),
            ConditionMetadata(
                type = "orderBlock",
                displayName = "Order Block",
                description = "Exit when price enters an order block",
                parameters = listOf(
                    ParameterMetadata(
                        name = "ageInDays",
                        displayName = "Age in Days",
                        type = "number",
                        defaultValue = 120,
                        min = 1,
                        max = 365
                    ),
                    ParameterMetadata(
                        name = "source",
                        displayName = "Source",
                        type = "select",
                        defaultValue = "CALCULATED",
                        options = listOf("CALCULATED", "OVTLYR", "ALL")
                    )
                ),
                category = "ProfitTaking"
            ),
            ConditionMetadata(
                type = "stopLoss",
                displayName = "Stop Loss",
                description = "Exit when price drops below entry - ATR multiplier",
                parameters = listOf(
                    ParameterMetadata(
                        name = "atrMultiplier",
                        displayName = "ATR Multiplier",
                        type = "number",
                        defaultValue = 2.0,
                        min = 0.5,
                        max = 5.0
                    )
                ),
                category = "StopLoss"
            ),
            ConditionMetadata(
                type = "trailingStopLoss",
                displayName = "ATR Trailing Stop Loss",
                description = "Exit when price drops X ATR below the highest price since entry (trailing stop)",
                parameters = listOf(
                    ParameterMetadata(
                        name = "atrMultiplier",
                        displayName = "ATR Multiplier",
                        type = "number",
                        defaultValue = 2.7,
                        min = 0.5,
                        max = 5.0
                    )
                ),
                category = "StopLoss"
            ),
            ConditionMetadata(
                type = "priceBelowEma",
                displayName = "Price Below EMA",
                description = "Exit when price closes below specified EMA",
                parameters = listOf(
                    ParameterMetadata(
                        name = "emaPeriod",
                        displayName = "EMA Period",
                        type = "number",
                        defaultValue = 10,
                        options = listOf("5", "10", "20", "50")
                    )
                ),
                category = "StopLoss"
            ),
            ConditionMetadata(
                type = "belowPreviousDayLow",
                displayName = "Below Previous Day Low",
                description = "Exit when price closes below previous day's low",
                parameters = emptyList(),
                category = "StopLoss"
            ),
            ConditionMetadata(
                type = "heatmapThreshold",
                displayName = "Heatmap Threshold",
                description = "Exit based on heatmap threshold",
                parameters = emptyList(),
                category = "Signal"
            ),
            ConditionMetadata(
                type = "heatmapDeclining",
                displayName = "Heatmap Declining",
                description = "Exit when heatmap is declining",
                parameters = emptyList(),
                category = "Signal"
            ),
            ConditionMetadata(
                type = "marketAndSectorDowntrend",
                displayName = "Market & Sector Downtrend",
                description = "Exit when both market and sector are in downtrend",
                parameters = emptyList(),
                category = "Trend"
            )
        )
    }
}
