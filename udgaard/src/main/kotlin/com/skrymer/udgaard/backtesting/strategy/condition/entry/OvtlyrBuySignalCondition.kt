package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that passes when the stock has a standing Ovtlyr BUY signal as of the quote
 * bar — i.e. the most recent Ovtlyr call on or before that date is a BUY. Because Ovtlyr signals
 * are sparse events, this holds on every bar between a BUY call and the next SELL, not just the
 * call day itself.
 */
@Component
class OvtlyrBuySignalCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = stock.currentOvtlyrSignal(quote.date) == OvtlyrSignalType.BUY

  override fun description(): String = "Stock has a current Ovtlyr buy signal"

  override fun getMetadata() =
    ConditionMetadata(
      type = "ovtlyrBuySignal",
      displayName = "Ovtlyr Buy Signal",
      description = "Stock has a current Ovtlyr buy signal (most recent Ovtlyr call is a BUY)",
      parameters = emptyList(),
      category = "Ovtlyr",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val current = stock.currentOvtlyrSignal(quote.date)
    val passed = current == OvtlyrSignalType.BUY
    return ConditionEvaluationResult(
      conditionType = "OvtlyrBuySignalCondition",
      description = description(),
      passed = passed,
      actualValue = current?.name ?: "NONE",
      threshold = "BUY",
      message =
        if (passed) {
          "Ovtlyr signal is BUY"
        } else {
          "Ovtlyr signal is ${current?.name ?: "absent"} — no standing buy"
        },
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this
}
