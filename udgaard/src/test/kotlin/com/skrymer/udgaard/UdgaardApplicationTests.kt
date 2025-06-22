package com.skrymer.udgaard

import com.skrymer.udgaard.integration.ovtlyr.DataLoader
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.model.strategy.MainExitStrategy
import com.skrymer.udgaard.model.strategy.Ovtlyr9EntryStrategy
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import com.skrymer.udgaard.service.StockService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class UdgaardApplicationTests {
    @Autowired
    lateinit var dataLoader: DataLoader

    @Autowired
    lateinit var stockService: StockService

    @Test
    fun contextLoads() {
    }

    @Test
    fun loadStocks() {
        println("===================== Getting stocks =====================")
        val stocks = dataLoader.loadTopStocks()

        val entryStrategy = Ovtlyr9EntryStrategy()
        val exitStrategy = MainExitStrategy()
        val backtestReport = stockService.backtest(entryStrategy, exitStrategy, stocks)

        println("===================== Back test executing =====================")
        println("Using entry strategy: ${entryStrategy.description()}")
        println("Using exit strategy: ${exitStrategy.description()}")
        println("Number of wins: ${backtestReport.numberOfWinningTrades()}")
        println("Number of losses: ${backtestReport.numberOfLosingTrades()}")

        println("Winning trades: (${backtestReport.winningTrades.size})")
        backtestReport.winningTrades.forEach { trade ->
            val entryQuote = trade.entryQuote
            val exitQuote = trade.exitQuote

            println("${entryQuote.symbol} entry date ${entryQuote.date} entry price@close: ${entryQuote.closePrice}")
            trade.quotes.forEach { quote ->
                println("Date: ${quote.date} price@close: ${quote.closePrice}")
            }
            println("Exit date ${exitQuote?.date} exit price@close: ${exitQuote?.closePrice} entry price@close ${entryQuote.closePrice}")
            println("Reason for exiting: ${trade.exitReason}")
            println("Wining: ${trade.calculatePercentageProfit()} percent\n")
        }

        println("Losing trades: (${backtestReport.losingTrades.size})")
        backtestReport.losingTrades.forEach { trade ->
            val entryQuote = trade.entryQuote
            val exitQuote = trade.exitQuote

            println("${entryQuote.symbol} entry date ${entryQuote.date} entry price@close: ${entryQuote.closePrice}")
            trade.quotes.forEach { quote ->
                println("Date: ${quote.date} price@close: ${quote.closePrice}")
            }
            println("Exit date ${exitQuote?.date} exit price@close: ${exitQuote?.closePrice} entry price@close ${entryQuote.closePrice}")
            println("Reason for exiting: ${trade.exitReason}")
            println("Loss: ${trade.calculatePercentageProfit()} percent\n")
        }

        println("========================= Stats:: =====================")
        println("Win rate ${backtestReport.winRate * 100}")
        println("Average win amount ${backtestReport.averageWinAmount}")
        println("Loss rate ${backtestReport.lossRate * 100}")
        println("Average loss amount ${backtestReport.averageLossAmount}")
        println("The average profit you can expect per trade ${backtestReport.edge}")
        println("Most profitable stock ${backtestReport.mostProfitable()}")
    }

    @Test
    fun loadMarketBreadth() {
        dataLoader.loadData()
    }
}
