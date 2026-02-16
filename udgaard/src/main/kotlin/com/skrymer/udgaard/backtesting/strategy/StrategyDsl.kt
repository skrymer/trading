package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ADXRangeCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ATRExpandingCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.AboveBearishOrderBlockCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.BelowOrderBlockCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ConsecutiveHigherHighsInValueZoneCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.DaysSinceEarningsCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EmaAlignmentCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EmaBullishCrossCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketBreadthAboveCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MarketUptrendCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.MinimumPriceCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.NoEarningsWithinDaysCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.NotInOrderBlockCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceAboveEmaCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.PriceAbovePreviousLowCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorBreadthGreaterThanSpyCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.SectorUptrendCondition
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
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaForDaysExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.PriceBelowEmaMinusAtrExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ProfitTargetExit
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

  fun marketBreadthAbove(threshold: Double) =
    apply {
      conditions.add(MarketBreadthAboveCondition(threshold))
    }

  // Sector conditions
  fun sectorUptrend() =
    apply {
      conditions.add(SectorUptrendCondition())
    }

  fun sectorBreadthGreaterThanSpy() =
    apply {
      conditions.add(SectorBreadthGreaterThanSpyCondition())
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

  @Deprecated("Use bearishOrderBlock() instead", ReplaceWith("bearishOrderBlock(ageInDays)"))
  fun orderBlock(ageInDays: Int = 120) = bearishOrderBlock(ageInDays)

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
