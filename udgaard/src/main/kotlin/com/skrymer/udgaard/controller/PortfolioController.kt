package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.model.*
import com.skrymer.udgaard.service.PortfolioService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/portfolio")
@CrossOrigin
class PortfolioController(
    private val portfolioService: PortfolioService
) {

    /**
     * Get all portfolios
     */
    @GetMapping
    fun getAllPortfolios(@RequestParam(required = false) userId: String?): ResponseEntity<List<Portfolio>> {
        val portfolios = portfolioService.getAllPortfolios(userId)
        return ResponseEntity.ok(portfolios)
    }

    /**
     * Create a new portfolio
     */
    @PostMapping
    fun createPortfolio(@RequestBody request: CreatePortfolioRequest): ResponseEntity<Portfolio> {
        val portfolio = portfolioService.createPortfolio(
            name = request.name,
            initialBalance = request.initialBalance,
            currency = request.currency,
            userId = request.userId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(portfolio)
    }

    /**
     * Get portfolio by ID
     */
    @GetMapping("/{portfolioId}")
    fun getPortfolio(@PathVariable portfolioId: String): ResponseEntity<Portfolio> {
        val portfolio = portfolioService.getPortfolio(portfolioId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(portfolio)
    }

    /**
     * Update portfolio balance
     */
    @PutMapping("/{portfolioId}")
    fun updatePortfolio(
        @PathVariable portfolioId: String,
        @RequestBody request: UpdatePortfolioRequest
    ): ResponseEntity<Portfolio> {
        val portfolio = portfolioService.updatePortfolio(portfolioId, request.currentBalance)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(portfolio)
    }

    /**
     * Delete portfolio
     */
    @DeleteMapping("/{portfolioId}")
    fun deletePortfolio(@PathVariable portfolioId: String): ResponseEntity<Void> {
        portfolioService.deletePortfolio(portfolioId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Get portfolio statistics
     */
    @GetMapping("/{portfolioId}/stats")
    fun getPortfolioStats(@PathVariable portfolioId: String): ResponseEntity<PortfolioStats> {
        val stats = portfolioService.calculateStats(portfolioId)
        return ResponseEntity.ok(stats)
    }

    /**
     * Open a new trade
     */
    @PostMapping("/{portfolioId}/trades")
    fun openTrade(
        @PathVariable portfolioId: String,
        @RequestBody request: OpenTradeRequest
    ): ResponseEntity<PortfolioTrade> {
        val trade = portfolioService.openTrade(
            portfolioId = portfolioId,
            symbol = request.symbol,
            entryPrice = request.entryPrice,
            entryDate = request.entryDate,
            quantity = request.quantity,
            entryStrategy = request.entryStrategy,
            exitStrategy = request.exitStrategy,
            currency = request.currency,
            underlyingSymbol = request.underlyingSymbol,
            instrumentType = request.instrumentType,
            optionType = request.optionType,
            strikePrice = request.strikePrice,
            expirationDate = request.expirationDate,
            contracts = request.contracts,
            multiplier = request.multiplier,
            entryIntrinsicValue = request.entryIntrinsicValue,
            entryExtrinsicValue = request.entryExtrinsicValue
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(trade)
    }

    /**
     * Close an existing trade
     */
    @PutMapping("/{portfolioId}/trades/{tradeId}/close")
    fun closeTrade(
        @PathVariable portfolioId: String,
        @PathVariable tradeId: String,
        @RequestBody request: CloseTradeRequest
    ): ResponseEntity<PortfolioTrade> {
        val trade = portfolioService.closeTrade(tradeId, request.exitPrice, request.exitDate)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(trade)
    }

    /**
     * Get all trades for a portfolio with exit signal information
     */
    @GetMapping("/{portfolioId}/trades")
    fun getTrades(
        @PathVariable portfolioId: String,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<List<PortfolioTradeResponse>> {
        val tradeStatus = status?.let { TradeStatus.valueOf(it.uppercase()) }
        val trades = portfolioService.getTrades(portfolioId, tradeStatus)

        // Add exit signal information for open trades
        val tradesWithSignals = trades.map { trade ->
            if (trade.status == TradeStatus.OPEN) {
                val (hasSignal, reason) = portfolioService.hasExitSignal(trade.id!!)
                PortfolioTradeResponse(trade, hasSignal, reason)
            } else {
                PortfolioTradeResponse(trade)
            }
        }

        return ResponseEntity.ok(tradesWithSignals)
    }

    /**
     * Get a specific trade
     */
    @GetMapping("/{portfolioId}/trades/{tradeId}")
    fun getTrade(
        @PathVariable portfolioId: String,
        @PathVariable tradeId: String
    ): ResponseEntity<PortfolioTrade> {
        val trade = portfolioService.getTrade(tradeId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(trade)
    }

    /**
     * Get equity curve data
     */
    @GetMapping("/{portfolioId}/equity-curve")
    fun getEquityCurve(@PathVariable portfolioId: String): ResponseEntity<EquityCurveData> {
        val data = portfolioService.getEquityCurve(portfolioId)
        return ResponseEntity.ok(data)
    }
}
