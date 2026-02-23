package com.skrymer.udgaard.scanner.controller

import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.RollScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ScanRequest
import com.skrymer.udgaard.scanner.dto.UpdateScannerTradeRequest
import com.skrymer.udgaard.scanner.model.ExitCheckResponse
import com.skrymer.udgaard.scanner.model.ScanResponse
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.service.ScannerService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/scanner")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class ScannerController(
  private val scannerService: ScannerService,
) {
  private val logger = LoggerFactory.getLogger(ScannerController::class.java)

  @PostMapping("/scan")
  fun scan(
    @RequestBody request: ScanRequest,
  ): ResponseEntity<ScanResponse> {
    logger.info("Running scan: entry=${request.entryStrategyName}, exit=${request.exitStrategyName}")
    return try {
      val response = scannerService.scan(request)
      logger.info("Scan complete: ${response.results.size} matches from ${response.totalStocksScanned} stocks")
      ResponseEntity.ok(response)
    } catch (e: IllegalArgumentException) {
      logger.error("Scan failed: ${e.message}")
      ResponseEntity.badRequest().build()
    } catch (e: Exception) {
      logger.error("Unexpected error during scan: ${e.message}", e)
      ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
  }

  @PostMapping("/check-exits")
  fun checkExits(): ResponseEntity<ExitCheckResponse> {
    logger.info("Checking exit signals for scanner trades")
    return try {
      val response = scannerService.checkExits()
      logger.info("Exit check: ${response.checksPerformed} checked, ${response.exitsTriggered} triggered")
      ResponseEntity.ok(response)
    } catch (e: Exception) {
      logger.error("Error checking exits: ${e.message}", e)
      ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
  }

  @GetMapping("/trades")
  fun getTrades(): ResponseEntity<List<ScannerTrade>> {
    logger.info("Fetching scanner trades")
    val trades = scannerService.getTrades()
    logger.info("Returning ${trades.size} scanner trades")
    return ResponseEntity.ok(trades)
  }

  @PostMapping("/trades")
  fun addTrade(
    @RequestBody request: AddScannerTradeRequest,
  ): ResponseEntity<ScannerTrade> {
    logger.info("Adding scanner trade for ${request.symbol}")
    return try {
      val trade = scannerService.addTrade(request)
      ResponseEntity.status(HttpStatus.CREATED).body(trade)
    } catch (e: IllegalArgumentException) {
      logger.error("Failed to add scanner trade: ${e.message}")
      ResponseEntity.badRequest().build()
    }
  }

  @PutMapping("/trades/{id}")
  fun updateTrade(
    @PathVariable id: Long,
    @RequestBody request: UpdateScannerTradeRequest,
  ): ResponseEntity<ScannerTrade> {
    logger.info("Updating scanner trade $id")
    return try {
      val trade = scannerService.updateTrade(id, request)
      ResponseEntity.ok(trade)
    } catch (e: IllegalArgumentException) {
      logger.error("Scanner trade not found: $id - ${e.message}")
      ResponseEntity.notFound().build()
    }
  }

  @DeleteMapping("/trades/{id}")
  fun deleteTrade(
    @PathVariable id: Long,
  ): ResponseEntity<Void> {
    logger.info("Deleting scanner trade $id")
    scannerService.deleteTrade(id)
    return ResponseEntity.noContent().build()
  }

  @PostMapping("/trades/{id}/roll")
  fun rollTrade(
    @PathVariable id: Long,
    @RequestBody request: RollScannerTradeRequest,
  ): ResponseEntity<ScannerTrade> {
    logger.info("Rolling scanner trade $id")
    return try {
      val trade = scannerService.rollTrade(id, request)
      ResponseEntity.ok(trade)
    } catch (e: IllegalArgumentException) {
      logger.error("Failed to roll scanner trade: ${e.message}")
      ResponseEntity.badRequest().build()
    }
  }
}
