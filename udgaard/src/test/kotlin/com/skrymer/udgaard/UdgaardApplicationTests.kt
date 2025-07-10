package com.skrymer.udgaard

import com.skrymer.udgaard.integration.ovtlyr.DataLoader
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.model.strategy.MainExitStrategy
import com.skrymer.udgaard.model.strategy.Ovtlyr9EntryStrategy
import com.skrymer.udgaard.service.StockService
import de.siegmar.fastcsv.writer.CsvWriter
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
            ?.filter { it.buySellDate?.isAfter(LocalDate.of(2025, 7, 6)) == true  }
            ?.sortedByDescending { it.ovtlySignalReturn }
        val file: Path = Paths.get("output.csv")
        val stocks = stockService.getStocks(screenerResults?.mapNotNull{ it.symbol} ?: emptyList(), true)
        val entryStrategy = Ovtlyr9EntryStrategy()
        val exitStrategy = MainExitStrategy()
        val backtestReport = stockService.backtest(entryStrategy, exitStrategy, stocks)

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
//        val stocks = dataLoader.loadTopStocks()
        val stock = stockService.getStocks(listOf("DT"), true).first()

        val entryStrategy = Ovtlyr9EntryStrategy()
        val exitStrategy = MainExitStrategy()
        val backtestReport = stockService.backtest(entryStrategy, exitStrategy, listOf(stock))

        println("===================== Back test executing =====================")
        println("Using entry strategy: ${entryStrategy.description()}")
        println("Using exit strategy: ${exitStrategy.description()}")
        println("Number of wins: ${backtestReport.numberOfWinningTrades}")
        println("Number of losses: ${backtestReport.numberOfLosingTrades}")

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
            println("Wining: ${trade.profitPercentage} percent\n")
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
            println("Loss: ${trade.profitPercentage} percent\n")
        }

        println("========================= Stats:: =====================")
        println("Win rate ${backtestReport.winRate * 100}")
        println("Average win amount ${backtestReport.averageWinAmount}")
        println("Loss rate ${backtestReport.lossRate * 100}")
        println("Average loss amount ${backtestReport.averageLossAmount}")
        println("The average profit you can expect per trade ${backtestReport.edge}")
        println("Most profitable stock ${backtestReport.mostProfitable}")
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
