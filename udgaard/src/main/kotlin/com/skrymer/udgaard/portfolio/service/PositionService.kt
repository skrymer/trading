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
    val aggregate =
      positionRepository.findWithExecutionsById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")
    if (aggregate.position.isClosed) {
      // Idempotency guard: a re-entrant close must not double-apply realizedPnl to the portfolio
      // balance. The broker-import flow's heuristics (allBrokerTradeIdsExist, chain.isClosed)
      // skip already-closed positions, but a partially-failed prior sync could leave the
      // position closed without all its broker trade IDs registered, which would otherwise
      // re-trigger the close path.
      logger.info("Position $positionId already closed; skipping idempotent close-call")
      return aggregate.position
    }
    val portfolio =
      portfolioRepository.findById(aggregate.position.portfolioId)
        ?: throw IllegalArgumentException("Portfolio ${aggregate.position.portfolioId} not found")

    logger.info("Closing position $positionId on $closedDate")

    val closed = aggregate.withClosed(closedDate)
    val updatedPortfolio = portfolio.withRealizedPnlApplied(closed.realizedPnl, closed.totalCommissions)

    logger.info(
      "Position $positionId realized P&L: ${closed.realizedPnl} " +
        "(multiplier: ${aggregate.position.multiplier})",
    )
    if (closed.realizedPnlBase != null) {
      logger.info("Position $positionId realized P&L (base): ${closed.realizedPnlBase}")
    }
    logger.info(
      "Updated portfolio ${aggregate.position.portfolioId} balance: " +
        "${portfolio.currentBalance} + ${closed.realizedPnl} + ${closed.totalCommissions} = ${updatedPortfolio.currentBalance}",
    )

    portfolioRepository.save(updatedPortfolio)
    return positionRepository.save(closed.position)
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
   * Recalculate position aggregates from executions. Delegates to the rich domain
   * (`PositionWithExecutions.recalculated()`) which owns the running-average reset rule.
   */
  private fun recalculatePositionAggregates(positionId: Long) {
    val aggregate = positionRepository.findWithExecutionsById(positionId) ?: return
    val recalculated = aggregate.recalculated()
    logger.debug(
      "Position $positionId aggregates: " +
        "quantity=${recalculated.position.currentQuantity}, " +
        "avgEntry=${recalculated.position.averageEntryPrice}, " +
        "cost=${recalculated.position.totalCost}",
    )
    positionRepository.save(recalculated.position)
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

    // Inherit the FX rate from the most recent prior execution so the closing leg contributes
    // correctly to realizedPnlBase. Falls back to null for USD-only portfolios (where no
    // execution carries a rate); realizedPnlBase short-circuits to null in that case anyway.
    val priorFxRate = executionRepository
      .findByPositionId(positionId)
      .sortedByDescending { it.executionDate }
      .firstNotNullOfOrNull { it.fxRateToBase }
    executionRepository.save(Execution.closingFor(position, exitPrice, exitDate, fxRateToBase = priorFxRate))
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
