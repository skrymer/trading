package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.intOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks the close is above a pre-computed simple moving average. The trend leg
 * for the quality premise wants `price > SMA200` specifically (Minervini-style long-term trend), which
 * `PriceAboveEmaCondition` cannot express — it has no EMA-200 path that maps cleanly to SMA, and EMA and
 * SMA differ materially at period 200 (see #149). Reads the SMA off the quote via the shared
 * [movingAverage] accessor; a null value (insufficient history) fails the gate.
 *
 * @param period SMA period — one of the persisted SMA windows (50 / 150 / 200), default 200.
 */
@Component
class PriceAboveSmaCondition(
  private val period: Int = 200,
) : EntryCondition {
  init {
    require(period in supportedMaPeriods("SMA")) {
      "SMA period must be one of ${supportedMaPeriods("SMA")}, was $period"
    }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val sma = quote.movingAverage("SMA", period) ?: return false
    return quote.closePrice > sma
  }

  override fun description(): String = "Price > SMA$period"

  override fun getMetadata() =
    ConditionMetadata(
      type = "priceAboveSma",
      displayName = "Price Above SMA",
      description = "Price is above the specified simple moving average",
      parameters =
        listOf(
          ParameterMetadata(
            name = "period",
            displayName = "SMA Period",
            type = "number",
            defaultValue = period,
            options = listOf("50", "150", "200"),
          ),
        ),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val sma = quote.movingAverage("SMA", period)
      ?: return ConditionEvaluationResult(
        conditionType = "PriceAboveSmaCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "SMA$period not available (insufficient history) ✗",
      )

    val price = quote.closePrice
    val passed = price > sma
    val mark = if (passed) "✓" else "✗"
    val relation = if (passed) ">" else "≤"

    return ConditionEvaluationResult(
      conditionType = "PriceAboveSmaCondition",
      description = description(),
      passed = passed,
      actualValue = "Price: ${"%.2f".format(price)}, SMA$period: ${"%.2f".format(sma)}",
      threshold = "Price > SMA$period",
      message = "Price ${"%.2f".format(price)} $relation SMA$period ${"%.2f".format(sma)} $mark",
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    PriceAboveSmaCondition(
      period = parameters.intOr("period", period),
    )
}
