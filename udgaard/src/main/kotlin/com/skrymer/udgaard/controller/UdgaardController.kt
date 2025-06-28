package com.skrymer.udgaard.controller

import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.strategy.MainExitStrategy
import com.skrymer.udgaard.model.strategy.Ovtlyr9EntryStrategy
import com.skrymer.udgaard.service.StockService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController()
@RequestMapping("/api")
class UdgaardController(val stockService: StockService) {

    @GetMapping("/report")
    @CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
    fun generateBacktestReport(@RequestParam(name = "stock", defaultValue = "nvda") stock: String): ResponseEntity<BacktestReport>{
      val entryStrategy = Ovtlyr9EntryStrategy()
      val exitStrategy = MainExitStrategy()

      val backtestReport = stockService.backtest(entryStrategy, exitStrategy, listOf(stockService.getStock(stock)))
      return ResponseEntity(backtestReport, HttpStatus.OK)
    }
}