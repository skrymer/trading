package com.skrymer.udgaard.scanner.controller

import com.skrymer.udgaard.portfolio.integration.options.OptionsDataProvider
import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.OptionContractResponse
import com.skrymer.udgaard.scanner.dto.OptionContractsRequest
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/scanner")
class ScannerController(
  private val scannerService: ScannerService,
  private val optionsDataProvider: OptionsDataProvider,
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

  @PostMapping("/option-contracts")
  fun getOptionContracts(
    @RequestBody request: OptionContractsRequest,
  ): ResponseEntity<Map<String, OptionContractResponse>> {
    val symbols = request.symbols.take(MAX_OPTION_SYMBOLS)
    logger.info("Fetching option contracts for ${symbols.size} symbols")

    val optionDate = request.date ?: LocalDate.now().toString()
    val minExpiration = LocalDate.parse(optionDate).plusMonths(1)
    val results = symbols
      .mapNotNull { symbol ->
        findBestOptionContract(symbol, request.stockPrices[symbol], optionDate, minExpiration)
      }.associateBy { it.symbol }

    logger.info("Fetched option contracts for ${results.size}/${symbols.size} symbols")
    return ResponseEntity.ok(results)
  }

  private fun findBestOptionContract(
    symbol: String,
    stockPrice: Double?,
    date: String,
    minExpiration: LocalDate,
  ): OptionContractResponse? {
    if (stockPrice == null) return null
    val contracts = optionsDataProvider.getHistoricalOptions(symbol, date) ?: return null

    val bestContract = contracts
      .filter { c ->
        c.optionType == com.skrymer.udgaard.portfolio.model.OptionType.CALL &&
          c.delta != null &&
          c.delta in DELTA_MIN..DELTA_MAX &&
          c.expiration >= minExpiration &&
          c.price > 0
      }.groupBy { it.expiration }
      .minByOrNull { (exp, _) -> exp }
      ?.value
      ?.minByOrNull { kotlin.math.abs((it.delta ?: 0.0) - DELTA_TARGET) }
      ?: return null

    val intrinsic = maxOf(stockPrice - bestContract.strike, 0.0)
    val extrinsic = bestContract.price - intrinsic

    return OptionContractResponse(
      symbol = symbol,
      strike = bestContract.strike,
      expiration = bestContract.expiration.toString(),
      price = bestContract.price,
      delta = bestContract.delta ?: DELTA_TARGET,
      openInterest = bestContract.openInterest,
      intrinsic = intrinsic,
      extrinsic = extrinsic,
    )
  }

  companion object {
    private const val MAX_OPTION_SYMBOLS = 20
    private const val DELTA_MIN = 0.70
    private const val DELTA_MAX = 0.90
    private const val DELTA_TARGET = 0.80
  }
}
