package com.skrymer.udgaard.service

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.model.strategy.*
import com.skrymer.udgaard.model.strategy.condition.LogicalOperator
import com.skrymer.udgaard.model.strategy.condition.entry.*
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.model.strategy.condition.exit.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DynamicStrategyBuilder(
  private val strategyRegistry: StrategyRegistry,
) {
  private val logger = LoggerFactory.getLogger(DynamicStrategyBuilder::class.java)

  /**
   * Build an entry strategy from configuration
   */
  fun buildEntryStrategy(config: StrategyConfig): EntryStrategy? =
    when (config) {
      is PredefinedStrategyConfig -> strategyRegistry.createEntryStrategy(config.name)
      is CustomStrategyConfig -> buildCustomEntryStrategy(config)
    }

  /**
   * Build an exit strategy from configuration
   */
  fun buildExitStrategy(config: StrategyConfig): ExitStrategy? =
    when (config) {
      is PredefinedStrategyConfig -> strategyRegistry.createExitStrategy(config.name)
      is CustomStrategyConfig -> buildCustomExitStrategy(config)
    }

  private fun buildCustomEntryStrategy(config: CustomStrategyConfig): CompositeEntryStrategy {
    val conditions = config.conditions.map { buildEntryCondition(it) }
    val operator =
      when (config.operator.uppercase()) {
        "AND" -> LogicalOperator.AND
        "OR" -> LogicalOperator.OR
        "NOT" -> LogicalOperator.NOT
        "" -> LogicalOperator.AND // Default for entry strategies
        else -> LogicalOperator.AND
      }
    return CompositeEntryStrategy(conditions, operator, config.description)
  }

  private fun buildCustomExitStrategy(config: CustomStrategyConfig): CompositeExitStrategy {
    logger.info("Building custom exit strategy with ${config.conditions.size} conditions, operator: '${config.operator}'")
    val conditions = config.conditions.map { buildExitCondition(it) }
    // For exit strategies, default to OR (exit when ANY condition is met)
    val operator =
      when (config.operator.uppercase()) {
        "AND" -> {
          // Warn if AND is explicitly used for exits (usually not desired)
          logger.warn(
            "Using AND operator for exit strategy - ALL conditions must be true simultaneously. This is rarely desired for exits.",
          )
          LogicalOperator.AND
        }
        "OR" -> LogicalOperator.OR
        "NOT" -> LogicalOperator.NOT
        "" -> LogicalOperator.OR // Default for exit strategies
        else -> LogicalOperator.OR // Default for exits
      }
    logger.info("Built custom exit strategy with operator: $operator, conditions: ${conditions.map { it.description() }}")
    return CompositeExitStrategy(conditions, operator, config.description)
  }

  private fun buildEntryCondition(config: ConditionConfig): EntryCondition =
    when (config.type.lowercase()) {
      "uptrend" -> UptrendCondition()
      "buysignal" ->
        BuySignalCondition(
          daysOld = (config.parameters["daysOld"] as? Number)?.toInt() ?: -1,
        )
      "heatmap" ->
        HeatmapCondition(
          threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 70.0,
        )
      "priceaboveema" ->
        PriceAboveEmaCondition(
          emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10,
        )
      "valuezone" ->
        ValueZoneCondition(
          atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.0,
        )
      "spybuysignal" -> SpyBuySignalCondition()
      "spyuptrend" -> SpyUptrendCondition()
      "marketuptrend" -> MarketUptrendCondition()
      "spyheatmap" ->
        SpyHeatmapThresholdCondition(
          threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 70.0,
        )
      "spyheatmaprising" -> SpyHeatmapRisingCondition()
      "sectoruptrend" -> SectorUptrendCondition()
      "sectorheatmaprising" -> SectorHeatmapRisingCondition()
      "sectorheatmap" ->
        SectorHeatmapThresholdCondition(
          threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 70.0,
        )
      "donkeychannel" -> DonkeyChannelCondition()
      "sectorheatmapgreaterthanspy" -> SectorHeatmapGreaterThanSpyCondition()
      "stockheatmaprising" -> StockHeatmapRisingCondition()
      "priceabovepreviouslow" -> PriceAbovePreviousLowCondition()
      "notinorderblock" ->
        NotInOrderBlockCondition(
          ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 120,
        )
      "beloworderblock" ->
        BelowOrderBlockCondition(
          percentBelow = (config.parameters["percentBelow"] as? Number)?.toDouble() ?: 2.0,
          ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 30,
        )
      "emabullishcross" ->
        EmaBullishCrossCondition(
          fastEma = (config.parameters["fastEma"] as? Number)?.toInt() ?: 10,
          slowEma = (config.parameters["slowEma"] as? Number)?.toInt() ?: 20,
        )
      "adxrange" ->
        ADXRangeCondition(
          minADX = (config.parameters["minADX"] as? Number)?.toDouble() ?: 20.0,
          maxADX = (config.parameters["maxADX"] as? Number)?.toDouble() ?: 50.0,
        )
      "atrexpanding" ->
        ATRExpandingCondition(
          minPercentile = (config.parameters["minPercentile"] as? Number)?.toDouble() ?: 30.0,
          maxPercentile = (config.parameters["maxPercentile"] as? Number)?.toDouble() ?: 70.0,
          lookbackPeriod = (config.parameters["lookbackPeriod"] as? Number)?.toInt() ?: 252,
        )
      "emaalignment" ->
        EmaAlignmentCondition(
          fastEmaPeriod = (config.parameters["fastEmaPeriod"] as? Number)?.toInt() ?: 10,
          slowEmaPeriod = (config.parameters["slowEmaPeriod"] as? Number)?.toInt() ?: 20,
        )
      "marketbreadthabove" ->
        MarketBreadthAboveCondition(
          threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 50.0,
        )
      "minimumprice" ->
        MinimumPriceCondition(
          minimumPrice = (config.parameters["minimumPrice"] as? Number)?.toDouble() ?: 10.0,
        )
      "orderblockrejection" ->
        OrderBlockRejectionCondition(
          minRejections = (config.parameters["minRejections"] as? Number)?.toInt() ?: 2,
          ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 30,
          rejectionThreshold = (config.parameters["rejectionThreshold"] as? Number)?.toDouble() ?: 2.0,
        )
      "sectorbreadthgreaterthanspy" -> SectorBreadthGreaterThanSpyCondition()
      "volumeaboveaverage" ->
        VolumeAboveAverageCondition(
          multiplier = (config.parameters["multiplier"] as? Number)?.toDouble() ?: 1.3,
          lookbackDays = (config.parameters["lookbackDays"] as? Number)?.toInt() ?: 20,
        )
      else -> throw IllegalArgumentException("Unknown entry condition type: ${config.type}")
    }

  private fun buildExitCondition(config: ConditionConfig): ExitCondition {
    logger.info("Building exit condition: type=${config.type}, params=${config.parameters}")
    val condition =
      when (config.type.lowercase()) {
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
          logger.info("  -> OrderBlockExit(ageInDays=$ageInDays)")
          OrderBlockExit(ageInDays)
        }
        "stoploss" ->
          StopLossExit(
            atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.0,
          )
        "trailingstoploss" -> {
          val atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.7
          logger.info("  -> ATRTrailingStopLoss(atrMultiplier=$atrMultiplier)")
          ATRTrailingStopLoss(atrMultiplier)
        }
        "pricebelowema" ->
          PriceBelowEmaExit(
            emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10,
          )
        "priceBelowEmaForDays" -> {
          val emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10
          val consecutiveDays = (config.parameters["consecutiveDays"] as? Number)?.toInt() ?: 3
          logger.info("  -> PriceBelowEmaForDaysExit(emaPeriod=$emaPeriod, consecutiveDays=$consecutiveDays)")
          PriceBelowEmaForDaysExit(emaPeriod, consecutiveDays)
        }
        "belowpreviousdaylow" -> BelowPreviousDayLowExit()
        "heatmapthreshold" -> HeatmapThresholdExit()
        "heatmapdeclining" -> HeatmapDecliningExit()
        "marketandsectordowntrend" -> MarketAndSectorDowntrendExit()
        "beforeearnings" -> {
          val daysBeforeEarnings = (config.parameters["daysBeforeEarnings"] as? Number)?.toInt() ?: 1
          logger.info("  -> BeforeEarningsExit(daysBeforeEarnings=$daysBeforeEarnings)")
          BeforeEarningsExit(daysBeforeEarnings)
        }
        else -> throw IllegalArgumentException("Unknown exit condition type: ${config.type}")
      }
    return condition
  }
}
