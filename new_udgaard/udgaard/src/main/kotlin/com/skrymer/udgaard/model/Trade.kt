package com.skrymer.udgaard.model

/**
 * Represents a trade with an entry, the quotes while the trade was on and an exit.
 */
class Trade
/**
 * @param stock - the stock the trade was for.
 * @param entryQuote - the stock quote on the day of entry.
 * @param exitQuote - the stock quote on the day of exit.
 * @param quotes - the stock quotes included in the trade, excluding entry and exit.
 */(var stock: Stock?, var entryQuote: StockQuote, var exitQuote: StockQuote, var quotes: MutableList<StockQuote?>?) {
    val profit: Double
        /**
         * Calculate the profit of this trade: "exit close price" - "entry close price"
         * @return
         */
        get() = exitQuote.getClosePrice() - entryQuote.getClosePrice()
}
