package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.Trade
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import org.springframework.stereotype.Service
import java.util.*

@Service
class StockService() {

    fun backtest(stock: Stock, entryStrategy: EntryStrategy, exitStrategy: ExitStrategy): BacktestReport {
        val quotesMatchingEntryStrategy = stock.getQuotesMatching(entryStrategy)
        val winningTrades = ArrayList<Trade>()
        val losingTrades = ArrayList<Trade>()

        quotesMatchingEntryStrategy.forEach { entryQuote ->
            val quotesMatchingExitStrategy = stock.testExitStrategy(entryQuote.date, exitStrategy)
            val exitQuote: StockQuote
            var profit = 0.0

            // When next quote did not match the exit strategy 
            if (quotesMatchingExitStrategy.isEmpty()) {
                val nextQuoteClosePrice = stock.getQuoteAfter(entryQuote).closePrice
                profit = nextQuoteClosePrice - entryQuote.closePrice
                exitQuote = stock.getQuoteAfter(entryQuote)
            } else {
                profit = quotesMatchingExitStrategy.last().closePrice - entryQuote.closePrice
                exitQuote = stock.getQuoteAfter(quotesMatchingExitStrategy.last())
            }
            if (profit > 0) {
                winningTrades.add(Trade(stock, entryQuote, exitQuote, quotesMatchingExitStrategy))
            } else {
                losingTrades.add(Trade(stock, entryQuote, exitQuote, quotesMatchingExitStrategy))
            }
        }

        return BacktestReport(winningTrades, losingTrades)
    }
}
