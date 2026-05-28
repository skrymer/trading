package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exits when the unrealized gain from entry reaches `targetPct` percent.
 *
 * Computed as `(quote.closePrice - entryQuote.closePrice) / entryQuote.closePrice >= targetPct/100`.
 * Distinct from `ProfitTargetExit` (which exits when price is N ATRs above an EMA — a volatility-
 * scaled target, not a percentage target). This is the simple "+X% gain" target.
 *
 * Returns false if `entryQuote` is null or `entryQuote.closePrice` is non-positive.
 */
@Component
class PercentGainExit(
  private val targetPct: Double = 10.0,
) : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean {
    val e = entryQuote ?: return false
    if (e.closePrice <= 0.0) return false
    val gain = (quote.closePrice - e.closePrice) / e.closePrice
    return gain >= targetPct / 100.0
  }

  override fun exitReason(): String = "Unrealized gain >= $targetPct%"

  override fun description(): String = "Percent gain target ($targetPct%)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "percentGain",
      displayName = "Percent gain target",
      description = "Exit when (close - entry) / entry >= targetPct / 100. Simple percentage target " +
        "(distinct from ProfitTargetExit's price-above-EMA-plus-ATR formulation).",
      parameters = listOf(
        ParameterMetadata(
          name = "targetPct",
          displayName = "Target % gain",
          type = "number",
          defaultValue = targetPct,
          min = 0.1,
          max = 1000.0,
        ),
      ),
      category = "ProfitTaking",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val e = entryQuote
    val message =
      when {
        e == null -> "No entry quote — cannot evaluate"
        e.closePrice <= 0.0 -> "Entry close non-positive — cannot evaluate"
        else -> {
          val gainPct = (quote.closePrice - e.closePrice) / e.closePrice * 100.0
          val triggered = gainPct >= targetPct
          val verdict = if (triggered) "TRIGGER" else "hold"
          "Gain %.2f%% vs target %.2f%% — %s".format(gainPct, targetPct, verdict)
        }
      }
    return ConditionEvaluationResult(
      conditionType = "PercentGainExit",
      description = description(),
      passed = shouldExit(stock, entryQuote, quote),
      actualValue = entryQuote?.let { e ->
        if (e.closePrice > 0.0) {
          "%.2f%%".format((quote.closePrice - e.closePrice) / e.closePrice * 100.0)
        } else {
          null
        }
      },
      threshold = ">= $targetPct%",
      message = message,
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): ExitCondition =
    PercentGainExit(
      targetPct = (parameters["targetPct"] as? Number)?.toDouble() ?: targetPct,
    )
}
