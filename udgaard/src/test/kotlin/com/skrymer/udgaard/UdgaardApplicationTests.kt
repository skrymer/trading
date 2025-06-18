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
    lateinit var ovtlyrClient: OvtlyrClient

    @Autowired
    lateinit var stockRepository: StockRepository

    @Autowired
    lateinit var marketBreadthRepository: MarketBreadthRepository

    @Autowired
    lateinit var dataLoader: DataLoader

    @Autowired
    lateinit var stockService: StockService

    @Test
    fun contextLoads() {}

    @Test
    fun loadStocks() {
        println("===================== Getting stocks =====================")
        val tsla = stockService.getStock("TSLA")
        val googl = stockService.getStock("GOOGL")
        val amzn = stockService.getStock("AMZN")
        val aapl = stockService.getStock("AAPL")
        val meta = stockService.getStock("META")
        val msft = stockService.getStock("MSFT")
        val nvda = stockService.getStock("NVDA")

        val entryStrategy = Ovtlyr9EntryStrategy()
        val exitStrategy = MainExitStrategy()
        val backtestReport = stockService.backtest(entryStrategy, exitStrategy, tsla, googl, amzn, aapl, meta, msft, nvda)

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
            trade.quotes.forEach{ quote ->
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
            trade.quotes.forEach{ quote ->
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
    }

    @Test
    fun loadMarketBreadth() {
        dataLoader.loadData()
    }

    @Test
    fun testOvtlyr9EntryStrategy() {
    }

} // await fetch("https://api.ovtlyr.com/v1.0/StockSymbol/GetAllDashboardChartBySymbolWithFiltersAndSort", {
//     "credentials": "omit",
//     "headers": {
//         "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:139.0) Gecko/20100101 Firefox/139.0",
//         "Accept": "*/*",
//         "Accept-Language": "en-US,en;q=0.5",
//         "Content-Type": "application/json",
//         "UserId": "7273",
//         "Token": "j5huga412w_ae08079a-bde7-44a8-978d-e5906fea1046",
//         "ProjectId": "Ovtlyr.com_project1",
//         "Sec-Fetch-Dest": "empty",
//         "Sec-Fetch-Mode": "cors",
//         "Sec-Fetch-Site": "same-site"
//     },
//     "referrer": "https://ovtlyr.com/",
//     "body": "{\"stockSymbol\":\"SPY\",\"period\":\"All\",\"page_index\":0,\"page_size\":2000}",
//     "method": "POST",
//     "mode": "cors"
// });

