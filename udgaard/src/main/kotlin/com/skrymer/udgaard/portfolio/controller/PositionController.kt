package com.skrymer.udgaard.portfolio.controller

import com.skrymer.udgaard.portfolio.dto.ClosePositionRequest
import com.skrymer.udgaard.portfolio.dto.CreatePositionRequest
import com.skrymer.udgaard.portfolio.dto.PositionUnrealizedPnlResponse
import com.skrymer.udgaard.portfolio.dto.PositionWithExecutionsResponse
import com.skrymer.udgaard.portfolio.dto.UpdatePositionMetadataRequest
import com.skrymer.udgaard.portfolio.model.EquityCurveData
import com.skrymer.udgaard.portfolio.model.Portfolio
import com.skrymer.udgaard.portfolio.model.Position
import com.skrymer.udgaard.portfolio.model.PositionStats
import com.skrymer.udgaard.portfolio.model.PositionStatus
import com.skrymer.udgaard.portfolio.service.PositionService
import com.skrymer.udgaard.portfolio.service.UnrealizedPnlService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
 * REST controller for position management
 */
@RestController
@RequestMapping("/api/positions")
class PositionController(
  private val positionService: PositionService,
  private val unrealizedPnlService: UnrealizedPnlService,
) {
  /**
   * Get all positions for a portfolio
   */
  @GetMapping("/{portfolioId}")
  @Transactional(readOnly = true)
  fun getPositions(
    @PathVariable portfolioId: Long,
    @RequestParam(required = false) status: String?,
  ): ResponseEntity<List<Position>> {
    val positionStatus = status?.let { PositionStatus.valueOf(it.uppercase()) }
    val positions = positionService.getPositions(portfolioId, positionStatus)
    return ResponseEntity.ok(positions)
  }

  /**
   * Get a specific position with all its executions
   */
  @GetMapping("/{portfolioId}/{positionId}")
  @Transactional(readOnly = true)
  @Suppress("detekt:UnusedParameter")
  fun getPosition(
    @PathVariable portfolioId: Long,
    @PathVariable positionId: Long,
  ): ResponseEntity<PositionWithExecutionsResponse> = try {
    val positionWithExecutions = positionService.getPositionWithExecutions(positionId)
    ResponseEntity.ok(PositionWithExecutionsResponse.from(positionWithExecutions))
  } catch (e: IllegalArgumentException) {
    logger.debug("Position not found: $positionId", e)
    ResponseEntity.notFound().build()
  }

  /**
   * Create a manual position
   */
  @PostMapping("/{portfolioId}")
  fun createPosition(
    @PathVariable portfolioId: Long,
    @RequestBody request: CreatePositionRequest,
  ): ResponseEntity<Position> {
    val position =
      positionService.createManualPosition(
        portfolioId = portfolioId,
        symbol = request.symbol,
        instrumentType = request.instrumentType,
        quantity = request.quantity,
        entryPrice = request.entryPrice,
        entryDate = request.entryDate,
        entryStrategy = request.entryStrategy,
        exitStrategy = request.exitStrategy,
        currency = request.currency,
        underlyingSymbol = request.underlyingSymbol,
        optionType = request.optionType,
        strikePrice = request.strikePrice,
        expirationDate = request.expirationDate,
        multiplier = request.multiplier,
      )
    return ResponseEntity.status(HttpStatus.CREATED).body(position)
  }

  /**
   * Close a position
   */
  @PutMapping("/{portfolioId}/{positionId}/close")
  @Suppress("detekt:UnusedParameter")
  fun closePosition(
    @PathVariable portfolioId: Long,
    @PathVariable positionId: Long,
    @RequestBody request: ClosePositionRequest,
  ): ResponseEntity<Position> = try {
    val position =
      positionService.closeManualPosition(
        positionId = positionId,
        exitPrice = request.exitPrice,
        exitDate = request.exitDate,
      )
    ResponseEntity.ok(position)
  } catch (e: IllegalArgumentException) {
    logger.warn("Failed to close position $positionId: ${e.message}", e)
    ResponseEntity.badRequest().build()
  }

  /**
   * Update position metadata (strategies, notes)
   */
  @PutMapping("/{portfolioId}/{positionId}/metadata")
  @Suppress("detekt:UnusedParameter")
  fun updatePositionMetadata(
    @PathVariable portfolioId: Long,
    @PathVariable positionId: Long,
    @RequestBody request: UpdatePositionMetadataRequest,
  ): ResponseEntity<Position> = try {
    val position =
      positionService.updatePositionMetadata(
        positionId = positionId,
        entryStrategy = request.entryStrategy,
        exitStrategy = request.exitStrategy,
        notes = request.notes,
      )
    ResponseEntity.ok(position)
  } catch (e: IllegalArgumentException) {
    logger.debug("Position not found for metadata update: $positionId", e)
    ResponseEntity.notFound().build()
  }

  /**
   * Delete a position
   */
  @DeleteMapping("/{portfolioId}/{positionId}")
  @Suppress("detekt:UnusedParameter")
  fun deletePosition(
    @PathVariable portfolioId: Long,
    @PathVariable positionId: Long,
  ): ResponseEntity<Void> {
    positionService.deletePosition(positionId)
    return ResponseEntity.noContent().build()
  }

  /**
   * Get portfolio statistics
   */
  @GetMapping("/{portfolioId}/stats")
  @Transactional(readOnly = true)
  fun getPortfolioStats(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<PositionStats> {
    val stats = positionService.calculateStats(portfolioId)
    return ResponseEntity.ok(stats)
  }

  /**
   * Calculate unrealized P&L for all open positions
   *
   * Fetches current market prices and calculates unrealized gains/losses.
   * This operation may be slow due to API calls for each position.
   */
  @GetMapping("/{portfolioId}/unrealized-pnl")
  fun getUnrealizedPnl(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<List<PositionUnrealizedPnlResponse>> {
    val unrealizedPnl = unrealizedPnlService.calculateUnrealizedPnl(portfolioId)
    return ResponseEntity.ok(unrealizedPnl)
  }

  /**
   * Get equity curve for portfolio
   */
  @GetMapping("/{portfolioId}/equity-curve")
  @Transactional(readOnly = true)
  fun getEquityCurve(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<EquityCurveData> {
    val equityCurve = positionService.getEquityCurve(portfolioId)
    return ResponseEntity.ok(equityCurve)
  }

  /**
   * Recalculate portfolio balance from closed positions
   */
  @PostMapping("/{portfolioId}/recalculate-balance")
  fun recalculateBalance(
    @PathVariable portfolioId: Long,
  ): ResponseEntity<Portfolio> {
    val portfolio = positionService.recalculatePortfolioBalance(portfolioId)
    return ResponseEntity.ok(portfolio)
  }

  /**
   * Get roll chain for a position
   */
  @GetMapping("/{portfolioId}/{positionId}/roll-chain")
  @Transactional(readOnly = true)
  @Suppress("detekt:UnusedParameter")
  fun getRollChain(
    @PathVariable portfolioId: Long,
    @PathVariable positionId: Long,
  ): ResponseEntity<List<Position>> = try {
    val chain = positionService.getRollChain(positionId)
    ResponseEntity.ok(chain)
  } catch (e: IllegalArgumentException) {
    logger.debug("Position not found for roll chain: $positionId", e)
    ResponseEntity.notFound().build()
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(PositionController::class.java)
  }
}
