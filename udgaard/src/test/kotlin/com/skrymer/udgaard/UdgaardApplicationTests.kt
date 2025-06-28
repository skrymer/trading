package com.skrymer.udgaard

import com.skrymer.udgaard.integration.ovtlyr.DataLoader
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.model.StockSymbol
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
        var screenerResults = ovtlyrClient.getScreenerStocks()?.stocks
            ?.filter { it.buySellDate?.equals(LocalDate.now()) == true || it.buySellDate?.equals(LocalDate.now().minusDays(1)) == true  }
            ?.sortedByDescending { it.ovtlySignalReturn }
        val file: Path = Paths.get("output.csv")

        val stocks = stockService.getStocks(screenerResults?.mapNotNull{ it.symbol} ?: emptyList())

        CsvWriter.builder().build(file).use { csv ->
            csv.writeRecord("Symbol", "Buy sell date", "Ovtlyr return", "Close price", "Sector", "Signal", "Sector greedier", "Order block", "Liquidity")

            screenerResults
            ?.forEach {
                val stockQuote = stocks.find { stock -> it.symbol?.equals(stock.symbol) == true }?.getQuoteByDate(it.buySellDate ?: LocalDate.now())
                println(stockQuote)
                csv.writeRecord(
                it.symbol,
                it.buySellDate.toString(),
                it.ovtlySignalReturn.format(2),
                it.closePrice.format(2),
                it.sector,
                it.signal
            )}
        }

        println(screenerResults)
    }

    @Test
    fun generateBacktestReport() {
        println("===================== Getting stocks =====================")
        val stocks = dataLoader.loadTopStocks()

        val entryStrategy = Ovtlyr9EntryStrategy()
        val exitStrategy = MainExitStrategy()
        val backtestReport = stockService.backtest(entryStrategy, exitStrategy, stocks)

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
    }

    @Test
    fun loadMarketBreadth() {
        dataLoader.loadData()
    }

    fun Double.format(scale: Int) = "%.${scale}f".format(this)
}
