package com.skrymer.udgaard

import com.skrymer.udgaard.integration.ovtlyr.DataLoader
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.Trade
import com.skrymer.udgaard.model.strategy.Ovtlyr9EntryStrategy
import com.skrymer.udgaard.model.strategy.PriceUnder10EmaExitStrategy
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import com.skrymer.udgaard.service.StockService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.*
import java.util.function.Consumer

@SpringBootTest
internal class UdgaardApplicationTests {
    @Autowired
    var ovtlyrClient: OvtlyrClient? = null

    @Autowired
    var stockRepository: StockRepository? = null

    @Autowired
    var marketBreadthRepository: MarketBreadthRepository? = null

    @Autowired
    var dataLoader: DataLoader? = null

    @Autowired
    var stockService: StockService? = null

    @Test
    fun contextLoads() {
    }

    @Test
    fun loadStocks() {
        val stock: Stock = stockRepository!!.findById("PLTR").orElseGet(java.util.function.Supplier {
            val response = ovtlyrClient!!.getStockInformation("PLTR")
            val marketBreadth = marketBreadthRepository!!.findById(MarketSymbol.FULLSTOCK)
            val sectorMarketBreadth: Optional<MarketBreadth?> =
                marketBreadthRepository.findById(MarketSymbol.valueOf(response.sectorSymbol))
            val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
            stockRepository.save<Stock?>(response.toModel(marketBreadth, sectorMarketBreadth, spy))
        })!!
        val entryStrategy = Ovtlyr9EntryStrategy()
        val exitStrategy = PriceUnder10EmaExitStrategy()
        val backtestReport = stockService!!.backtest(stock, entryStrategy, exitStrategy)
        println("===================== Back test for %s =====================".formatted(stock.symbol))
        println("Using entry strategy: %s".formatted(entryStrategy.description()))
        println("USing exit strategy: %s".formatted(exitStrategy.description()))
        println("Winning trades: (%s)".formatted(backtestReport.winningTrades.size))
        backtestReport.winningTrades.forEach(Consumer { trade: Trade? ->
            val entryQuote = trade!!.entryQuote
            val exitQuote = trade.exitQuote

            println(
                "Entry date %s entryprice@close: %s entry10EMAprice@close: %s".formatted(
                    entryQuote.date,
                    entryQuote.closePrice,
                    entryQuote.closePrice_EMA10
                )
            )
            trade.quotes!!.forEach(Consumer { quote: StockQuote? ->
                println(
                    "Date: %s price@close: %s 10EMA@close %s".formatted(
                        quote!!.date, quote.closePrice, quote.closePrice_EMA10
                    )
                )
            })
            println(
                "Exit date %s exitprice@close: %s exit10EMAprice@close: %s".formatted(
                    exitQuote.date,
                    exitQuote.closePrice,
                    exitQuote.closePrice_EMA10
                )
            )
            println("Win: %s\n".formatted(trade.profit))
        })
        println("\nLosing trades: (%s)".formatted(backtestReport.losingTrades.size))
        backtestReport.losingTrades.forEach(Consumer { trade: Trade? ->
            val entryQuote = trade!!.entryQuote
            val exitQuote = trade.exitQuote

            println(
                "Entry date %s entryprice@close: %s entry10EMAprice@close: %s".formatted(
                    entryQuote.date,
                    entryQuote.closePrice,
                    entryQuote.closePrice_EMA10
                )
            )
            trade.quotes!!.forEach(Consumer { quote: StockQuote? ->
                println(
                    "Date: %s price@close: %s 10EMA@close %s".formatted(
                        quote!!.date, quote.closePrice, quote.closePrice_EMA10
                    )
                )
            })
            println(
                "Exit date %s exitprice@close: %s exit10EMAprice@close: %s".formatted(
                    exitQuote.date,
                    exitQuote.closePrice,
                    exitQuote.closePrice_EMA10
                )
            )
            println("Loss: %s\n".formatted(trade.profit))
        })

        println("========================= Stats:: =====================")
        println("Win rate %s".formatted(backtestReport.winRate * 100))
        println("Average win amount %s".formatted(backtestReport.averageWinAmount))
        println("Loss rate %s".formatted(backtestReport.lossRate * 100))
        println("Average loss amount %s".formatted(backtestReport.averageLossAmount))
        println("The average profit you can expect per trade %s".formatted(backtestReport.edge))
    }

    @Test
    fun loadMarketBreadth() {
        dataLoader!!.loadData()
    }

    @Test
    fun testOvtlyr9EntryStrategy() {
    }

    @Test
    fun getStock() {
        val stock = stockService!!.getStock("NVDA")

        println(stock)
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

