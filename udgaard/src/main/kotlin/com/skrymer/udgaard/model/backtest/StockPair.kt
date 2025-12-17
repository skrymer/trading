package com.skrymer.udgaard.model.backtest

import com.skrymer.udgaard.domain.StockDomain

/**
 * Represents a pair of stocks used during backtesting:
 * - tradingStock: the stock being traded (e.g., TQQQ)
 * - strategyStock: the stock used for strategy signals (e.g., QQQ for TQQQ)
 * - underlyingSymbol: the symbol of the underlying if different from trading stock, null otherwise
 *
 * When useUnderlyingAssets is true, leveraged ETFs will use their underlying asset
 * for strategy evaluation while trading the leveraged asset.
 */
data class StockPair(
  val tradingStock: StockDomain,
  val strategyStock: StockDomain,
  val underlyingSymbol: String?,
)
