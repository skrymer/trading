package com.skrymer.udgaard.model

import java.time.temporal.ChronoUnit

/**
 * Represents a trade with an entry, the quotes while the trade was on and an exit.
 * @param stockSymbol - the stock symbol the trade was for.
 * @param entryQuote - the stock quote on the day of entry.
 * @param quotes - the stock quotes included in the trade, excluding entry and exit.
 * @param exitReason - the reason for exiting the trade.
 */
class Trade(
    var stockSymbol: String,
    var entryQuote: StockQuote,
    var quotes: List<StockQuote>,
    var exitReason: String,
    var profit: Double = 0.0
) {

    /**
     * Calculate the profit percentage of this trade: (profit/entry close price) * 100
     * @return
     */
    val profitPercentage: Double
        get() = (profit / entryQuote.closePrice) * 100.0

    /**
     * The number of days the trade lasted
     */
    val tradingDays: Long
        get() {
            val exitDate = quotes . sortedByDescending { it.date }.first().date
            return ChronoUnit.DAYS.between( entryQuote.date, exitDate)
        }

    fun containsQuote(stockQuote: StockQuote) = quotes.contains(stockQuote)
}
