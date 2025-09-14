package com.skrymer.udgaard.controller

import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.strategy.PlanAlphaEntryStrategy
import com.skrymer.udgaard.model.strategy.PlanAlphaExitStrategy
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.service.MarketBreadthService
import com.skrymer.udgaard.service.StockService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController()
@RequestMapping("/api")
class UdgaardController(
  val stockService: StockService,
  val marketBreadthService: MarketBreadthService
) {

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(UdgaardController::class.java)
  }

  @GetMapping("/report")
  @CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
  fun generateBacktestReport(
    @RequestParam(name = "stockSymbol") stockSymbol: String?,
    @RequestParam(name = "refresh") refresh: Boolean = false
  ): ResponseEntity<BacktestReport> {

    if (stockSymbol.isNullOrBlank()) {
      return ResponseEntity(HttpStatus.BAD_REQUEST)
    }
    val stock = stockService.getStock(stockSymbol.uppercase(), refresh)
    if (stock == null) {
      return ResponseEntity(HttpStatus.BAD_REQUEST)
    }

    val entryStrategy = PlanAlphaEntryStrategy()
    val exitStrategy = PlanAlphaExitStrategy()
    val backtestReport = stockService.backtest(
      entryStrategy,
      exitStrategy,
      listOf(stock),
      LocalDate.of(2020, 1, 1),
      LocalDate.now()
    )
    return ResponseEntity(backtestReport, HttpStatus.OK)
  }

  @GetMapping("/report/all")
  @CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
  fun generateBacktestReportForAllStocks(): ResponseEntity<BacktestReport> {
    logger.info("Generating report for all stocks started")
    val stocks = stockService.getAllStocks()
    logger.info("Stocks fetched")
    val entryStrategy = PlanAlphaEntryStrategy()
    val exitStrategy = PlanAlphaExitStrategy()
    val backtestReport = stockService.backtest(
      entryStrategy,
      exitStrategy,
      stocks,
      LocalDate.of(2020, 1, 1),
      LocalDate.now()
    )
    logger.info("Report generated")

    return ResponseEntity(backtestReport, HttpStatus.OK
    )
  }

  @GetMapping("/market-breadth")
  @CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
  fun getMarketBreadth(
    @RequestParam(name = "marketSymbol") marketSymbol: String?,
    @RequestParam(name = "refresh") refresh: Boolean = false
  ): ResponseEntity<MarketBreadth> {
    val marketBreadth = marketBreadthService.getMarketBreadth(
      marketSymbol = MarketSymbol.valueOf(marketSymbol),
      fromDate = LocalDate.now().minusMonths(3),
      toDate = LocalDate.now(),
      refresh = refresh
    )
    return ResponseEntity(marketBreadth, HttpStatus.OK)
  }

  @GetMapping("/stock")
  @CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
  fun getStock(
    @RequestParam(name = "symbol") symbol: String,
    @RequestParam(name = "refresh") refresh: Boolean = false
  ): ResponseEntity<Stock> {
    val marketBreadth = stockService.getStock(symbol, refresh)
    return ResponseEntity(marketBreadth, HttpStatus.OK)
  }
}