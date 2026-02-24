package com.skrymer.udgaard.portfolio.controller

import com.skrymer.udgaard.portfolio.dto.BrokerErrorResponse
import com.skrymer.udgaard.portfolio.dto.CreatePortfolioFromBrokerRequest
import com.skrymer.udgaard.portfolio.dto.CreatePortfolioRequest
import com.skrymer.udgaard.portfolio.dto.SyncPortfolioRequest
import com.skrymer.udgaard.portfolio.dto.TestBrokerConnectionRequest
import com.skrymer.udgaard.portfolio.dto.TestBrokerConnectionResponse
import com.skrymer.udgaard.portfolio.dto.UpdatePortfolioRequest
import com.skrymer.udgaard.portfolio.dto.toBrokerCredentials
import com.skrymer.udgaard.portfolio.model.Portfolio
import com.skrymer.udgaard.portfolio.service.BrokerIntegrationService
import com.skrymer.udgaard.portfolio.service.PortfolioService
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
    @RequestBody request: CreatePortfolioRequest,
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
    @RequestBody request: UpdatePortfolioRequest,
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
    try {
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
    } catch (e: IllegalArgumentException) {
      return ResponseEntity
        .badRequest()
        .body(BrokerErrorResponse("Bad Request", e.message ?: "Invalid request parameters"))
    } catch (e: Exception) {
      return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(BrokerErrorResponse("Internal Server Error", e.message ?: "An unexpected error occurred"))
    }
  }

  /**
   * Sync portfolio with broker data
   */
  @PostMapping("/{portfolioId}/sync")
  fun syncPortfolio(
    @PathVariable portfolioId: Long,
    @RequestBody request: SyncPortfolioRequest,
  ): ResponseEntity<*> {
    try {
      val portfolio = portfolioService.getPortfolio(portfolioId)
      if (portfolio == null) {
        return ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .body(BrokerErrorResponse("Not Found", "Portfolio not found"))
      }
      val credentials = request.credentials.toBrokerCredentials(portfolio.broker)
      val result = brokerIntegrationService.syncPortfolio(portfolioId, credentials)
      return ResponseEntity.ok(result)
    } catch (e: IllegalArgumentException) {
      return ResponseEntity
        .badRequest()
        .body(BrokerErrorResponse("Bad Request", e.message ?: "Invalid request parameters"))
    } catch (e: Exception) {
      return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(BrokerErrorResponse("Internal Server Error", e.message ?: "An unexpected error occurred"))
    }
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
