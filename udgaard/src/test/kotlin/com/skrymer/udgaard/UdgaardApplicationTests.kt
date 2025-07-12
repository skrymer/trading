package com.skrymer.udgaard

import com.skrymer.udgaard.integration.ovtlyr.DataLoader
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.strategy.MainExitStrategy
import com.skrymer.udgaard.model.strategy.Ovtlyr9EntryStrategy
import com.skrymer.udgaard.service.StockService
import de.siegmar.fastcsv.writer.CsvWriter
import io.polygon.kotlin.sdk.rest.PolygonRestClient
import io.polygon.kotlin.sdk.rest.options.getSnapshot
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate

@SpringBootTest
internal class UdgaardApplicationTests {
    @Autowired
    lateinit var dataLoader: DataLoader

    @Autowired
    lateinit var stockService: StockService

    @Autowired
    lateinit var ovtlyrClient: OvtlyrClient

    @Test
    fun contextLoads() {
    }



    @Test
    fun screener() {
        val screenerResults = ovtlyrClient.getScreenerStocks()
            ?.stocks
            ?.filter { it.buySellDate?.isAfter(LocalDate.of(2025, 7, 8)) == true  }
            ?.sortedByDescending { it.ovtlySignalReturn }
        val file: Path = Paths.get("output.csv")
        val stocks = stockService.getStocks(screenerResults?.mapNotNull{ it.symbol} ?: emptyList(), true)
        val entryStrategy = Ovtlyr9EntryStrategy()
        val exitStrategy = MainExitStrategy()
        val backtestReport = stockService.backtest(entryStrategy, exitStrategy, stocks)

        val polygonClient = PolygonRestClient(
            apiKey = ""
        )

//        polygonClient.optionsClient.getSnapshot()
        val csvHeaders = listOf(
            "Symbol",
            "Buy sell date",
            "Ovtlyr return",
            "Close price",
            "Sector",
            "Signal",
            "Heatmap",
            "Previous Heatmap",
            "Sector heatmap",
            "Sector previous heatmap",
            "Profits",
            "Order block",
            "Earnings in next 3 days",
            "EV",
            "Ask",
            "Strike",
        )

        CsvWriter.builder().build(file).use { csv ->
            csv.writeRecord(csvHeaders)
            screenerResults
            ?.forEach {
                val stock = stocks.find { stock -> it.symbol?.equals(stock.symbol) == true }
                val quote = stock?.getQuoteByDate(LocalDate.now())
                val profits = backtestReport.stockProfits.find { pair -> pair.first.symbol == stock?.symbol }

                if((quote?.heatmap ?: 0.0) < (quote?.previousHeatmap ?: 0.0)){
                    println("Heatmap is stalling: ${stock?.symbol}")
                }
                else if((quote?.sectorHeatmap ?: 0.0) < (quote?.previousSectorHeatmap ?: 0.0)){
                    println("Sector heatmap is stalling: ${stock?.sectorSymbol}")
                }
                else {
                    csv.writeRecord(
                        it.symbol,
                        it.buySellDate.toString(),
                        it.ovtlySignalReturn.format(2),
                        it.closePrice.format(2),
                        it.sector,
                        it.signal,
                        quote?.heatmap.toString(),
                        quote?.previousHeatmap.toString(),
                        quote?.sectorHeatmap.toString(),
                        quote?.previousSectorHeatmap.toString(),
                        profits?.second?.format(2)
                    )
                }
            }
        }

        println(screenerResults)
    }

    @Test
    fun generateBacktestReport() {
        println("===================== Getting stocks =====================")
        val stocks = dataLoader.loadStockByMarket(MarketSymbol.XLK, false)
//        val stock = stockService.getStocks(listOf("DT"), true).first()
        val entryStrategy = Ovtlyr9EntryStrategy()
        val exitStrategy = MainExitStrategy()
        val backtestReport = stockService.backtest(entryStrategy, exitStrategy, stocks)

        println("===================== Back test executing:: =====================")
        println("Using entry strategy: ${entryStrategy.description()}")
        println("Using exit strategy: ${exitStrategy.description()}")
        println("========================= Stats:: =====================")
        println("Number of wins: ${backtestReport.numberOfWinningTrades}")
        println("Number of losses: ${backtestReport.numberOfLosingTrades}")
        println("Win rate ${(backtestReport.winRate * 100).format(2)}%")
        println("Average win amount ${backtestReport.averageWinAmount.format(2)}$")
        println("Loss rate ${(backtestReport.lossRate * 100).format(2)}%")
        println("Average loss amount ${backtestReport.averageLossAmount.format(2)}$")
        println("The percentage you can expect to win per trade ${backtestReport.edge.format(2)}%")
        println("========================= Stock information:: =========================")
        println("Stocks ordered by profitability")
        backtestReport.stockProfits.forEach {
            println("Symbol: ${it.first.symbol} profit ${it.second.format(2)}")
        }
    }

    @Test
    fun loadMarketBreadth() {
        dataLoader.loadData()
    }
}
