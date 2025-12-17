package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.domain.*
import com.skrymer.udgaard.model.*
import com.skrymer.udgaard.service.PortfolioService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/portfolio")
@CrossOrigin
class PortfolioController(
  private val portfolioService: PortfolioService,
) {
  /**
   * Get all portfolios
   */
  @GetMapping
  @Transactional(readOnly = true)
  fun getAllPortfolios(
    @RequestParam(required = false) userId: String?,
  ): ResponseEntity<List<PortfolioDomain>> {
    val portfolios = portfolioService.getAllPortfolios(userId)
    return ResponseEntity.ok(portfolios)
  }

  /**
   * Create a new portfolio
   */
  @PostMapping
  fun createPortfolio(
    @RequestBody request: CreatePortfolioRequest,
  ): ResponseEntity<PortfolioDomain> {
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
  ): ResponseEntity<PortfolioDomain> {
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
  ): ResponseEntity<PortfolioDomain> {
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
   * Get portfolio statistics
   * @param groupRolledTrades - If true, treat roll chains as single trades (use cumulative P&L)
   */
  @GetMapping("/{portfolioId}/stats")
  @Transactional(readOnly = true)
  fun getPortfolioStats(
    @PathVariable portfolioId: Long,
    @RequestParam(required = false, defaultValue = "false") groupRolledTrades: Boolean,
  ): ResponseEntity<PortfolioStats> {
    val stats = portfolioService.calculateStats(portfolioId, groupRolledTrades)
    return ResponseEntity.ok(stats)
  }

  /**
   * Open a new trade
   */
  @PostMapping("/{portfolioId}/trades")
  fun openTrade(
    @PathVariable portfolioId: Long,
    @RequestBody request: OpenTradeRequest,
  ): ResponseEntity<PortfolioTradeDomain> {
    val trade =
      portfolioService.openTrade(
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
        entryExtrinsicValue = request.entryExtrinsicValue,
      )
    return ResponseEntity.status(HttpStatus.CREATED).body(trade)
  }

  /**
   * Update an existing open trade
   */
  @PutMapping("/{portfolioId}/trades/{tradeId}")
  fun updateTrade(
    @PathVariable portfolioId: Long,
    @PathVariable tradeId: Long,
    @RequestBody request: UpdateTradeRequest,
  ): ResponseEntity<PortfolioTradeDomain> {
    try {
      val trade =
        portfolioService.updateTrade(
          tradeId = tradeId,
          symbol = request.symbol,
          entryPrice = request.entryPrice,
          entryDate = request.entryDate,
          quantity = request.quantity,
          entryStrategy = request.entryStrategy,
          exitStrategy = request.exitStrategy,
          underlyingSymbol = request.underlyingSymbol,
          instrumentType = request.instrumentType,
          optionType = request.optionType,
          strikePrice = request.strikePrice,
          expirationDate = request.expirationDate,
          contracts = request.contracts,
          multiplier = request.multiplier,
          entryIntrinsicValue = request.entryIntrinsicValue,
          entryExtrinsicValue = request.entryExtrinsicValue,
        ) ?: return ResponseEntity.notFound().build()
      return ResponseEntity.ok(trade)
    } catch (e: IllegalArgumentException) {
      return ResponseEntity.badRequest().build()
    }
  }

  /**
   * Close an existing trade
   */
  @PutMapping("/{portfolioId}/trades/{tradeId}/close")
  fun closeTrade(
    @PathVariable portfolioId: Long,
    @PathVariable tradeId: Long,
    @RequestBody request: CloseTradeRequest,
  ): ResponseEntity<PortfolioTradeDomain> {
    val trade =
      portfolioService.closeTrade(
        tradeId,
        request.exitPrice,
        request.exitDate,
        request.exitIntrinsicValue,
        request.exitExtrinsicValue,
      ) ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(trade)
  }

  /**
   * Get all trades for a portfolio
   */
  @GetMapping("/{portfolioId}/trades")
  @Transactional(readOnly = true)
  fun getTrades(
    @PathVariable portfolioId: Long,
    @RequestParam(required = false) status: String?,
  ): ResponseEntity<List<PortfolioTradeDomain>> {
    val tradeStatus = status?.let { TradeStatus.valueOf(it.uppercase()) }
    val trades = portfolioService.getTrades(portfolioId, tradeStatus)
    return ResponseEntity.ok(trades)
  }

  /**
   * Get a specific trade
   */
  @GetMapping("/{portfolioId}/trades/{tradeId}")
  @Transactional(readOnly = true)
  fun getTrade(
    @PathVariable portfolioId: Long,
    @PathVariable tradeId: Long,
  ): ResponseEntity<PortfolioTradeDomain> {
    val trade =
      portfolioService.getTrade(tradeId)
        ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(trade)
  }

  /**
   * Delete a trade (only open trades can be deleted)
   */
  @DeleteMapping("/{portfolioId}/trades/{tradeId}")
  fun deleteTrade(
    @PathVariable portfolioId: Long,
    @PathVariable tradeId: Long,
  ): ResponseEntity<Void> {
    try {
      val deleted = portfolioService.deleteTrade(tradeId)
      return if (deleted) {
        ResponseEntity.noContent().build()
      } else {
        ResponseEntity.notFound().build()
      }
    } catch (e: IllegalArgumentException) {
      return ResponseEntity.badRequest().build()
    }
  }

  /**
   * Get equity curve data
   */
  @GetMapping("/{portfolioId}/equity-curve")
  @Transactional(readOnly = true)
  fun getEquityCurve(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<EquityCurveData> {
    val data = portfolioService.getEquityCurve(portfolioId)
    return ResponseEntity.ok(data)
  }

  /**
   * Roll an options position to a new strike/expiration
   */
  @PostMapping("/{portfolioId}/trades/{tradeId}/roll")
  fun rollTrade(
    @PathVariable portfolioId: Long,
    @PathVariable tradeId: Long,
    @RequestBody request: RollTradeRequest,
  ): ResponseEntity<RollTradeResponse> {
    try {
      val (closedTrade, newTrade) =
        portfolioService.rollTrade(
          tradeId = tradeId,
          newSymbol = request.newSymbol,
          newStrikePrice = request.newStrikePrice,
          newExpirationDate = request.newExpirationDate,
          newOptionType = request.newOptionType,
          newEntryPrice = request.newEntryPrice,
          rollDate = request.rollDate,
          contracts = request.contracts,
          exitPrice = request.exitPrice,
        )

      // Calculate roll cost from the two trades
      val exitValue = (closedTrade.exitPrice ?: 0.0) * (closedTrade.contracts ?: closedTrade.quantity) * closedTrade.multiplier
      val newEntryCost = newTrade.entryPrice * (newTrade.contracts ?: newTrade.quantity) * newTrade.multiplier
      val rollCost = newEntryCost - exitValue

      return ResponseEntity.ok(
        RollTradeResponse(
          closedTrade = closedTrade,
          newTrade = newTrade,
          rollCost = rollCost,
        ),
      )
    } catch (e: IllegalArgumentException) {
      return ResponseEntity.badRequest().build()
    } catch (e: IllegalStateException) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
  }

  /**
   * Get the complete roll chain for a trade
   */
  @GetMapping("/{portfolioId}/trades/{tradeId}/roll-chain")
  @Transactional(readOnly = true)
  fun getRollChain(
    @PathVariable portfolioId: Long,
    @PathVariable tradeId: Long,
  ): ResponseEntity<RollChainResponse> {
    val chain = portfolioService.getRollChain(tradeId)
    return ResponseEntity.ok(RollChainResponse(trades = chain))
  }
}
