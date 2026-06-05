package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.intOr
import com.skrymer.udgaard.backtesting.service.numberOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that requires trading volume to have dried up: the average volume over the most
 * recent `dryupWindow` bars is below `dryupRatio` times the average volume over the longer
 * `baseWindow` bars. Shrinking participation as a base matures is a precondition for a clean
 * breakout — supply has been absorbed.
 *
 * Window semantics (S1): both windows are the trailing-N bars up to and **including** the current
 * bar (`quote.date`). Fail-closed when fewer than `baseWindow` bars of history are available, or
 * when the base-window average volume is zero (no volume to compare against).
 *
 * @param dryupWindow number of recent bars whose average volume must have dried up (default 10)
 * @param baseWindow longer comparison window for the baseline average volume (default 50)
 * @param dryupRatio recent average must be below this fraction of the base average (default 0.7)
 */
@Component
class VolumeDryUpCondition(
  private val dryupWindow: Int = 10,
  private val baseWindow: Int = 50,
  private val dryupRatio: Double = 0.7,
) : EntryCondition {
  init {
    require(dryupWindow in 1..baseWindow) { "dryupWindow must be in 1..baseWindow, was $dryupWindow (baseWindow=$baseWindow)" }
    require(dryupRatio > 0.0) { "dryupRatio must be positive, was $dryupRatio" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val win = stock.quotesInRange(quote.date.minusDays(LOOKBACK_CALENDAR_DAYS), quote.date)
    val barCount = win.size
    if (barCount < baseWindow) return false

    val recent = win.subList(barCount - dryupWindow, barCount)
    val base = win.subList(barCount - baseWindow, barCount)
    val recentAvg = recent.sumOf { it.volume }.toDouble() / recent.size
    val baseAvg = base.sumOf { it.volume }.toDouble() / base.size
    return baseAvg > 0.0 && recentAvg < dryupRatio * baseAvg
  }

  override fun description(): String =
    "Volume dry-up (recent $dryupWindow-bar avg < ${"%.2f".format(dryupRatio)}× base $baseWindow-bar avg)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "volumeDryUp",
      displayName = "Volume Dry-Up",
      description = "Recent average volume has contracted below a fraction of the longer base-window average",
      parameters =
        listOf(
          ParameterMetadata(
            name = "dryupWindow",
            displayName = "Dry-Up Window",
            type = "number",
            defaultValue = dryupWindow,
            min = 1,
            max = 60,
          ),
          ParameterMetadata(
            name = "baseWindow",
            displayName = "Base Window",
            type = "number",
            defaultValue = baseWindow,
            min = 5,
            max = 200,
          ),
          ParameterMetadata(
            name = "dryupRatio",
            displayName = "Dry-Up Ratio",
            type = "number",
            defaultValue = dryupRatio,
            min = 0.1,
            max = 1.0,
          ),
        ),
      category = "Volume",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val win = stock.quotesInRange(quote.date.minusDays(LOOKBACK_CALENDAR_DAYS), quote.date)
    val barCount = win.size
    if (barCount < baseWindow) {
      return failedResult("Insufficient data ($barCount/$baseWindow bars)")
    }

    val recent = win.subList(barCount - dryupWindow, barCount)
    val base = win.subList(barCount - baseWindow, barCount)
    val recentAvg = recent.sumOf { it.volume }.toDouble() / recent.size
    val baseAvg = base.sumOf { it.volume }.toDouble() / base.size
    if (baseAvg <= 0.0) {
      return failedResult("Base-window average volume is zero")
    }

    val ratio = recentAvg / baseAvg
    val passed = recentAvg < dryupRatio * baseAvg
    val mark = if (passed) "✓" else "✗"
    return ConditionEvaluationResult(
      conditionType = "VolumeDryUpCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.2f".format(ratio)}× base",
      threshold = "< ${"%.2f".format(dryupRatio)}× base",
      message = "Recent volume ${"%.2f".format(ratio)}× base (needs < ${"%.2f".format(dryupRatio)}×) $mark",
    )
  }

  private fun failedResult(reason: String): ConditionEvaluationResult =
    ConditionEvaluationResult(
      conditionType = "VolumeDryUpCondition",
      description = description(),
      passed = false,
      actualValue = null,
      threshold = null,
      message = "$reason ✗",
    )

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    VolumeDryUpCondition(
      dryupWindow = parameters.intOr("dryupWindow", dryupWindow),
      baseWindow = parameters.intOr("baseWindow", baseWindow),
      dryupRatio = parameters.numberOr("dryupRatio", dryupRatio),
    )

  private companion object {
    const val LOOKBACK_CALENDAR_DAYS = 400L
  }
}
