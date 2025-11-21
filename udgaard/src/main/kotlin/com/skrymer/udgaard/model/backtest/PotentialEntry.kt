package com.skrymer.udgaard.model.backtest

import com.skrymer.udgaard.model.StockQuote

/**
 * Represents a potential trade entry identified on a specific date.
 *
 * Contains both the strategy quote (used for entry signal evaluation) and
 * the trading quote (used for actual price/P&L calculation).
 *
 * @property stockPair - the stock pair containing trading and strategy stocks
 * @property strategyEntryQuote - quote from the strategy stock used for entry evaluation
 * @property tradingEntryQuote - quote from the trading stock used for actual entry price
 */
data class PotentialEntry(
    val stockPair: StockPair,
    val strategyEntryQuote: StockQuote,
    val tradingEntryQuote: StockQuote
)
