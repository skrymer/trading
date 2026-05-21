package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when the stock has a standing Ovtlyr SELL signal as of the quote
 * bar — i.e. the most recent Ovtlyr call on or before that date is a SELL. Because Ovtlyr signals
 * are sparse events, this holds on every bar between a SELL call and the next BUY, not just the
 * call day itself.
 */
@Component
class OvtlyrSellSignalCondition : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = stock.currentOvtlyrSignal(quote.date) == OvtlyrSignalType.SELL

  override fun evaluateWithDetails(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val current = stock.currentOvtlyrSignal(quote.date)
    val passed = current == OvtlyrSignalType.SELL
    return ConditionEvaluationResult(
      conditionType = "OvtlyrSellSignalCondition",
      description = description(),
      passed = passed,
      actualValue = current?.name ?: "NONE",
      threshold = "SELL",
      message =
        if (passed) {
          "Ovtlyr signal is SELL"
        } else {
          "Ovtlyr signal is ${current?.name ?: "absent"} — no standing sell"
        },
    )
  }

  override fun exitReason(): String = "Ovtlyr sell signal"

  override fun description(): String = "Stock has a current Ovtlyr sell signal"

  override fun getMetadata() =
    ConditionMetadata(
      type = "ovtlyrSellSignal",
      displayName = "Ovtlyr Sell Signal",
      description = "Exit when the stock has a current Ovtlyr sell signal (most recent Ovtlyr call is a SELL)",
      parameters = emptyList(),
      category = "Ovtlyr",
    )

  override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this
}
