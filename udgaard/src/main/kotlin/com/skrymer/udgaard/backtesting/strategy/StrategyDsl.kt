package com.skrymer.udgaard.backtesting.strategy

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
import com.skrymer.udgaard.data.model.OrderBlockSensitivity

/**
 * DSL builder for creating entry strategies using composition.
 */
class EntryStrategyBuilder {
  private val conditions = mutableListOf<EntryCondition>()
  private var operator: LogicalOperator = LogicalOperator.AND
  private var description: String? = null

  fun uptrend() =
    apply {
      conditions.add(UptrendCondition())
    }

  fun priceAbove(emaPeriod: Int) =
    apply {
      conditions.add(PriceAboveEmaCondition(emaPeriod))
    }

  fun emaAlignment(
    fastEmaPeriod: Int = 10,
    slowEmaPeriod: Int = 20,
  ) = apply {
    conditions.add(EmaAlignmentCondition(fastEmaPeriod, slowEmaPeriod))
  }

  fun inValueZone(atrMultiplier: Double = 2.0, emaPeriod: Int) =
    apply {
      conditions.add(ValueZoneCondition(atrMultiplier, emaPeriod))
    }

  fun minimumPrice(dollars: Double = 10.0) =
    apply {
      conditions.add(MinimumPriceCondition(dollars))
    }

  // Market conditions
  fun marketUptrend() =
    apply {
      conditions.add(MarketUptrendCondition())
    }

  fun spyPriceUptrend() =
    apply {
      conditions.add(SpyPriceUptrendCondition())
    }

  fun marketBreadthAbove(threshold: Double) =
    apply {
      conditions.add(MarketBreadthAboveCondition(threshold))
    }

  fun marketBreadthEmaAlignment(vararg emaPeriods: Int) =
    apply {
      val periods = if (emaPeriods.isEmpty()) listOf(5, 10, 20) else emaPeriods.toList()
      conditions.add(MarketBreadthEmaAlignmentCondition(periods))
    }

  fun marketBreadthRecovering() =
    apply {
      conditions.add(MarketBreadthRecoveringCondition())
    }

  fun marketBreadthNearDonchianLow(percentile: Double = 0.10) =
    apply {
      conditions.add(MarketBreadthNearDonchianLowCondition(percentile))
    }

  fun marketBreadthTrending(minWidth: Double = 20.0) =
    apply {
      conditions.add(MarketBreadthTrendingCondition(minWidth))
    }

  // Sector conditions
  fun sectorUptrend() =
    apply {
      conditions.add(SectorUptrendCondition())
    }

  fun sectorBreadthGreaterThanMarket() =
    apply {
      conditions.add(SectorBreadthGreaterThanMarketCondition())
    }

  fun sectorBreadthAbove(threshold: Double = 50.0) =
    apply {
      conditions.add(SectorBreadthAboveCondition(threshold))
    }

  fun sectorBreadthEmaAlignment() =
    apply {
      conditions.add(SectorBreadthEmaAlignmentCondition())
    }

  fun sectorBreadthAccelerating(threshold: Double = 5.0) =
    apply {
      conditions.add(SectorBreadthAcceleratingCondition(threshold))
    }

  // Stock conditions
  fun priceAbovePreviousLow() =
    apply {
      conditions.add(PriceAbovePreviousLowCondition())
    }

  fun notInOrderBlock(ageInDays: Int = 120) =
    apply {
      conditions.add(NotInOrderBlockCondition(ageInDays))
    }

  fun belowOrderBlock(
    percentBelow: Double = 2.0,
    ageInDays: Int = 30,
    sensitivity: OrderBlockSensitivity? = null,
  ) = apply {
    conditions.add(BelowOrderBlockCondition(percentBelow, ageInDays, sensitivity))
  }

  fun emaBullishCross(
    fastEma: Int = 10,
    slowEma: Int = 20,
  ) = apply {
    conditions.add(EmaBullishCrossCondition(fastEma, slowEma))
  }

  fun atrExpanding(
    minPercentile: Double = 30.0,
    maxPercentile: Double = 70.0,
  ) = apply {
    conditions.add(ATRExpandingCondition(minPercentile, maxPercentile))
  }

  fun adxRange(
    minADX: Double = 20.0,
    maxADX: Double = 50.0,
  ) = apply {
    conditions.add(ADXRangeCondition(minADX, maxADX))
  }

  fun volumeAboveAverage(
    multiplier: Double = 1.3,
    lookbackDays: Int = 20,
  ) = apply {
    conditions.add(VolumeAboveAverageCondition(multiplier, lookbackDays))
  }

  fun noEarningsWithinDays(days: Int = 7) =
    apply {
      conditions.add(NoEarningsWithinDaysCondition(days))
    }

  fun daysSinceEarnings(days: Int = 5) =
    apply {
      conditions.add(DaysSinceEarningsCondition(days))
    }

  fun consecutiveHigherHighsInValueZone(
    consecutiveDays: Int = 3,
    atrMultiplier: Double = 2.0,
    emaPeriod: Int = 20,
  ) = apply {
    conditions.add(ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays, atrMultiplier, emaPeriod))
  }

  fun aboveBearishOrderBlock(
    consecutiveDays: Int = 3,
    ageInDays: Int = 30,
    proximityPercent: Double = 2.0,
    sensitivity: OrderBlockSensitivity? = null,
  ) = apply {
    conditions.add(AboveBearishOrderBlockCondition(consecutiveDays, ageInDays, proximityPercent, sensitivity))
  }

  fun bullishCandle(minPercent: Double = 0.5) =
    apply {
      conditions.add(BullishCandleCondition(minPercent))
    }

  fun emaSpread(
    fastEmaPeriod: Int = 10,
    slowEmaPeriod: Int = 20,
    minSpreadPercent: Double = 1.0,
  ) = apply {
    conditions.add(EmaSpreadCondition(fastEmaPeriod, slowEmaPeriod, minSpreadPercent))
  }

  fun priceNearDonchianHigh(maxDistancePercent: Double = 1.5) =
    apply {
      conditions.add(PriceNearDonchianHighCondition(maxDistancePercent))
    }

  fun withOperator(op: LogicalOperator) =
    apply {
      operator = op
    }

  fun withDescription(desc: String) =
    apply {
      description = desc
    }

  fun build(): CompositeEntryStrategy = CompositeEntryStrategy(conditions, operator, description)
}

/**
 * DSL builder for creating exit strategies using composition.
 */
class ExitStrategyBuilder {
  private val conditions = mutableListOf<ExitCondition>()
  private var operator: LogicalOperator = LogicalOperator.OR
  private var description: String? = null

  fun emaCross(
    fastEma: Int = 10,
    slowEma: Int = 20,
  ) = apply {
    conditions.add(EmaCrossExit(fastEma, slowEma))
  }

  fun profitTarget(
    atrMultiplier: Double = 3.5,
    emaPeriod: Int = 20,
  ) = apply {
    conditions.add(ProfitTargetExit(atrMultiplier, emaPeriod))
  }

  fun bearishOrderBlock(
    ageInDays: Int = 120,
    useHighPrice: Boolean = false,
    sensitivity: OrderBlockSensitivity? = null,
  ) = apply {
    conditions.add(BearishOrderBlockExit(ageInDays, useHighPrice, sensitivity))
  }

  fun stopLoss(atrMultiplier: Double = 2.0) =
    apply {
      conditions.add(StopLossExit(atrMultiplier))
    }

  fun trailingStopLoss(atrMultiplier: Double = 2.7) =
    apply {
      conditions.add(ATRTrailingStopLoss(atrMultiplier))
    }

  fun priceBelowEma(emaPeriod: Int = 10) =
    apply {
      conditions.add(PriceBelowEmaExit(emaPeriod))
    }

  fun priceBelowEmaMinusAtr(
    emaPeriod: Int = 5,
    atrMultiplier: Double = 0.5,
  ) = apply {
    conditions.add(PriceBelowEmaMinusAtrExit(emaPeriod, atrMultiplier))
  }

  fun priceBelowEmaForDays(
    emaPeriod: Int = 10,
    consecutiveDays: Int = 3,
  ) = apply {
    conditions.add(PriceBelowEmaForDaysExit(emaPeriod, consecutiveDays))
  }

  fun belowPreviousDayLow() =
    apply {
      conditions.add(BelowPreviousDayLowExit())
    }

  fun marketAndSectorDowntrend() =
    apply {
      conditions.add(MarketAndSectorDowntrendExit())
    }

  fun marketBreadthDeteriorating() =
    apply {
      conditions.add(MarketBreadthDeterioratingExit())
    }

  fun sectorBreadthBelow(threshold: Double = 30.0) =
    apply {
      conditions.add(SectorBreadthBelowExit(threshold))
    }

  fun exitBeforeEarnings(days: Int = 1) =
    apply {
      conditions.add(BeforeEarningsExit(days))
    }

  fun withOperator(op: LogicalOperator) =
    apply {
      operator = op
    }

  fun withDescription(desc: String) =
    apply {
      description = desc
    }

  fun build(): CompositeExitStrategy = CompositeExitStrategy(conditions, operator, description)
}

/**
 * DSL function for creating entry strategies.
 */
fun entryStrategy(block: EntryStrategyBuilder.() -> Unit): CompositeEntryStrategy = EntryStrategyBuilder().apply(block).build()

/**
 * DSL function for creating exit strategies.
 */
fun exitStrategy(block: ExitStrategyBuilder.() -> Unit): CompositeExitStrategy = ExitStrategyBuilder().apply(block).build()
