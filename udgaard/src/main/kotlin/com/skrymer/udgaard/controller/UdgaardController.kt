package com.skrymer.udgaard.controller

import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.strategy.MainExitStrategy
import com.skrymer.udgaard.model.strategy.Ovtlyr9EntryStrategy
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.service.MarketBreadthService
import com.skrymer.udgaard.service.StockService
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

    val entryStrategy = Ovtlyr9EntryStrategy()
    val exitStrategy = MainExitStrategy()
    val backtestReport = stockService.backtest(
      entryStrategy,
      exitStrategy,
      listOf(stock),
      LocalDate.of(2020, 1, 1),
      LocalDate.now()
    )
    return ResponseEntity(backtestReport, HttpStatus.OK)
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
}