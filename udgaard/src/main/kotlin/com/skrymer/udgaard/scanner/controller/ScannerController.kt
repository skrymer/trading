package com.skrymer.udgaard.scanner.controller

import com.skrymer.udgaard.portfolio.integration.options.OptionsDataProvider
import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.CloseScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.DrawdownStatsResponse
import com.skrymer.udgaard.scanner.dto.OptionContractResponse
import com.skrymer.udgaard.scanner.dto.OptionContractsRequest
import com.skrymer.udgaard.scanner.dto.RollScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ScanRequest
import com.skrymer.udgaard.scanner.dto.UpdateScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ValidateEntriesRequest
import com.skrymer.udgaard.scanner.model.EntryValidationResponse
import com.skrymer.udgaard.scanner.model.ExitCheckResponse
import com.skrymer.udgaard.scanner.model.ScanResponse
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.service.CohortDivergenceReport
import com.skrymer.udgaard.scanner.service.CohortDivergenceService
import com.skrymer.udgaard.scanner.service.DivergenceConfig
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/scanner")
class ScannerController(
  private val scannerService: ScannerService,
  private val optionsDataProvider: OptionsDataProvider,
  private val cohortDivergenceService: CohortDivergenceService,
) {
  private val logger = LoggerFactory.getLogger(ScannerController::class.java)

  @PostMapping("/scan")
  fun scan(
    @RequestBody request: ScanRequest,
  ): ResponseEntity<ScanResponse> {
    logger.info("Running scan: entry=${request.entryStrategyName}, exit=${request.exitStrategyName}")
    val response = scannerService.scan(request)
    logger.info("Scan complete: ${response.results.size} matches from ${response.totalStocksScanned} stocks")
    return ResponseEntity.ok(response)
  }

  @PostMapping("/check-exits")
  fun checkExits(): ResponseEntity<ExitCheckResponse> {
    logger.info("Checking exit signals for scanner trades")
    val response = scannerService.checkExits()
    logger.info("Exit check: ${response.checksPerformed} checked, ${response.exitsTriggered} triggered")
    return ResponseEntity.ok(response)
  }

  @PostMapping("/validate-entries")
  fun validateEntries(
    @RequestBody request: ValidateEntriesRequest,
  ): ResponseEntity<EntryValidationResponse> {
    logger.info("Validating ${request.symbols.size} entries: entry=${request.entryStrategyName}, exit=${request.exitStrategyName}")
    val response = scannerService.validateEntries(request)
    logger.info("Validation complete: ${response.validCount} valid, ${response.invalidCount} invalid, ${response.doaCount} DOA")
    return ResponseEntity.ok(response)
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
    val trade = scannerService.addTrade(request)
    return ResponseEntity.status(HttpStatus.CREATED).body(trade)
  }

  @PutMapping("/trades/{id}")
  fun updateTrade(
    @PathVariable id: Long,
    @RequestBody request: UpdateScannerTradeRequest,
  ): ResponseEntity<ScannerTrade> {
    logger.info("Updating scanner trade $id")
    return ResponseEntity.ok(scannerService.updateTrade(id, request))
  }

  @PutMapping("/trades/{id}/close")
  fun closeTrade(
    @PathVariable id: Long,
    @RequestBody request: CloseScannerTradeRequest,
  ): ResponseEntity<ScannerTrade> {
    logger.info("Closing scanner trade $id")
    return ResponseEntity.ok(scannerService.closeTrade(id, request))
  }

  @DeleteMapping("/trades/{id}")
  fun deleteTrade(
    @PathVariable id: Long,
  ): ResponseEntity<Void> {
    logger.info("Deleting scanner trade $id")
    scannerService.deleteTrade(id)
    return ResponseEntity.noContent().build()
  }

  @PostMapping("/trades/reset")
  fun deleteAllTrades(): ResponseEntity<Map<String, Int>> {
    logger.info("Deleting all scanner trades")
    val count = scannerService.deleteAllTrades()
    return ResponseEntity.ok(mapOf("deleted" to count))
  }

  @GetMapping("/trades/closed")
  fun getClosedTrades(): ResponseEntity<List<ScannerTrade>> {
    val trades = scannerService.getClosedTrades()
    return ResponseEntity.ok(trades)
  }

  @GetMapping("/trades/closed/stats")
  fun getClosedTradeStats() = ResponseEntity.ok(scannerService.getClosedTradeStats())

  @GetMapping("/drawdown-stats")
  fun getDrawdownStats(): ResponseEntity<DrawdownStatsResponse> =
    ResponseEntity.ok(scannerService.getDrawdownStats())

  @GetMapping("/cohort-divergence")
  fun getCohortDivergence(
    @RequestParam entryStrategy: String,
    @RequestParam exitStrategy: String,
    @RequestParam(defaultValue = "SectorEdgeWithTightness") ranker: String,
    @RequestParam(defaultValue = "20") windowDays: Int,
  ): ResponseEntity<CohortDivergenceReport> =
    ResponseEntity.ok(
      cohortDivergenceService.compute(DivergenceConfig(entryStrategy, exitStrategy, ranker, windowDays)),
    )

  @PostMapping("/trades/{id}/roll")
  fun rollTrade(
    @PathVariable id: Long,
    @RequestBody request: RollScannerTradeRequest,
  ): ResponseEntity<ScannerTrade> {
    logger.info("Rolling scanner trade $id")
    return ResponseEntity.ok(scannerService.rollTrade(id, request))
  }

  @PostMapping("/option-contracts")
  fun getOptionContracts(
    @RequestBody request: OptionContractsRequest,
  ): ResponseEntity<Map<String, OptionContractResponse>> {
    require(request.symbols.size <= MAX_OPTION_SYMBOLS) {
      "Too many symbols: ${request.symbols.size} (max $MAX_OPTION_SYMBOLS). Chunk on the client."
    }
    val symbols = request.symbols
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
