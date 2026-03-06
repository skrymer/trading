package com.skrymer.udgaard.portfolio.controller

import com.skrymer.udgaard.portfolio.dto.BrokerErrorResponse
import com.skrymer.udgaard.portfolio.dto.CreatePortfolioFromBrokerRequest
import com.skrymer.udgaard.portfolio.dto.CreatePortfolioRequest
import com.skrymer.udgaard.portfolio.dto.SyncPortfolioRequest
import com.skrymer.udgaard.portfolio.dto.TestBrokerConnectionRequest
import com.skrymer.udgaard.portfolio.dto.TestBrokerConnectionResponse
import com.skrymer.udgaard.portfolio.dto.UpdatePortfolioRequest
import com.skrymer.udgaard.portfolio.dto.toBrokerCredentials
import com.skrymer.udgaard.portfolio.model.CashTransaction
import com.skrymer.udgaard.portfolio.model.ForexDisposal
import com.skrymer.udgaard.portfolio.model.ForexLot
import com.skrymer.udgaard.portfolio.model.Portfolio
import com.skrymer.udgaard.portfolio.service.BrokerIntegrationService
import com.skrymer.udgaard.portfolio.service.CashTransactionService
import com.skrymer.udgaard.portfolio.service.ForexTrackingService
import com.skrymer.udgaard.portfolio.service.PortfolioService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for portfolio management
 * Position/Trade management is in PositionController
 */
@RestController
@RequestMapping("/api/portfolio")
class PortfolioController(
  private val portfolioService: PortfolioService,
  private val brokerIntegrationService: BrokerIntegrationService,
  private val forexTrackingService: ForexTrackingService,
  private val cashTransactionService: CashTransactionService,
) {
  /**
   * Get all portfolios
   */
  @GetMapping
  @Transactional(readOnly = true)
  fun getAllPortfolios(
    @RequestParam(required = false) userId: String?,
  ): ResponseEntity<List<Portfolio>> {
    val portfolios = portfolioService.getAllPortfolios(userId)
    return ResponseEntity.ok(portfolios)
  }

  /**
   * Create a new portfolio
   */
  @PostMapping
  fun createPortfolio(
    @Valid @RequestBody request: CreatePortfolioRequest,
  ): ResponseEntity<Portfolio> {
    val portfolio =
      portfolioService.createPortfolio(
        name = request.name,
        initialBalance = request.initialBalance,
        currency = request.currency,
        userId = request.userId,
      )
    return ResponseEntity.status(HttpStatus.CREATED).body(portfolio)
  }

  /**
   * Get portfolio by ID
   */
  @GetMapping("/{portfolioId}")
  @Transactional(readOnly = true)
  fun getPortfolio(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<Portfolio> {
    val portfolio =
      portfolioService.getPortfolio(portfolioId)
        ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(portfolio)
  }

  /**
   * Update portfolio balance
   */
  @PutMapping("/{portfolioId}")
  fun updatePortfolio(
    @PathVariable portfolioId: Long,
    @Valid @RequestBody request: UpdatePortfolioRequest,
  ): ResponseEntity<Portfolio> {
    val portfolio =
      portfolioService.updatePortfolio(portfolioId, request.currentBalance)
        ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(portfolio)
  }

  /**
   * Delete portfolio
   */
  @DeleteMapping("/{portfolioId}")
  fun deletePortfolio(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<Void> {
    portfolioService.deletePortfolio(portfolioId)
    return ResponseEntity.noContent().build()
  }

  /**
   * Create portfolio from broker data
   */
  @PostMapping("/import")
  fun createFromBroker(
    @RequestBody request: CreatePortfolioFromBrokerRequest,
  ): ResponseEntity<*> {
    val credentials = request.credentials.toBrokerCredentials(request.broker)
    val result =
      brokerIntegrationService.createPortfolioFromBroker(
        name = request.name,
        broker = request.broker,
        credentials = credentials,
        startDate = request.startDate,
        currency = request.currency,
        initialBalance = request.initialBalance,
      )
    return ResponseEntity.status(HttpStatus.CREATED).body(result)
  }

  /**
   * Sync portfolio with broker data
   */
  @PostMapping("/{portfolioId}/sync")
  fun syncPortfolio(
    @PathVariable portfolioId: Long,
    @RequestBody request: SyncPortfolioRequest,
  ): ResponseEntity<*> {
    val portfolio = portfolioService.getPortfolio(portfolioId)
      ?: return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(BrokerErrorResponse("Not Found", "Portfolio not found"))
    val credentials = request.credentials.toBrokerCredentials(portfolio.broker)
    val result = brokerIntegrationService.syncPortfolio(portfolioId, credentials)
    return ResponseEntity.ok(result)
  }

  /**
   * Get forex lots for a portfolio
   */
  @GetMapping("/{portfolioId}/forex/lots")
  @Transactional(readOnly = true)
  fun getForexLots(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<List<ForexLot>> {
    val lots = forexTrackingService.getForexLots(portfolioId)
    return ResponseEntity.ok(lots)
  }

  /**
   * Get forex disposals for a portfolio
   */
  @GetMapping("/{portfolioId}/forex/disposals")
  @Transactional(readOnly = true)
  fun getForexDisposals(
    @PathVariable portfolioId: Long,
    @RequestParam(required = false) startDate: java.time.LocalDate?,
    @RequestParam(required = false) endDate: java.time.LocalDate?,
  ): ResponseEntity<List<ForexDisposal>> {
    val disposals = if (startDate != null && endDate != null) {
      forexTrackingService.getForexDisposals(portfolioId, startDate, endDate)
    } else {
      forexTrackingService.getForexDisposals(portfolioId)
    }
    return ResponseEntity.ok(disposals)
  }

  /**
   * Get forex summary for a portfolio
   */
  @GetMapping("/{portfolioId}/forex/summary")
  @Transactional(readOnly = true)
  fun getForexSummary(
    @PathVariable portfolioId: Long,
    @RequestParam(required = false) startDate: java.time.LocalDate?,
    @RequestParam(required = false) endDate: java.time.LocalDate?,
  ): ResponseEntity<ForexSummary> {
    val lots = forexTrackingService.getForexLots(portfolioId)
    val disposals = if (startDate != null && endDate != null) {
      forexTrackingService.getForexDisposals(portfolioId, startDate, endDate)
    } else {
      forexTrackingService.getForexDisposals(portfolioId)
    }

    val totalRealizedFxPnl = disposals.sumOf { it.realizedFxPnl }
    val openLotsUsdBalance = lots
      .filter { it.status == com.skrymer.udgaard.portfolio.model.ForexLotStatus.OPEN }
      .sumOf { it.remainingQuantity }

    return ResponseEntity.ok(
      ForexSummary(
        totalRealizedFxPnl = totalRealizedFxPnl,
        openLotsCount = lots.count { it.status == com.skrymer.udgaard.portfolio.model.ForexLotStatus.OPEN },
        exhaustedLotsCount = lots.count { it.status == com.skrymer.udgaard.portfolio.model.ForexLotStatus.EXHAUSTED },
        disposalsCount = disposals.size,
        openUsdBalance = openLotsUsdBalance,
      ),
    )
  }

  @GetMapping("/{portfolioId}/cash-transactions")
  @Transactional(readOnly = true)
  fun getCashTransactions(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<List<CashTransaction>> {
    val transactions = cashTransactionService.getCashTransactions(portfolioId)
    return ResponseEntity.ok(transactions)
  }

  @GetMapping("/{portfolioId}/cash-transactions/summary")
  @Transactional(readOnly = true)
  fun getCashTransactionSummary(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<CashTransactionSummary> {
    val totalDeposits = cashTransactionService.getTotalDeposits(portfolioId)
    val totalWithdrawals = cashTransactionService.getTotalWithdrawals(portfolioId)
    return ResponseEntity.ok(
      CashTransactionSummary(
        totalDeposits = totalDeposits,
        totalWithdrawals = totalWithdrawals,
        netCashFlow = totalDeposits - totalWithdrawals,
      ),
    )
  }

  /**
   * Test broker connection
   */
  @PostMapping("/broker/test")
  fun testBrokerConnection(
    @RequestBody request: TestBrokerConnectionRequest,
  ): ResponseEntity<TestBrokerConnectionResponse> {
    try {
      val credentials = request.credentials.toBrokerCredentials(request.broker)
      val success = brokerIntegrationService.testConnection(request.broker, credentials)
      return ResponseEntity.ok(
        TestBrokerConnectionResponse(
          success = success,
          message = if (success) "Connection successful" else "Connection failed",
        ),
      )
    } catch (e: Exception) {
      return ResponseEntity.ok(
        TestBrokerConnectionResponse(
          success = false,
          message = "Error: ${e.message}",
        ),
      )
    }
  }
}

data class CashTransactionSummary(
  val totalDeposits: Double,
  val totalWithdrawals: Double,
  val netCashFlow: Double,
)

data class ForexSummary(
  val totalRealizedFxPnl: Double,
  val openLotsCount: Int,
  val exhaustedLotsCount: Int,
  val disposalsCount: Int,
  val openUsdBalance: Double,
)
