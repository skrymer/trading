package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.intOr
import com.skrymer.udgaard.backtesting.service.stringOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks a stacked (strictly descending) moving-average alignment —
 * a Stage-2 uptrend trend filter.
 *
 * With the defaults (SMA 50/150/200, requirePriceAboveFast = true) this asserts
 * `close > SMA50 > SMA150 > SMA200`, collapsing the price/MA criteria (price above each MA,
 * and 50 > 150 > 200) into one strictly-ordered chain.
 *
 * Each MA is read directly from the pre-computed, persisted quote fields — never recomputed.
 * A MA that is unavailable (null SMA, or a 0.0 EMA placeholder) means insufficient history,
 * and the condition fails: a stock without a full window must not pass the trend gate.
 *
 * @param maType "SMA" (default) or "EMA"
 * @param fastPeriod fastest MA, sits on top of the stack (default 50)
 * @param midPeriod middle MA (default 150)
 * @param slowPeriod slowest MA, bottom of the stack (default 200)
 * @param requirePriceAboveFast also require close > MA(fast) (default true)
 */
@Component
class MovingAverageStackCondition(
  private val maType: String = "SMA",
  private val fastPeriod: Int = 50,
  private val midPeriod: Int = 150,
  private val slowPeriod: Int = 200,
  private val requirePriceAboveFast: Boolean = true,
) : EntryCondition {
  init {
    val supported = supportedMaPeriods(maType)
    require(supported.isNotEmpty()) { "Unsupported MA type '$maType' (use SMA or EMA)" }
    listOf(fastPeriod, midPeriod, slowPeriod).forEach { period ->
      require(period in supported) { "$maType does not provide period $period (supported: ${supported.sorted()})" }
    }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val fast = quote.movingAverage(maType, fastPeriod) ?: return false
    val mid = quote.movingAverage(maType, midPeriod) ?: return false
    val slow = quote.movingAverage(maType, slowPeriod) ?: return false

    val stackOrdered = fast > mid && mid > slow
    return if (requirePriceAboveFast) quote.closePrice > fast && stackOrdered else stackOrdered
  }

  override fun description(): String {
    val pricePrefix = if (requirePriceAboveFast) "Price > " else ""
    return "$pricePrefix$maType$fastPeriod > $maType$midPeriod > $maType$slowPeriod"
  }

  override fun getMetadata() =
    ConditionMetadata(
      type = "movingAverageStack",
      displayName = "Moving Average Stack",
      description = "Moving averages are stacked in strictly descending order (Stage-2 trend filter)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "maType",
            displayName = "MA Type",
            type = "string",
            defaultValue = maType,
            options = listOf("SMA", "EMA"),
          ),
          ParameterMetadata(
            name = "fastPeriod",
            displayName = "Fast Period",
            type = "number",
            defaultValue = fastPeriod,
            min = 5,
            max = 200,
          ),
          ParameterMetadata(
            name = "midPeriod",
            displayName = "Mid Period",
            type = "number",
            defaultValue = midPeriod,
            min = 5,
            max = 200,
          ),
          ParameterMetadata(
            name = "slowPeriod",
            displayName = "Slow Period",
            type = "number",
            defaultValue = slowPeriod,
            min = 5,
            max = 200,
          ),
          ParameterMetadata(
            name = "requirePriceAboveFast",
            displayName = "Require Price Above Fast MA",
            type = "boolean",
            defaultValue = requirePriceAboveFast,
          ),
        ),
      category = "Trend",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val fast = quote.movingAverage(maType, fastPeriod)
    val mid = quote.movingAverage(maType, midPeriod)
    val slow = quote.movingAverage(maType, slowPeriod)

    if (fast == null || mid == null || slow == null) {
      return ConditionEvaluationResult(
        conditionType = "MovingAverageStackCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "Moving averages not available (insufficient history) ✗",
      )
    }

    val passed = evaluate(stock, quote, context)
    val actual =
      "Price ${"%.2f".format(quote.closePrice)}, $maType$fastPeriod ${"%.2f".format(fast)}, " +
        "$maType$midPeriod ${"%.2f".format(mid)}, $maType$slowPeriod ${"%.2f".format(slow)}"
    val mark = if (passed) "✓" else "✗"

    return ConditionEvaluationResult(
      conditionType = "MovingAverageStackCondition",
      description = description(),
      passed = passed,
      actualValue = actual,
      threshold = description(),
      message = "$actual $mark",
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    MovingAverageStackCondition(
      maType = parameters.stringOr("maType", maType),
      fastPeriod = parameters.intOr("fastPeriod", fastPeriod),
      midPeriod = parameters.intOr("midPeriod", midPeriod),
      slowPeriod = parameters.intOr("slowPeriod", slowPeriod),
      requirePriceAboveFast = (parameters["requirePriceAboveFast"] as? Boolean) ?: requirePriceAboveFast,
    )
}
