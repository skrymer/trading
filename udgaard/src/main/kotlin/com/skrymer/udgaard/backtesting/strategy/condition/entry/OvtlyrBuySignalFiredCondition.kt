package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that passes only on the exact bar an Ovtlyr BUY signal fired — the call-day
 * event. Unlike [OvtlyrBuySignalCondition], which holds on every bar of a standing BUY, this
 * fires once per BUY call, so it models "enter on the buy signal" rather than "enter whenever
 * the buy signal stands".
 */
@Component
class OvtlyrBuySignalFiredCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = stock.ovtlyrSignalOn(quote.date) == OvtlyrSignalType.BUY

  override fun description(): String = "Stock has an Ovtlyr buy signal that fired on this bar"

  override fun getMetadata() =
    ConditionMetadata(
      type = "ovtlyrBuySignalFired",
      displayName = "Ovtlyr Buy Signal Fired",
      description = "Stock has an Ovtlyr buy signal that fired on this exact bar (the call day, not the standing state)",
      parameters = emptyList(),
      category = "Ovtlyr",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val event = stock.ovtlyrSignalOn(quote.date)
    val passed = event == OvtlyrSignalType.BUY
    return ConditionEvaluationResult(
      conditionType = "OvtlyrBuySignalFiredCondition",
      description = description(),
      passed = passed,
      actualValue = event?.name ?: "NONE",
      threshold = "BUY",
      message =
        if (passed) {
          "Ovtlyr BUY signal fired on ${quote.date}"
        } else {
          "No Ovtlyr BUY call on ${quote.date} (event: ${event?.name ?: "none"})"
        },
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this
}
