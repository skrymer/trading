package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.strategy.condition.LogicalOperator
import com.skrymer.udgaard.model.strategy.condition.TradingCondition
import com.skrymer.udgaard.model.strategy.condition.entry.*
import com.skrymer.udgaard.model.strategy.condition.exit.*

/**
 * DSL builder for creating entry strategies using composition.
 */
class EntryStrategyBuilder {
    private val conditions = mutableListOf<TradingCondition>()
    private var operator: LogicalOperator = LogicalOperator.AND
    private var description: String? = null

    fun uptrend() = apply {
        conditions.add(UptrendCondition())
    }

    fun buySignal(currentOnly: Boolean = false) = apply {
        conditions.add(BuySignalCondition(currentOnly))
    }

    fun heatmap(threshold: Int = 70) = apply {
        conditions.add(HeatmapCondition(threshold.toDouble()))
    }

    fun priceAbove(emaPeriod: Int) = apply {
        conditions.add(PriceAboveEmaCondition(emaPeriod))
    }

    fun inValueZone(atrMultiplier: Double = 2.0) = apply {
        conditions.add(ValueZoneCondition(atrMultiplier))
    }

    // Market/SPY conditions
    fun spyBuySignal() = apply {
        conditions.add(SpyBuySignalCondition())
    }

    fun spyUptrend() = apply {
        conditions.add(SpyUptrendCondition())
    }

    fun marketUptrend() = apply {
        conditions.add(MarketUptrendCondition())
    }

    fun spyHeatmap(threshold: Int = 70) = apply {
        conditions.add(SpyHeatmapThresholdCondition(threshold.toDouble()))
    }

    fun spyHeatmapRising() = apply {
        conditions.add(SpyHeatmapRisingCondition())
    }

    // Sector conditions
    fun sectorUptrend() = apply {
        conditions.add(SectorUptrendCondition())
    }

    fun sectorHeatmapRising() = apply {
        conditions.add(SectorHeatmapRisingCondition())
    }

    fun sectorHeatmap(threshold: Int = 70) = apply {
        conditions.add(SectorHeatmapThresholdCondition(threshold.toDouble()))
    }

    fun donkeyChannel() = apply {
        conditions.add(DonkeyChannelCondition())
    }

    fun sectorHeatmapGreaterThanSpy() = apply {
        conditions.add(SectorHeatmapGreaterThanSpyCondition())
    }

    // Stock conditions
    fun stockHeatmapRising() = apply {
        conditions.add(StockHeatmapRisingCondition())
    }

    fun priceAbovePreviousLow() = apply {
        conditions.add(PriceAbovePreviousLowCondition())
    }

    fun notInOrderBlock(ageInDays: Int = 120) = apply {
        conditions.add(NotInOrderBlockCondition(ageInDays))
    }

    fun belowOrderBlock(percentBelow: Double = 2.0, ageInDays: Int = 30) = apply {
        conditions.add(BelowOrderBlockCondition(percentBelow, ageInDays))
    }

    fun withOperator(op: LogicalOperator) = apply {
        operator = op
    }

    fun withDescription(desc: String) = apply {
        description = desc
    }

    fun build(): CompositeEntryStrategy {
        return CompositeEntryStrategy(conditions, operator, description)
    }
}

/**
 * DSL builder for creating exit strategies using composition.
 */
class ExitStrategyBuilder {
    private val conditions = mutableListOf<ExitCondition>()
    private var operator: LogicalOperator = LogicalOperator.OR
    private var description: String? = null

    fun sellSignal() = apply {
        conditions.add(SellSignalExit())
    }

    fun emaCross(fastEma: Int = 10, slowEma: Int = 20) = apply {
        conditions.add(EmaCrossExit(fastEma, slowEma))
    }

    fun profitTarget(atrMultiplier: Double = 3.5, emaPeriod: Int = 20) = apply {
        conditions.add(ProfitTargetExit(atrMultiplier, emaPeriod))
    }

    fun orderBlock(ageInDays: Int = 120) = apply {
        conditions.add(OrderBlockExit(ageInDays))
    }

    fun stopLoss(atrMultiplier: Double = 2.0) = apply {
        conditions.add(StopLossExit(atrMultiplier))
    }

    fun trailingStopLoss(atrMultiplier: Double = 2.7) = apply {
        conditions.add(ATRTrailingStopLoss(atrMultiplier))
    }

    fun priceBelowEma(emaPeriod: Int = 10) = apply {
        conditions.add(PriceBelowEmaExit(emaPeriod))
    }

    fun priceBelowEmaForDays(emaPeriod: Int = 10, consecutiveDays: Int = 3) = apply {
        conditions.add(PriceBelowEmaForDaysExit(emaPeriod, consecutiveDays))
    }

    fun belowPreviousDayLow() = apply {
        conditions.add(BelowPreviousDayLowExit())
    }

    fun heatmapThreshold() = apply {
        conditions.add(HeatmapThresholdExit())
    }

    fun heatmapDeclining() = apply {
        conditions.add(HeatmapDecliningExit())
    }

    fun marketAndSectorDowntrend() = apply {
        conditions.add(MarketAndSectorDowntrendExit())
    }

    fun exitBeforeEarnings(days: Int = 1) = apply {
        conditions.add(BeforeEarningsExit(days))
    }

    fun withOperator(op: LogicalOperator) = apply {
        operator = op
    }

    fun withDescription(desc: String) = apply {
        description = desc
    }

    fun build(): CompositeExitStrategy {
        return CompositeExitStrategy(conditions, operator, description)
    }
}

/**
 * DSL function for creating entry strategies.
 */
fun entryStrategy(block: EntryStrategyBuilder.() -> Unit): CompositeEntryStrategy {
    return EntryStrategyBuilder().apply(block).build()
}

/**
 * DSL function for creating exit strategies.
 */
fun exitStrategy(block: ExitStrategyBuilder.() -> Unit): CompositeExitStrategy {
    return ExitStrategyBuilder().apply(block).build()
}
