package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.CustomStrategyConfig
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.dto.StrategyConfig
import com.skrymer.udgaard.backtesting.strategy.CompositeEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.CompositeExitStrategy
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ADXRangeCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ATRExpandingCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.AboveBearishOrderBlockCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.BelowOrderBlockCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.BullishCandleCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ConsecutiveHigherHighsInValueZoneCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.DaysSinceEarningsCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EmaAlignmentCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EmaBullishCrossCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EmaSpreadCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthAboveCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthEmaAlignmentCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthNearDonchianLowCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthRecoveringCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthTrendingCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketUptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MinimumPriceCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.NoEarningsWithinDaysCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.NotInOrderBlockCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.OrderBlockRejectionCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceAboveEmaCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceAbovePreviousLowCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceNearDonchianHighCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthAboveCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthAcceleratingCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthEmaAlignmentCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthGreaterThanMarketCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorUptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SpyPriceUptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.UptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ValueZoneCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.VolumeAboveAverageCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ATRTrailingStopLoss
import com.skrymer.udgaard.backtesting.strategy.condition.exit.BearishOrderBlockExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.BeforeEarningsExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.BelowPreviousDayLowExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.EmaCrossExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.MarketAndSectorDowntrendExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.MarketBreadthDeterioratingExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaForDaysExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaMinusAtrExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ProfitTargetExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.SectorBreadthBelowExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.StopLossExit
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
      "priceaboveema" ->
        PriceAboveEmaCondition(
          emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10,
        )
      "valuezone" ->
        ValueZoneCondition(
          atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.0,
        )
      "marketuptrend" -> MarketUptrendCondition()
      "spypriceuptrend" -> SpyPriceUptrendCondition()
      "sectoruptrend" -> SectorUptrendCondition()
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
      "marketbreadthemaalignment" -> {
        val periodsStr = config.parameters["emaPeriods"] as? String
        val periods = periodsStr?.split(",")?.map { it.trim().toInt() } ?: listOf(5, 10, 20)
        MarketBreadthEmaAlignmentCondition(periods)
      }
      "marketbreadthrecovering" -> MarketBreadthRecoveringCondition()
      "marketbreadthneardonchianlow" ->
        MarketBreadthNearDonchianLowCondition(
          percentile = (config.parameters["percentile"] as? Number)?.toDouble() ?: 0.10,
        )
      "marketbreadthtrending" ->
        MarketBreadthTrendingCondition(
          minWidth = (config.parameters["minWidth"] as? Number)?.toDouble() ?: 20.0,
        )
      "sectorbreadthabove" ->
        SectorBreadthAboveCondition(
          threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 50.0,
        )
      "sectorbreadthemaalignment" -> SectorBreadthEmaAlignmentCondition()
      "sectorbreadthaccelerating" ->
        SectorBreadthAcceleratingCondition(
          threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 5.0,
        )
      "sectorbreadthgreaterthanmarket" -> SectorBreadthGreaterThanMarketCondition()
      "volumeaboveaverage" ->
        VolumeAboveAverageCondition(
          multiplier = (config.parameters["multiplier"] as? Number)?.toDouble() ?: 1.3,
          lookbackDays = (config.parameters["lookbackDays"] as? Number)?.toInt() ?: 20,
        )
      "noearningswithindays" ->
        NoEarningsWithinDaysCondition(
          days = (config.parameters["days"] as? Number)?.toInt() ?: 7,
        )
      "dayssinceearnings" ->
        DaysSinceEarningsCondition(
          days = (config.parameters["days"] as? Number)?.toInt() ?: 5,
        )
      "consecutivehigherhighsinvaluezone" ->
        ConsecutiveHigherHighsInValueZoneCondition(
          consecutiveDays = (config.parameters["consecutiveDays"] as? Number)?.toInt() ?: 3,
          atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.0,
          emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 20,
        )
      "abovebearishorderblock" ->
        AboveBearishOrderBlockCondition(
          consecutiveDays = (config.parameters["consecutiveDays"] as? Number)?.toInt() ?: 3,
          ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 30,
          proximityPercent = (config.parameters["proximityPercent"] as? Number)?.toDouble() ?: 2.0,
        )
      "bullishcandle" ->
        BullishCandleCondition(
          minPercent = (config.parameters["minPercent"] as? Number)?.toDouble() ?: 0.5,
        )
      "emaspread" ->
        EmaSpreadCondition(
          fastEmaPeriod = (config.parameters["fastEmaPeriod"] as? Number)?.toInt() ?: 10,
          slowEmaPeriod = (config.parameters["slowEmaPeriod"] as? Number)?.toInt() ?: 20,
          minSpreadPercent = (config.parameters["minSpreadPercent"] as? Number)?.toDouble() ?: 1.0,
        )
      "priceneardonchianhigh" ->
        PriceNearDonchianHighCondition(
          maxDistancePercent = (config.parameters["maxDistancePercent"] as? Number)?.toDouble() ?: 1.5,
        )
      else -> throw IllegalArgumentException("Unknown entry condition type: ${config.type}")
    }

  private fun buildExitCondition(config: ConditionConfig): ExitCondition {
    logger.info("Building exit condition: type=${config.type}, params=${config.parameters}")
    val condition =
      when (config.type.lowercase()) {
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
        "bearishorderblock" -> {
          val ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 120
          logger.info("  -> BearishOrderBlockExit(ageInDays=$ageInDays)")
          BearishOrderBlockExit(ageInDays)
        }
        "orderblock" -> {
          // Deprecated: kept for backward compatibility
          val ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 120
          logger.info("  -> BearishOrderBlockExit(ageInDays=$ageInDays) [deprecated: orderblock]")
          BearishOrderBlockExit(ageInDays)
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
        "pricebelowemaminusatr" ->
          PriceBelowEmaMinusAtrExit(
            emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 5,
            atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 0.5,
          )
        "pricebelowemafordays" -> {
          val emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10
          val consecutiveDays = (config.parameters["consecutiveDays"] as? Number)?.toInt() ?: 3
          logger.info("  -> PriceBelowEmaForDaysExit(emaPeriod=$emaPeriod, consecutiveDays=$consecutiveDays)")
          PriceBelowEmaForDaysExit(emaPeriod, consecutiveDays)
        }
        "belowpreviousdaylow" -> BelowPreviousDayLowExit()
        "marketandsectordowntrend" -> MarketAndSectorDowntrendExit()
        "marketbreadthdeteriorating" -> MarketBreadthDeterioratingExit()
        "sectorbreadthbelow" ->
          SectorBreadthBelowExit(
            threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 30.0,
          )
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
