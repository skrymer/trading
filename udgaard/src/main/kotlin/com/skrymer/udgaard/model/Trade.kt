package com.skrymer.udgaard.model

/**
 * Represents a trade with an entry, the quotes while the trade was on and an exit.
 * @param stock - the stock the trade was for.
 * @param entryQuote - the stock quote on the day of entry.
 * @param exitQuote - the stock quote on the day of exit.
 * @param quotes - the stock quotes included in the trade, excluding entry and exit.
 * @param exitReason - the reason for exiting the trade.
 */
class Trade(
    var stock: Stock,
    var entryQuote: StockQuote,
    var exitQuote: StockQuote?,
    var quotes: List<StockQuote>,
    var exitReason: String
) {
    /**
     * Calculate the profit of this trade: "exit close price" - "entry close price"
     * @return
     */
    fun calculateProfit() = (exitQuote?.closePrice ?: 0.0) - entryQuote.closePrice

    /**
     * Calculate the profit of this trade: "exit close price" - "entry close price"
     * @return
     */
    fun calculatePercentageProfit() = (calculateProfit() / entryQuote.closePrice) * 100.0

}
