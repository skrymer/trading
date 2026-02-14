package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Trailing stop loss that tracks the highest price reached since entry
 * and exits when price drops by a specified ATR multiple from that high.
 *
 * Unlike a fixed stop loss, this "trails" the price upward, locking in gains
 * while still protecting against downside. The stop level only moves up, never down.
 *
 * Example with 2.7 ATR:
 * - Entry at $100, ATR = $5
 * - Price goes to $110 (new high) -> Stop at $110 - (2.7 * $5) = $96.50
 * - Price goes to $120 (new high) -> Stop at $120 - (2.7 * $5) = $106.50
 * - Price drops to $105 -> Still holding (above $106.50 stop)
 * - Price drops to $106 -> Exit triggered (below $106.50)
 *
 * @param atrMultiplier Number of ATRs below the highest price to trigger exit (default 2.7)
 */
@Component
class ATRTrailingStopLoss(
  private val atrMultiplier: Double = 2.7,
) : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean {
    if (entryQuote == null) return false

    // Get all quotes from entry to current
    val quotesSinceEntry =
      stock.quotes
        .filter { q ->
          q.date != null &&
            entryQuote.date != null &&
            (q.date!! >= entryQuote.date!!) &&
            (q.date!! <= quote.date!!)
        }.sortedBy { it.date }

    if (quotesSinceEntry.isEmpty()) return false

    // Find the highest close price reached since entry
    val highestPrice = quotesSinceEntry.maxOfOrNull { it.closePrice } ?: entryQuote.closePrice

    // Calculate trailing stop level using the ATR from the current quote
    val trailingStopLevel = highestPrice - (atrMultiplier * quote.atr)

    // Exit if current price drops below the trailing stop
    return quote.closePrice < trailingStopLevel
  }

  override fun exitReason(): String = "ATR trailing stop loss triggered ($atrMultiplier ATR below highest price)"

  override fun description(): String = "ATR trailing stop ($atrMultiplier ATR)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "trailingStopLoss",
      displayName = "ATR Trailing Stop Loss",
      description = "Exit when price drops X ATR below the highest price since entry (trailing stop)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "atrMultiplier",
            displayName = "ATR Multiplier",
            type = "number",
            defaultValue = 2.7,
            min = 0.5,
            max = 5.0,
          ),
        ),
      category = "StopLoss",
    )
}
