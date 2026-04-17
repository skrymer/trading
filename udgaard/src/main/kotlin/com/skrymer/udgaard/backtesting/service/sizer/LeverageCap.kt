package com.skrymer.udgaard.backtesting.service.sizer

import kotlin.math.floor

/**
 * Portfolio-level leverage cap. Reduces a sizer's proposed share count so total open notional
 * stays within `portfolioValue × leverageRatio`. Applied outside the sizer — the sizer is a pure
 * "how much would I want?" decision; this enforces the account-level constraint.
 *
 * Returns 0 if no capital is available (already at/above cap).
 * Returns `shares` unchanged if `leverageRatio` is null (no cap configured).
 */
fun applyLeverageCap(
  shares: Int,
  entryPrice: Double,
  portfolioValue: Double,
  openNotional: Double,
  leverageRatio: Double?,
): Int {
  if (leverageRatio == null || shares <= 0 || entryPrice <= 0.0) return shares
  val available = portfolioValue * leverageRatio - openNotional
  if (available <= 0.0) return 0
  val capped = floor(available / entryPrice).toInt()
  return minOf(shares, capped)
}
