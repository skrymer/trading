package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Hard-line SPY price-trend gate. Permits entries only when SPY's close is strictly above
 * its 200-day EMA on the evaluation date.
 *
 * Distinct from `SpyPriceUptrendCondition`: this uses a single slow 200-EMA reference (regime-
 * scale, ~year-long). `SpyPriceUptrendCondition` uses an EMA5 > EMA10 > EMA20 alignment plus
 * price > EMA50 (trend-scale, ~quarter-long). They measure different time horizons and can
 * disagree — e.g. SPY may be above its 200-EMA but failing the shorter-EMA alignment during
 * a multi-week pullback.
 *
 * Complements `BreadthEma10Above50Condition` (fast breadth-level gate) and `MarketUptrendCondition`
 * (breadth-vs-EMA10 trend) — three independent regime dimensions per the regime-conditional
 * portfolio framework (memory: regime-conditional-portfolio-framework).
 *
 * The SPY quote is read from `BacktestContext.spyQuoteMap`, which the engine pre-loads with
 * point-in-time SPY data. Returns false if no SPY quote exists for the date — by convention,
 * missing data means the gate cannot confirm "safe regime", so entries are suppressed.
 */
@Component
class SpyTrendUpCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val spy = context.getSpyQuote(quote.date) ?: return false
    if (spy.ema200 <= 0.0) return false
    return spy.closePrice > spy.ema200
  }

  override fun description(): String = "SPY close > SPY 200-EMA"

  override fun getMetadata() =
    ConditionMetadata(
      type = "spyTrendUp",
      displayName = "SPY in 200-EMA uptrend",
      description = "Permits entries only when SPY close is above its 200-day EMA",
      parameters = emptyList(),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val spy = context.getSpyQuote(quote.date)
    val spyClose = spy?.closePrice ?: 0.0
    val spyEma200 = spy?.ema200 ?: 0.0
    val message =
      when {
        spy == null -> "SPY quote unavailable for ${quote.date} ✗"
        spyEma200 <= 0.0 -> "SPY 200-EMA unavailable (not enough history) ✗"
        passed -> "SPY %.2f is above 200-EMA %.2f ✓".format(spyClose, spyEma200)
        else -> "SPY %.2f is at or below 200-EMA %.2f ✗".format(spyClose, spyEma200)
      }
    return ConditionEvaluationResult(
      conditionType = "SpyTrendUpCondition",
      description = description(),
      passed = passed,
      actualValue = "%.2f".format(spyClose),
      threshold = "> %.2f (200-EMA)".format(spyEma200),
      message = message,
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this
}
