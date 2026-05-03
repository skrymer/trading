package com.skrymer.udgaard.backtesting.model

/**
 * Risk-adjusted ratios derived from a position-sized backtest's daily equity curve and trades.
 * All fields are nullable — they're not always computable (e.g. flat curve → no Sharpe, fewer
 * than 20 trades → no tailRatio, zero drawdown → no Calmar).
 *
 * `sqn` (System Quality Number) scales with sqrt(trade count) — compare across strategies with
 * similar trade frequency, not across strategies with very different N.
 */
data class RiskMetrics(
  val sharpeRatio: Double?,
  val sortinoRatio: Double?,
  val calmarRatio: Double?,
  val sqn: Double?,
  val tailRatio: Double?,
)
