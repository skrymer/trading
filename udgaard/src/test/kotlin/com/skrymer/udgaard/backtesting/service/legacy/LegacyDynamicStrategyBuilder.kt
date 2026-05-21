// Transient parity fixture for the ConditionRegistry deepening (candidate #2).
// DELETE WITH the parity-test cleanup commit once ConditionRegistryParityTest is green.
// See docs/architecture/dynamic-strategy-builder-deepening.md.
//
// This file is a verbatim copy of the pre-refactor DynamicStrategyBuilder's per-condition
// dispatch tables. It is NOT a Spring component — it's instantiated directly by the
// parity test. Do not import from production code.
package com.skrymer.udgaard.backtesting.service.legacy

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
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
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthIncreasingCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthNearDonchianLowCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthRecoveringCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthTrendingCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketUptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MinimumHistoryDaysCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MinimumPriceCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.NoEarningsWithinDaysCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.NotInOrderBlockCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.OrderBlockBreakoutCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.OrderBlockRejectionCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.OvtlyrBuySignalCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceAboveEmaCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceAbovePreviousLowCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceNearDonchianHighCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthAboveCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthAcceleratingCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthEmaAlignmentCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthGreaterThanMarketCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthIncreasingCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorUptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SpyPriceUptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.UptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ValueZoneCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.VolatilityContractedCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.VolumeAboveAverageCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ATRTrailingStopLoss
import com.skrymer.udgaard.backtesting.strategy.condition.exit.BearishOrderBlockExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.BeforeEarningsExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.BelowPreviousDayLowExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.EmaCrossExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.GapAndCrapExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.MarketAndSectorDowntrendExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.MarketBreadthDeterioratingExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.OvtlyrSellSignalCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaForDaysExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaMinusAtrExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ProfitTargetExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.SectorBreadthBelowExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.StagnationExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.StopLossExit

class LegacyDynamicStrategyBuilder {
  @Suppress("LongMethod", "CyclomaticComplexMethod")
  fun buildEntryCondition(config: ConditionConfig): EntryCondition =
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
      "minimumhistorydays" ->
        MinimumHistoryDaysCondition(
          days = (config.parameters["days"] as? Number)?.toInt() ?: 180,
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
      "marketbreadthincreasing" ->
        MarketBreadthIncreasingCondition(
          days = (config.parameters["days"] as? Number)?.toInt() ?: 3,
        )
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
      "sectorbreadthincreasing" ->
        SectorBreadthIncreasingCondition(
          days = (config.parameters["days"] as? Number)?.toInt() ?: 3,
          sectorSymbol = (config.parameters["sectorSymbol"] as? String) ?: "XLK",
        )
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
      "orderblockbreakout" ->
        OrderBlockBreakoutCondition(
          consecutiveDays = (config.parameters["consecutiveDays"] as? Number)?.toInt() ?: 1,
          maxDaysSinceBreakout = (config.parameters["maxDaysSinceBreakout"] as? Number)?.toInt() ?: 3,
          ageInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 0,
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
      "volatilitycontracted" ->
        VolatilityContractedCondition(
          lookbackDays = (config.parameters["lookbackDays"] as? Number)?.toInt() ?: 10,
          maxAtrMultiple = (config.parameters["maxAtrMultiple"] as? Number)?.toDouble() ?: 2.5,
        )
      "ovtlyrbuysignal" -> OvtlyrBuySignalCondition()
      else -> throw IllegalArgumentException("Unknown entry condition type: ${config.type}")
    }

  @Suppress("LongMethod", "CyclomaticComplexMethod")
  fun buildExitCondition(config: ConditionConfig): ExitCondition =
    when (config.type.lowercase()) {
      "emacross" ->
        EmaCrossExit(
          fastEma = (config.parameters["fastEma"] as? Number)?.toInt() ?: 10,
          slowEma = (config.parameters["slowEma"] as? Number)?.toInt() ?: 20,
        )
      "profittarget" ->
        ProfitTargetExit(
          atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 3.0,
          emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 20,
        )
      "bearishorderblock" ->
        BearishOrderBlockExit(
          orderBlockAgeInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 120,
        )
      "orderblock" -> // Deprecated alias dropped post-refactor; kept here so the parity test exercises the legacy mapping
        BearishOrderBlockExit(
          orderBlockAgeInDays = (config.parameters["ageInDays"] as? Number)?.toInt() ?: 120,
        )
      "stoploss" ->
        StopLossExit(
          atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.0,
        )
      "trailingstoploss" ->
        ATRTrailingStopLoss(
          atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 2.7,
        )
      "pricebelowema" ->
        PriceBelowEmaExit(
          emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10,
        )
      "pricebelowemaminusatr" ->
        PriceBelowEmaMinusAtrExit(
          emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 5,
          atrMultiplier = (config.parameters["atrMultiplier"] as? Number)?.toDouble() ?: 0.5,
        )
      "pricebelowemafordays" ->
        PriceBelowEmaForDaysExit(
          emaPeriod = (config.parameters["emaPeriod"] as? Number)?.toInt() ?: 10,
          consecutiveDays = (config.parameters["consecutiveDays"] as? Number)?.toInt() ?: 3,
        )
      "belowpreviousdaylow" -> BelowPreviousDayLowExit()
      "marketandsectordowntrend" -> MarketAndSectorDowntrendExit()
      "marketbreadthdeteriorating" -> MarketBreadthDeterioratingExit()
      "sectorbreadthbelow" ->
        SectorBreadthBelowExit(
          threshold = (config.parameters["threshold"] as? Number)?.toDouble() ?: 30.0,
        )
      "beforeearnings" ->
        BeforeEarningsExit(
          daysBeforeEarnings = (config.parameters["daysBeforeEarnings"] as? Number)?.toInt() ?: 1,
        )
      "stagnation" ->
        StagnationExit(
          thresholdPercent = (config.parameters["thresholdPercent"] as? Number)?.toDouble() ?: 3.0,
          windowDays = (config.parameters["windowDays"] as? Number)?.toInt() ?: 15,
        )
      "gapandcrap" ->
        GapAndCrapExit(
          gapPercent = (config.parameters["gapPercent"] as? Number)?.toDouble() ?: 5.0,
        )
      "ovtlyrsellsignal" -> OvtlyrSellSignalCondition()
      else -> throw IllegalArgumentException("Unknown exit condition type: ${config.type}")
    }
}
