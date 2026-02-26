package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that filters out choppy, range-bound markets.
 * Uses the width of the 20-day Donchian channel on market breadth as a regime proxy.
 * Wide channel = trending market (allow entries). Narrow channel = choppy market (block entries).
 * Also requires breadth > EMA10 to ensure we're entering in an uptrending market.
 *
 * @param minWidth Minimum Donchian band width in percentage points. Default 20.0.
 */
@Component
class MarketBreadthTrendingCondition(
  private val minWidth: Double = 20.0,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val breadth = context.getMarketBreadth(quote.date) ?: return false
    val width = breadth.donchianUpperBand - breadth.donchianLowerBand
    return width >= minWidth && breadth.breadthPercent > breadth.ema10
  }

  override fun description(): String =
    "Market breadth trending (Donchian width >= %.0f, breadth > EMA10)".format(minWidth)

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthTrending",
      displayName = "Market Breadth Trending",
      description = "Market breadth Donchian channel is wide enough to indicate a trending market, with breadth above EMA10",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minWidth",
            displayName = "Min Donchian Width",
            type = "number",
            defaultValue = 20.0,
            min = 5.0,
            max = 50.0,
          ),
        ),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val breadth = context.getMarketBreadth(quote.date)
    val upper = breadth?.donchianUpperBand ?: 0.0
    val lower = breadth?.donchianLowerBand ?: 0.0
    val width = upper - lower
    val breadthPct = breadth?.breadthPercent ?: 0.0
    val ema10 = breadth?.ema10 ?: 0.0

    val message =
      if (passed) {
        "Market trending: Donchian width %.1f >= %.1f, breadth %.1f%% > EMA10 %.1f%%".format(
          width,
          minWidth,
          breadthPct,
          ema10,
        )
      } else {
        "Market not trending: Donchian width %.1f (need >= %.1f), breadth %.1f%% vs EMA10 %.1f%%".format(
          width,
          minWidth,
          breadthPct,
          ema10,
        )
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthTrendingCondition",
      description = description(),
      passed = passed,
      actualValue = "width=%.1f, breadth=%.1f%%".format(width, breadthPct),
      threshold = "width >= %.1f, breadth > EMA10 (%.1f%%)".format(minWidth, ema10),
      message = message,
    )
  }
}
