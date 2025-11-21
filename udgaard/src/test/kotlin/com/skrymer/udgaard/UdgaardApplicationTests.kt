package com.skrymer.udgaard

import com.skrymer.udgaard.integration.ovtlyr.DataLoader
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.model.strategy.PlanAlphaEntryStrategy
import com.skrymer.udgaard.model.strategy.PlanMoneyExitStrategy
import com.skrymer.udgaard.service.MarketBreadthService
import com.skrymer.udgaard.service.StockService
import de.siegmar.fastcsv.writer.CsvWriter
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

@SpringBootTest
internal class UdgaardApplicationTests {
    private val logger = LoggerFactory.getLogger(UdgaardApplicationTests::class.java)
    @Autowired
    lateinit var dataLoader: DataLoader

    @Autowired
    lateinit var stockService: StockService

    @Autowired
    lateinit var marketBreadthService: MarketBreadthService

    @Autowired
    lateinit var ovtlyrClient: OvtlyrClient

    @Test
    fun contextLoads() {
    }



//    @Test
    fun screener() {
        val screenerResults = ovtlyrClient.getScreenerStocks()
            ?.stocks
            ?.filter { it.buySellDate?.isAfter(LocalDate.of(2025, 7, 22)) == true  }
            ?.sortedByDescending { it.ovtlySignalReturn }
        val file: Path = Paths.get("output.csv")
        val stocks = runBlocking { stockService.getStocks(screenerResults?.mapNotNull{ it.symbol} ?: emptyList(), true) }
        val entryStrategy = PlanAlphaEntryStrategy()
        val exitStrategy = PlanMoneyExitStrategy()
        val backtestReport = stockService.backtest(

            entryStrategy,
            exitStrategy,
            stocks,
            LocalDate.of(2024, 1, 1),
            LocalDate.now()
            )

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
            "Donkey score",
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
                val marketBreadthFullStock = marketBreadthService.getMarketBreadth(MarketSymbol.FULLSTOCK)
                val sectorMarketBreadth = marketBreadthService.getMarketBreadth(MarketSymbol.valueOf(stock?.sectorSymbol))
                val quote = stock?.getQuoteByDate(it.buySellDate!!)
                val profits = backtestReport.stockProfits.find { pair -> pair.first == stock?.symbol }
                val fullStockDonkeyScore = marketBreadthFullStock?.getDonkeyScoreByDate(it.buySellDate!!) ?: 0
                val sectorDonkeyScore = sectorMarketBreadth?.getDonkeyScoreByDate(it.buySellDate!!) ?: 0
                val donkeyScore = fullStockDonkeyScore + sectorDonkeyScore

                if((quote?.heatmap ?: 0.0) < (quote?.previousHeatmap ?: 0.0)){
                    logger.info("Heatmap is stalling: ${stock?.symbol}")
                }
                else if((quote?.sectorHeatmap ?: 0.0) < (quote?.previousSectorHeatmap ?: 0.0)){
                    logger.info("Sector heatmap is stalling: ${stock?.sectorSymbol}")
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
                        profits?.second?.format(2),
                        donkeyScore.toString()
                    )
                }
            }
        }

        logger.info("Screener results: {}", screenerResults)
    }

//    @Test
    fun generateBacktestReport() {
        logger.info("===================== Getting stocks =====================")
        val stocks = runBlocking { dataLoader.loadTopStocks(true) }
//        val stock = stockService.getStocks(listOf("SMTC"), true).first()
        val entryStrategy = PlanAlphaEntryStrategy()
        val exitStrategy = PlanMoneyExitStrategy()
        val backtestReport =
            stockService.backtest( entryStrategy, exitStrategy,stocks, LocalDate.of(2023, 1, 1),
                LocalDate.now())

//        val quoteStrings = generateTestDataAsString(
//            stock,
//            LocalDate.of(2025, 6, 25),
//            LocalDate.of(2025,7,2)
//        )

        logger.info("===================== Back test executing:: =====================")
        logger.info("Using entry strategy: ${entryStrategy.description()}")
        logger.info("Using exit strategy: ${exitStrategy.description()}")
        logger.info("========================= Stats:: =====================")
        logger.info("Number of wins: ${backtestReport.numberOfWinningTrades}")
        logger.info("Number of losses: ${backtestReport.numberOfLosingTrades}")
        logger.info("Win rate ${(backtestReport.winRate * 100).format(2)}%")
        logger.info("Average win amount ${backtestReport.averageWinAmount.format(2)}$")
        logger.info("Loss rate ${(backtestReport.lossRate * 100).format(2)}%")
        logger.info("Average loss amount ${backtestReport.averageLossAmount.format(2)}$")
        logger.info("The percentage you can expect to win per trade ${(backtestReport.edge).format(2)}%")
        logger.info("========================= Stock information:: =========================")
        logger.info("Exit reason count")
        val exitReasonCount = backtestReport.exitReasonCount
        exitReasonCount.forEach { (reason, count) -> logger.info("$reason count $count") }

        logger.info("Stocks ordered by profitability")
        backtestReport.stockProfits.forEach {
            logger.info("Symbol: ${it.first} profit ${it.second.format(2)}")
        }
    }

//    @Test
    fun loadOvtlyrData() {
        dataLoader.loadData()
    }

    fun generateTestDataAsString(stock: Stock, after: LocalDate, before: LocalDate): String{
        return stock.quotes
            .filter { it.date?.isAfter(after ) == true }
            .filter { it.date?.isBefore(before) == true }
            .map { """
            StockQuote(
                symbol = "${it.symbol}",
                date = LocalDate.of(${it.date?.year}, ${it.date?.monthValue}, ${it.date?.dayOfMonth}),
                openPrice = ${it.openPrice},
                heatmap = ${it.heatmap},
                previousHeatmap = ${it.previousHeatmap},
                sectorHeatmap = ${it.sectorHeatmap},
                previousSectorHeatmap = ${it.previousSectorHeatmap},
                sectorIsInUptrend = ${if(it.sectorIsInUptrend) "true" else "false" },
                signal = ${it.signal},
                closePrice = ${it.closePrice},
                closePriceEMA10 = ${it.closePriceEMA10},
                closePriceEMA5 = ${it.closePriceEMA5},
                closePriceEMA20 = ${it.closePriceEMA20},
                closePriceEMA50 = ${it.closePriceEMA50},
                trend = "${it.trend}",
                lastBuySignal = LocalDate.of(${it.lastBuySignal?.year}, ${it.lastBuySignal?.monthValue}, ${it.lastBuySignal?.dayOfMonth}),
                lastSellSignal = LocalDate.of(${it.lastSellSignal?.year}, ${it.lastSellSignal?.monthValue}, ${it.lastSellSignal?.dayOfMonth}),
                spySignal = "${it.spySignal}",
                spyIsInUptrend = ${if(it.spyInUptrend) "true" else "false" },
                marketIsInUptrend = ${if(it.marketIsInUptrend) "true" else "false" },
                previousQuoteDate = LocalDate.of(${it.previousQuoteDate?.year}, ${it.previousQuoteDate?.monthValue}, ${it.previousQuoteDate?.dayOfMonth}),
                atr = ${it.atr},
                sectorStocksInUptrend = ${it.sectorStocksInUptrend},
                sectorStocksInDowntrend = ${it.sectorStocksInUptrend},
                sectorBullPercentage = ${it.sectorBullPercentage},
                high = ${it.high},
                low = ${it.low}
        )
        """.trimIndent() }
            .reduce { s1,s2 -> s1+s2 }

    }
}
