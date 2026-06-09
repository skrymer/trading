package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Market-level regime gate: permits entries only when the leadership-gap regime is ON for the bar —
 * i.e. the equal-weight universe is leading the cap-weighted market (a broad thrust) rather than a
 * thin tape of mega-caps carrying the index.
 *
 * The read is pre-computed once per backtest into [BacktestContext.leadershipRegimeMap] (gap = SPY
 * 20-bar return minus the equal-weight universe mean, EMA-smoothed, Schmitt-triggered with a dead-band,
 * vetoed by a sustained breadth washout). This condition only reads that boolean, so it carries no
 * tunable parameters of its own. A date with no pre-computed read defaults to cash.
 */
@Component
class LeadershipGapRegimeOnCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = context.getLeadershipRegimeOn(quote.date)

  override fun description(): String = "Leadership-gap regime is ON (equal-weight leading the market)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "leadershipGapRegimeOn",
      displayName = "Leadership-gap regime ON",
      description =
        "Permits entries only when the equal-weight universe is leading the cap-weighted market " +
          "(broad-thrust deploy regime), not when a thin tape of mega-caps is carrying the index",
      parameters = emptyList(),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    return ConditionEvaluationResult(
      conditionType = "LeadershipGapRegimeOnCondition",
      description = description(),
      passed = passed,
      actualValue = if (passed) "regime ON (deploy)" else "regime OFF (cash)",
      threshold = "regime ON",
      message =
        if (passed) {
          "Leadership-gap regime ON for ${quote.date} — equal-weight leading ✓"
        } else {
          "Leadership-gap regime OFF for ${quote.date} — thin tape / no confirmed broad thrust ✗"
        },
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this
}
