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
 * Entry condition that requires price range to have contracted in steps: across three consecutive
 * `stepWindow`-bar sub-windows (oldest → middle → most recent), the normalized high-low range must
 * be strictly decreasing. Each sub-window's range is normalized by its mean close, so the check is
 * scale-free across price levels. Successive tightening is the structural signature of a maturing
 * base coiling toward a breakout.
 *
 * The window count is fixed at three (a tunable window count would be the classic
 * "N-of-M pullbacks" aliased-regime-sensitivity hazard); only the per-window width `stepWindow`
 * is configurable.
 *
 * Window semantics (S1): the three sub-windows are the trailing `3 * stepWindow` bars up to and
 * **including** the current bar (`quote.date`). Fail-closed when fewer than `3 * stepWindow` bars
 * of history are available. A sub-window with a non-positive mean close is treated as maximally
 * wide so it cannot spuriously satisfy the contraction.
 *
 * @param stepWindow number of bars in each of the three consecutive sub-windows (default 10)
 */
@Component
class NarrowingRangeCondition(
  private val stepWindow: Int = 10,
) : EntryCondition {
  init {
    require(stepWindow >= 1) { "stepWindow must be at least 1, was $stepWindow" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val win = stock.quotesInRange(quote.date.minusDays(LOOKBACK_CALENDAR_DAYS), quote.date)
    val barCount = win.size
    val stepSize = stepWindow
    if (barCount < WINDOW_COUNT * stepSize) return false

    val r1 = normalizedRange(win, barCount - stepSize, barCount)
    val r2 = normalizedRange(win, barCount - 2 * stepSize, barCount - stepSize)
    val r3 = normalizedRange(win, barCount - 3 * stepSize, barCount - 2 * stepSize)
    return r1 < r2 && r2 < r3
  }

  /** Normalized high-low range of `win[from, to)`: (maxHigh - minLow) / meanClose, scale-free. */
  private fun normalizedRange(
    win: List<StockQuote>,
    from: Int,
    to: Int,
  ): Double {
    val seg = win.subList(from, to)
    val meanClose = seg.sumOf { it.closePrice } / seg.size
    return if (meanClose <= 0.0) {
      Double.MAX_VALUE
    } else {
      (seg.maxOf { it.high } - seg.minOf { it.low }) / meanClose
    }
  }

  override fun description(): String = "Narrowing range (3 consecutive $stepWindow-bar windows strictly contracting)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "narrowingRange",
      displayName = "Narrowing Range",
      description = "Normalized high-low range strictly decreases across three consecutive sub-windows",
      parameters =
        listOf(
          ParameterMetadata(
            name = "stepWindow",
            displayName = "Step Window",
            type = "number",
            defaultValue = stepWindow,
            min = 1,
            max = 60,
          ),
        ),
      category = "Volatility",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val win = stock.quotesInRange(quote.date.minusDays(LOOKBACK_CALENDAR_DAYS), quote.date)
    val barCount = win.size
    val stepSize = stepWindow
    if (barCount < WINDOW_COUNT * stepSize) {
      return failedResult("Insufficient data ($barCount/${WINDOW_COUNT * stepSize} bars)")
    }

    val r1 = normalizedRange(win, barCount - stepSize, barCount)
    val r2 = normalizedRange(win, barCount - 2 * stepSize, barCount - stepSize)
    val r3 = normalizedRange(win, barCount - 3 * stepSize, barCount - 2 * stepSize)
    val passed = r1 < r2 && r2 < r3
    val mark = if (passed) "✓" else "✗"
    return ConditionEvaluationResult(
      conditionType = "NarrowingRangeCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.3f".format(r1)} < ${"%.3f".format(r2)} < ${"%.3f".format(r3)}",
      threshold = "strictly decreasing",
      message = "Normalized range r1 ${"%.3f".format(r1)}, r2 ${"%.3f".format(r2)}, r3 ${"%.3f".format(r3)} $mark",
    )
  }

  private fun failedResult(reason: String): ConditionEvaluationResult =
    ConditionEvaluationResult(
      conditionType = "NarrowingRangeCondition",
      description = description(),
      passed = false,
      actualValue = null,
      threshold = null,
      message = "$reason ✗",
    )

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    NarrowingRangeCondition(
      stepWindow = parameters.intOr("stepWindow", stepWindow),
    )

  private companion object {
    const val LOOKBACK_CALENDAR_DAYS = 400L
    const val WINDOW_COUNT = 3
  }
}
