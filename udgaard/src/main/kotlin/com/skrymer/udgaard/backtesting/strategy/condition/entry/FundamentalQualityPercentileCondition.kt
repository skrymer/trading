package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.numberOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that requires the stock's gross-profitability quality percentile to be at or above a
 * threshold — an absolute, market-wide quality gate that admits only the most profitable-per-asset
 * names (default ≥ 80 = top quintile of the market).
 *
 * Reads the pre-computed `qualityPercentile` field directly — a cross-sectional rank of
 * `grossProfit_TTM / totalAssets_asof` computed in Midgaard against the whole survivorship-free
 * universe (ADR 0019), so the condition needs no peer set at evaluation time. When the percentile is
 * unavailable (fewer than four quarterly filings, a degenerate denominator, a thin date, or before the
 * trust floor) the condition fails — a stock with no quality rank must not pass the gate.
 *
 * @param minPercentile minimum required percentile, 0–100 (default 80.0)
 */
@Component
class FundamentalQualityPercentileCondition(
  private val minPercentile: Double = 80.0,
) : EntryCondition {
  init {
    require(minPercentile in 0.0..100.0) { "minPercentile must be in 0..100, was $minPercentile" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val percentile = quote.qualityPercentile ?: return false
    return percentile >= minPercentile
  }

  override fun description(): String = "Quality percentile ≥ ${"%.0f".format(minPercentile)}"

  override fun getMetadata() =
    ConditionMetadata(
      type = "fundamentalQualityPercentile",
      displayName = "Fundamental Quality Percentile",
      description = "Stock's gross-profitability percentile vs the whole market is at or above the threshold",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minPercentile",
            displayName = "Min Percentile",
            type = "number",
            defaultValue = minPercentile,
            min = 0.0,
            max = 100.0,
          ),
        ),
      category = "Fundamentals",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val percentile = quote.qualityPercentile
      ?: return ConditionEvaluationResult(
        conditionType = "FundamentalQualityPercentileCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "Quality percentile not available (insufficient filings or thin universe) ✗",
      )

    val passed = percentile >= minPercentile
    val mark = if (passed) "✓" else "✗"

    return ConditionEvaluationResult(
      conditionType = "FundamentalQualityPercentileCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.1f".format(percentile)} percentile",
      threshold = "≥ ${"%.0f".format(minPercentile)}",
      message = "Quality ${"%.1f".format(percentile)} percentile (≥ ${"%.0f".format(minPercentile)}) $mark",
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    FundamentalQualityPercentileCondition(
      minPercentile = parameters.numberOr("minPercentile", minPercentile),
    )
}
