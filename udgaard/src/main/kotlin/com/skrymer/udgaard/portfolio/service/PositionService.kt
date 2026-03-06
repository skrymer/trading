package com.skrymer.udgaard.portfolio.service

import com.skrymer.udgaard.portfolio.model.Execution
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.portfolio.model.Position
import com.skrymer.udgaard.portfolio.model.PositionSource
import com.skrymer.udgaard.portfolio.model.PositionStatus
import com.skrymer.udgaard.portfolio.model.PositionWithExecutions
import com.skrymer.udgaard.portfolio.repository.ExecutionJooqRepository
import com.skrymer.udgaard.portfolio.repository.PortfolioJooqRepository
import com.skrymer.udgaard.portfolio.repository.PositionJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

@Service
class PositionService(
  private val positionRepository: PositionJooqRepository,
  private val executionRepository: ExecutionJooqRepository,
  private val portfolioRepository: PortfolioJooqRepository,
) {
  private val logger = LoggerFactory.getLogger(PositionService::class.java)

  // ===========================================
  // BROKER IMPORT OPERATIONS
  // ===========================================

  /**
   * Find or create position for broker import
   * Looks for existing OPEN position matching characteristics
   */
  fun findOrCreatePosition(
    portfolioId: Long,
    symbol: String,
    instrumentType: InstrumentType,
    underlyingSymbol: String?,
    optionType: OptionType?,
    strikePrice: Double?,
    expirationDate: LocalDate?,
    entryStrategy: String,
    exitStrategy: String,
    currency: String,
    openedDate: LocalDate? = null,
  ): Position {
    // Try to find existing OPEN position with matching characteristics
    val existing =
      positionRepository.findOpenPositionByCharacteristics(
        portfolioId,
        symbol,
        strikePrice,
        expirationDate,
        optionType,
      )

    if (existing != null) {
      logger.debug("Found existing position ${existing.id} for $symbol")
      return existing
    }

    // Create new position
    logger.info("Creating new position for $symbol")
    val newPosition =
      Position(
        id = null,
        portfolioId = portfolioId,
        symbol = symbol,
        underlyingSymbol = underlyingSymbol,
        instrumentType = instrumentType,
        optionType = optionType,
        strikePrice = strikePrice,
        expirationDate = expirationDate,
        multiplier = if (instrumentType == InstrumentType.OPTION) 100 else 1,
        currentQuantity = 0,
        currentContracts = if (instrumentType == InstrumentType.OPTION) 0 else null,
        averageEntryPrice = 0.0,
        totalCost = 0.0,
        status = PositionStatus.OPEN,
        openedDate = openedDate ?: LocalDate.now(),
        closedDate = null,
        realizedPnl = null,
        rolledToPositionId = null,
        parentPositionId = null,
        rollNumber = 0,
        entryStrategy = entryStrategy,
        exitStrategy = exitStrategy,
        notes = null,
        currency = currency,
        source = PositionSource.BROKER,
      )

    return positionRepository.save(newPosition)
  }

  /**
   * Add execution to position and recalculate aggregates
   */
  @Transactional
  fun addExecution(
    positionId: Long,
    quantity: Int,
    price: Double,
    executionDate: LocalDate,
    brokerTradeId: String?,
    commission: Double?,
    fxRateToBase: Double? = null,
  ): Execution {
    logger.info("Adding execution to position $positionId: quantity=$quantity, price=$price, date=$executionDate")

    // Save execution
    val execution =
      executionRepository.save(
        Execution(
          id = null,
          positionId = positionId,
          quantity = quantity,
          price = price,
          executionDate = executionDate,
          brokerTradeId = brokerTradeId,
          linkedBrokerTradeId = null,
          executionTime = null,
          commission = commission,
          fxRateToBase = fxRateToBase,
          notes = null,
        ),
      )

    // Recalculate position aggregates
    recalculatePositionAggregates(positionId)

    return execution
  }

  /**
   * Close position - mark as closed and calculate realized P&L
   */
  @Transactional
  fun closePosition(
    positionId: Long,
    closedDate: LocalDate,
  ): Position {
    val position =
      positionRepository.findById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")

    logger.info("Closing position $positionId on $closedDate")

    // Calculate realized P&L from all executions
    val executions = executionRepository.findByPositionId(positionId)
    val totalBought = executions.filter { it.quantity > 0 }.sumOf { it.quantity * it.price }
    val totalSold = executions.filter { it.quantity < 0 }.sumOf { abs(it.quantity) * it.price }

    // Multiply by position multiplier (for options: 100, for stocks: 1)
    val multiplier = position.multiplier
    val realizedPnl = (totalSold - totalBought) * multiplier

    // Calculate base currency P&L if FX rates are available
    val hasFxRates = executions.any { it.fxRateToBase != null }
    val realizedPnlBase = if (hasFxRates) {
      val totalBoughtBase = executions
        .filter { it.quantity > 0 }
        .sumOf { it.quantity * it.price * (it.fxRateToBase ?: 1.0) }
      val totalSoldBase = executions
        .filter { it.quantity < 0 }
        .sumOf { abs(it.quantity) * it.price * (it.fxRateToBase ?: 1.0) }
      (totalSoldBase - totalBoughtBase) * multiplier
    } else {
      null
    }

    logger.info("Position $positionId realized P&L: $realizedPnl (bought: $totalBought, sold: $totalSold, multiplier: $multiplier)")
    if (realizedPnlBase != null) {
      logger.info("Position $positionId realized P&L (base): $realizedPnlBase")
    }

    val closed =
      position.copy(
        status = PositionStatus.CLOSED,
        closedDate = closedDate,
        realizedPnl = realizedPnl,
        realizedPnlBase = realizedPnlBase,
        currentQuantity = 0,
        currentContracts = if (position.instrumentType == InstrumentType.OPTION) 0 else null,
      )

    // Update portfolio current balance with realized P&L minus commissions
    val portfolio =
      portfolioRepository.findById(position.portfolioId)
        ?: throw IllegalArgumentException("Portfolio ${position.portfolioId} not found")

    val positionCommissions = executions.sumOf { it.commission ?: 0.0 }
    val updatedPortfolio =
      portfolio.copy(
        currentBalance = portfolio.currentBalance + realizedPnl + positionCommissions,
        lastUpdated = LocalDateTime.now(),
      )

    portfolioRepository.save(updatedPortfolio)
    logger.info(
      "Updated portfolio ${position.portfolioId} balance: " +
        "${portfolio.currentBalance} + $realizedPnl + $positionCommissions = ${updatedPortfolio.currentBalance}",
    )

    return positionRepository.save(closed)
  }

  /**
   * Find existing open position for an underlying symbol (for roll consolidation)
   */
  fun findOpenPositionByUnderlying(
    portfolioId: Long,
    underlyingSymbol: String,
  ): Position? = positionRepository.findOpenPositionByUnderlying(portfolioId, underlyingSymbol)

  /**
   * Update position after roll - changes symbol, strike, and expiration to new values
   */
  fun updatePositionAfterRoll(
    positionId: Long,
    newSymbol: String,
    newStrike: Double?,
    newExpiry: LocalDate?,
    notes: String,
  ): Position {
    val position =
      positionRepository.findById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")

    logger.info("Updating position $positionId after roll: $newSymbol strike=$newStrike expiry=$newExpiry")

    val updated =
      position.copy(
        symbol = newSymbol,
        strikePrice = newStrike,
        expirationDate = newExpiry,
        notes = notes,
      )

    return positionRepository.save(updated)
  }

  /**
   * Recalculate position aggregates from executions.
   * Uses a running average that resets when quantity hits 0 (e.g., between roll legs),
   * so avgEntryPrice reflects the current/last leg's entry, not a blend of all historical buys.
   */
  private fun recalculatePositionAggregates(positionId: Long) {
    val position = positionRepository.findById(positionId) ?: return
    val executions = executionRepository.findByPositionId(positionId)

    // Total cost across all buys (for return % calculations in stats)
    // Include multiplier so totalCost is in actual dollars (consistent with realizedPnl)
    val multiplier = if (position.instrumentType == InstrumentType.OPTION) position.multiplier else 1
    val totalCost = executions.filter { it.quantity > 0 }.sumOf { it.quantity * it.price } * multiplier

    // Running average entry price: resets when position fully closes (roll boundary)
    val sorted = executions.sortedBy { it.executionDate }
    var runningQty = 0
    var runningCost = 0.0
    var avgEntryPrice = 0.0

    for (exec in sorted) {
      if (exec.quantity > 0) {
        runningCost += exec.quantity * exec.price
        runningQty += exec.quantity
        avgEntryPrice = runningCost / runningQty
      } else {
        val sellQty = abs(exec.quantity)
        runningCost -= sellQty * avgEntryPrice
        runningQty -= sellQty
        if (runningQty == 0) {
          runningCost = 0.0
        }
      }
    }

    val currentQuantity = runningQty

    logger.debug(
      "Position $positionId aggregates: quantity=$currentQuantity, avgEntry=$avgEntryPrice, cost=$totalCost",
    )

    val updated =
      position.copy(
        currentQuantity = currentQuantity,
        currentContracts = if (position.instrumentType == InstrumentType.OPTION) currentQuantity else null,
        averageEntryPrice = avgEntryPrice,
        totalCost = totalCost,
      )

    positionRepository.save(updated)
  }

  // ===========================================
  // MANUAL ENTRY OPERATIONS
  // ===========================================

  /**
   * Create manual position (user-entered, not from broker)
   */
  @Transactional
  fun createManualPosition(
    portfolioId: Long,
    symbol: String,
    instrumentType: InstrumentType,
    quantity: Int,
    entryPrice: Double,
    entryDate: LocalDate,
    entryStrategy: String,
    exitStrategy: String,
    currency: String,
    underlyingSymbol: String? = null,
    optionType: OptionType? = null,
    strikePrice: Double? = null,
    expirationDate: LocalDate? = null,
    multiplier: Int = 100,
  ): Position {
    logger.info("Creating manual position: $symbol, quantity=$quantity, price=$entryPrice")

    val totalCost =
      if (instrumentType == InstrumentType.OPTION) {
        entryPrice * quantity * multiplier
      } else {
        entryPrice * quantity
      }

    // Create position
    val position =
      positionRepository.save(
        Position(
          id = null,
          portfolioId = portfolioId,
          symbol = symbol,
          underlyingSymbol = underlyingSymbol,
          instrumentType = instrumentType,
          optionType = optionType,
          strikePrice = strikePrice,
          expirationDate = expirationDate,
          multiplier = multiplier,
          currentQuantity = quantity,
          currentContracts = if (instrumentType == InstrumentType.OPTION) quantity else null,
          averageEntryPrice = entryPrice,
          totalCost = totalCost,
          status = PositionStatus.OPEN,
          openedDate = entryDate,
          closedDate = null,
          realizedPnl = null,
          rolledToPositionId = null,
          parentPositionId = null,
          rollNumber = 0,
          entryStrategy = entryStrategy,
          exitStrategy = exitStrategy,
          notes = null,
          currency = currency,
          source = PositionSource.MANUAL,
        ),
      )

    // Create initial execution
    executionRepository.save(
      Execution(
        id = null,
        positionId = position.id!!,
        quantity = quantity,
        price = entryPrice,
        executionDate = entryDate,
        brokerTradeId = null,
        linkedBrokerTradeId = null,
        executionTime = null,
        commission = null,
        notes = "Manual entry",
      ),
    )

    return position
  }

  /**
   * Close manual position with exit price and date
   */
  @Transactional
  fun closeManualPosition(
    positionId: Long,
    exitPrice: Double,
    exitDate: LocalDate,
  ): Position {
    val position =
      positionRepository.findById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")

    if (position.status != PositionStatus.OPEN) {
      throw IllegalArgumentException("Position $positionId is already closed")
    }

    logger.info("Closing manual position $positionId with exit price $exitPrice on $exitDate")

    // Add closing execution
    executionRepository.save(
      Execution(
        id = null,
        positionId = positionId,
        quantity = -position.currentQuantity,
        price = exitPrice,
        executionDate = exitDate,
        brokerTradeId = null,
        linkedBrokerTradeId = null,
        executionTime = null,
        commission = null,
        notes = "Manual close",
      ),
    )

    // Close position
    return closePosition(positionId, exitDate)
  }

  // ===========================================
  // EDIT OPERATIONS
  // ===========================================

  /**
   * Update position metadata (strategies, notes)
   * Executions are immutable, only metadata can be edited
   */
  fun updatePositionMetadata(
    positionId: Long,
    entryStrategy: String?,
    exitStrategy: String?,
    notes: String?,
  ): Position {
    val position =
      positionRepository.findById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")

    logger.info("Updating metadata for position $positionId")

    val updated =
      position.copy(
        entryStrategy = entryStrategy ?: position.entryStrategy,
        exitStrategy = exitStrategy ?: position.exitStrategy,
        notes = notes ?: position.notes,
      )

    return positionRepository.save(updated)
  }

  // ===========================================
  // DELETE OPERATIONS
  // ===========================================

  /**
   * Delete position (cascades to executions)
   */
  fun deletePosition(positionId: Long) {
    logger.info("Deleting position $positionId")
    positionRepository.delete(positionId)
  }

  // ===========================================
  // QUERY OPERATIONS
  // ===========================================

  /**
   * Get all positions for a portfolio, optionally filtered by status
   */
  fun getPositions(
    portfolioId: Long,
    status: PositionStatus? = null,
  ): List<Position> =
    if (status != null) {
      positionRepository.findByPortfolioIdAndStatus(portfolioId, status)
    } else {
      positionRepository.findByPortfolioId(portfolioId)
    }

  /**
   * Get position by ID
   */
  fun getPositionById(positionId: Long): Position? = positionRepository.findById(positionId)

  /**
   * Get position with all its executions
   */
  fun getPositionWithExecutions(positionId: Long): PositionWithExecutions {
    val position =
      positionRepository.findById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")

    val executions = executionRepository.findByPositionId(positionId)

    return PositionWithExecutions(
      position = position,
      executions = executions,
    )
  }

  /**
   * Get roll chain for a position
   * Walks forward and backward through rolledToPositionId links
   */
  fun getRollChain(positionId: Long): List<Position> {
    val position =
      positionRepository.findById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")

    // Walk backwards to find the original position in the chain
    var originalPosition = position
    while (originalPosition.parentPositionId != null) {
      originalPosition =
        positionRepository.findById(originalPosition.parentPositionId)
          ?: break
    }

    // Walk forwards to build the chain
    val chain = mutableListOf(originalPosition)
    var currentPosition = originalPosition
    while (currentPosition.rolledToPositionId != null) {
      val nextPosition = positionRepository.findById(currentPosition.rolledToPositionId)
      if (nextPosition != null) {
        chain.add(nextPosition)
        currentPosition = nextPosition
      } else {
        break
      }
    }

    return chain
  }
}
