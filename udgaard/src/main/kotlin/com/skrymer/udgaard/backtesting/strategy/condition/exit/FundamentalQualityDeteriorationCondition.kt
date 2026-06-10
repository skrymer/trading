package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exits when the stock's gross-profitability quality percentile falls below a threshold — the hold-leg
 * counterpart to the quality entry gate, hysteretic by design (exit below 60 vs enter at 80, so a name
 * is held while it remains a market-quality leader). A null percentile (the rank dropped out — fewer
 * than four trailing filings, a degenerate denominator, or the date is too thin) is treated as
 * deterioration and exits: a position whose quality reading we can no longer compute is no longer a
 * proven quality holding.
 *
 * @param exitBelowPercentile exit when `qualityPercentile` is strictly below this, 0–100 (default 60.0)
 */
@Component
class FundamentalQualityDeteriorationCondition(
  private val exitBelowPercentile: Double = 60.0,
) : ExitCondition {
  init {
    require(exitBelowPercentile in 0.0..100.0) { "exitBelowPercentile must be in 0..100, was $exitBelowPercentile" }
  }

  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean {
    val percentile = quote.qualityPercentile ?: return true
    return percentile < exitBelowPercentile
  }

  override fun exitReason(): String = "Quality percentile < ${"%.0f".format(exitBelowPercentile)}"

  override fun description(): String = "Quality deterioration (percentile < ${"%.0f".format(exitBelowPercentile)})"

  override fun getMetadata() =
    ConditionMetadata(
      type = "fundamentalQualityDeterioration",
      displayName = "Fundamental Quality Deterioration",
      description = "Exit when the gross-profitability percentile falls below the threshold (null percentile exits)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "exitBelowPercentile",
            displayName = "Exit Below Percentile",
            type = "number",
            defaultValue = exitBelowPercentile,
            min = 0.0,
            max = 100.0,
          ),
        ),
      category = "Signal",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val percentile = quote.qualityPercentile
    val triggered = shouldExit(stock, entryQuote, quote)
    val verdict = if (triggered) "TRIGGER" else "hold"
    val message =
      if (percentile == null) {
        "Quality percentile unavailable — deterioration, $verdict"
      } else {
        "Quality %.1f percentile vs floor %.0f — %s".format(percentile, exitBelowPercentile, verdict)
      }

    return ConditionEvaluationResult(
      conditionType = "FundamentalQualityDeteriorationCondition",
      description = description(),
      passed = triggered,
      actualValue = percentile?.let { "${"%.1f".format(it)} percentile" },
      threshold = "< ${"%.0f".format(exitBelowPercentile)}",
      message = message,
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): ExitCondition =
    FundamentalQualityDeteriorationCondition(
      exitBelowPercentile = (parameters["exitBelowPercentile"] as? Number)?.toDouble() ?: exitBelowPercentile,
    )
}
