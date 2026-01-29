package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.ExecutionDomain
import com.skrymer.udgaard.domain.InstrumentTypeDomain
import com.skrymer.udgaard.domain.OptionTypeDomain
import com.skrymer.udgaard.domain.PortfolioDomain
import com.skrymer.udgaard.domain.PositionDomain
import com.skrymer.udgaard.domain.PositionSourceDomain
import com.skrymer.udgaard.domain.PositionStats
import com.skrymer.udgaard.domain.PositionStatusDomain
import com.skrymer.udgaard.domain.PositionWithExecutions
import com.skrymer.udgaard.model.EquityCurveData
import com.skrymer.udgaard.model.EquityDataPoint
import com.skrymer.udgaard.repository.jooq.ExecutionJooqRepository
import com.skrymer.udgaard.repository.jooq.PortfolioJooqRepository
import com.skrymer.udgaard.repository.jooq.PositionJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import kotlin.math.abs

/**
 * Service for managing positions and executions
 */
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
    instrumentType: InstrumentTypeDomain,
    underlyingSymbol: String?,
    optionType: OptionTypeDomain?,
    strikePrice: Double?,
    expirationDate: LocalDate?,
    entryStrategy: String,
    exitStrategy: String,
    currency: String,
    openedDate: LocalDate? = null,
  ): PositionDomain {
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
      PositionDomain(
        id = null,
        portfolioId = portfolioId,
        symbol = symbol,
        underlyingSymbol = underlyingSymbol,
        instrumentType = instrumentType,
        optionType = optionType,
        strikePrice = strikePrice,
        expirationDate = expirationDate,
        multiplier = if (instrumentType == InstrumentTypeDomain.OPTION) 100 else 1,
        currentQuantity = 0,
        currentContracts = if (instrumentType == InstrumentTypeDomain.OPTION) 0 else null,
        averageEntryPrice = 0.0,
        totalCost = 0.0,
        status = PositionStatusDomain.OPEN,
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
        source = PositionSourceDomain.BROKER,
      )

    return positionRepository.save(newPosition)
  }

  /**
   * Add execution to position and recalculate aggregates
   */
  fun addExecution(
    positionId: Long,
    quantity: Int,
    price: Double,
    executionDate: LocalDate,
    brokerTradeId: String?,
    commission: Double?,
  ): ExecutionDomain {
    logger.info("Adding execution to position $positionId: quantity=$quantity, price=$price, date=$executionDate")

    // Save execution
    val execution =
      executionRepository.save(
        ExecutionDomain(
          id = null,
          positionId = positionId,
          quantity = quantity,
          price = price,
          executionDate = executionDate,
          brokerTradeId = brokerTradeId,
          linkedBrokerTradeId = null,
          executionTime = null,
          commission = commission,
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
  fun closePosition(
    positionId: Long,
    closedDate: LocalDate,
  ): PositionDomain {
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

    logger.info("Position $positionId realized P&L: $realizedPnl (bought: $totalBought, sold: $totalSold, multiplier: $multiplier)")

    val closed =
      position.copy(
        status = PositionStatusDomain.CLOSED,
        closedDate = closedDate,
        realizedPnl = realizedPnl,
        currentQuantity = 0,
        currentContracts = if (position.instrumentType == InstrumentTypeDomain.OPTION) 0 else null,
      )

    // Update portfolio current balance with realized P&L
    val portfolio =
      portfolioRepository.findById(position.portfolioId)
        ?: throw IllegalArgumentException("Portfolio ${position.portfolioId} not found")

    val updatedPortfolio =
      portfolio.copy(
        currentBalance = portfolio.currentBalance + realizedPnl,
        lastUpdated = LocalDateTime.now(),
      )

    portfolioRepository.save(updatedPortfolio)
    logger.info(
      "Updated portfolio ${position.portfolioId} balance: ${portfolio.currentBalance} + $realizedPnl = ${updatedPortfolio.currentBalance}",
    )

    return positionRepository.save(closed)
  }

  /**
   * Find existing open position for an underlying symbol (for roll consolidation)
   */
  fun findOpenPositionByUnderlying(
    portfolioId: Long,
    underlyingSymbol: String,
  ): PositionDomain? = positionRepository.findOpenPositionByUnderlying(portfolioId, underlyingSymbol)

  /**
   * Update position after roll - changes symbol, strike, and expiration to new values
   */
  fun updatePositionAfterRoll(
    positionId: Long,
    newSymbol: String,
    newStrike: Double?,
    newExpiry: LocalDate?,
    notes: String,
  ): PositionDomain {
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
   * Recalculate position aggregates from executions
   */
  private fun recalculatePositionAggregates(positionId: Long) {
    val position = positionRepository.findById(positionId) ?: return
    val executions = executionRepository.findByPositionId(positionId)

    // Calculate current quantity (sum of all executions)
    val currentQuantity = executions.sumOf { it.quantity }

    // Calculate average entry price (weighted by buy executions only)
    val buyExecutions = executions.filter { it.quantity > 0 }
    val totalBought = buyExecutions.sumOf { it.quantity }
    val totalCost = buyExecutions.sumOf { it.quantity * it.price }
    val avgEntryPrice = if (totalBought > 0) totalCost / totalBought else 0.0

    logger.debug(
      "Position $positionId aggregates: quantity=$currentQuantity, avgEntry=$avgEntryPrice, cost=$totalCost",
    )

    // Update position
    val updated =
      position.copy(
        currentQuantity = currentQuantity,
        currentContracts = if (position.instrumentType == InstrumentTypeDomain.OPTION) currentQuantity else null,
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
  fun createManualPosition(
    portfolioId: Long,
    symbol: String,
    instrumentType: InstrumentTypeDomain,
    quantity: Int,
    entryPrice: Double,
    entryDate: LocalDate,
    entryStrategy: String,
    exitStrategy: String,
    currency: String,
    underlyingSymbol: String? = null,
    optionType: OptionTypeDomain? = null,
    strikePrice: Double? = null,
    expirationDate: LocalDate? = null,
    multiplier: Int = 100,
  ): PositionDomain {
    logger.info("Creating manual position: $symbol, quantity=$quantity, price=$entryPrice")

    val totalCost =
      if (instrumentType == InstrumentTypeDomain.OPTION) {
        entryPrice * quantity * multiplier
      } else {
        entryPrice * quantity
      }

    // Create position
    val position =
      positionRepository.save(
        PositionDomain(
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
          currentContracts = if (instrumentType == InstrumentTypeDomain.OPTION) quantity else null,
          averageEntryPrice = entryPrice,
          totalCost = totalCost,
          status = PositionStatusDomain.OPEN,
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
          source = PositionSourceDomain.MANUAL,
        ),
      )

    // Create initial execution
    executionRepository.save(
      ExecutionDomain(
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
  fun closeManualPosition(
    positionId: Long,
    exitPrice: Double,
    exitDate: LocalDate,
  ): PositionDomain {
    val position =
      positionRepository.findById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")

    if (position.status != PositionStatusDomain.OPEN) {
      throw IllegalArgumentException("Position $positionId is already closed")
    }

    logger.info("Closing manual position $positionId with exit price $exitPrice on $exitDate")

    // Add closing execution
    executionRepository.save(
      ExecutionDomain(
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
  ): PositionDomain {
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
    status: PositionStatusDomain? = null,
  ): List<PositionDomain> =
    if (status != null) {
      positionRepository.findByPortfolioIdAndStatus(portfolioId, status)
    } else {
      positionRepository.findByPortfolioId(portfolioId)
    }

  /**
   * Get position by ID
   */
  fun getPositionById(positionId: Long): PositionDomain? = positionRepository.findById(positionId)

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
  fun getRollChain(positionId: Long): List<PositionDomain> {
    val position =
      positionRepository.findById(positionId)
        ?: throw IllegalArgumentException("Position $positionId not found")

    // Walk backwards to find the original position in the chain
    var originalPosition = position
    while (originalPosition.parentPositionId != null) {
      originalPosition =
        positionRepository.findById(originalPosition.parentPositionId!!)
          ?: break
    }

    // Walk forwards to build the chain
    val chain = mutableListOf(originalPosition)
    var currentPosition = originalPosition
    while (currentPosition.rolledToPositionId != null) {
      val nextPosition = positionRepository.findById(currentPosition.rolledToPositionId!!)
      if (nextPosition != null) {
        chain.add(nextPosition)
        currentPosition = nextPosition
      } else {
        break
      }
    }

    return chain
  }

  // ===========================================
  // STATS OPERATIONS
  // ===========================================

  /**
   * Calculate portfolio statistics from positions
   */
  fun calculateStats(portfolioId: Long): PositionStats {
    val portfolio = portfolioRepository.findById(portfolioId)
      ?: throw IllegalArgumentException("Portfolio not found: $portfolioId")

    val allPositions = getPositions(portfolioId)
    val closedPositions = allPositions.filter { it.status == PositionStatusDomain.CLOSED }
    val openPositions = allPositions.filter { it.status == PositionStatusDomain.OPEN }

    if (closedPositions.isEmpty()) {
      return createEmptyStats(allPositions.size, openPositions.size)
    }

    // Calculate wins and losses
    val wins = closedPositions.filter { (it.realizedPnl ?: 0.0) > 0 }
    val losses = closedPositions.filter { (it.realizedPnl ?: 0.0) < 0 }

    val winRate =
      if (closedPositions.isNotEmpty()) {
        (wins.size.toDouble() / closedPositions.size) * 100.0
      } else {
        0.0
      }

    // Calculate average P&L percentages
    val avgWin =
      if (wins.isNotEmpty()) {
        wins.map { ((it.realizedPnl ?: 0.0) / it.totalCost) * 100.0 }.average()
      } else {
        0.0
      }

    val avgLoss =
      if (losses.isNotEmpty()) {
        losses.map { ((it.realizedPnl ?: 0.0) / it.totalCost) * 100.0 }.average()
      } else {
        0.0
      }

    // Calculate proven edge
    val lossRate = 100.0 - winRate
    val provenEdge = (winRate / 100.0 * avgWin) - (lossRate / 100.0 * abs(avgLoss))

    // Total profit
    val totalProfit = closedPositions.sumOf { it.realizedPnl ?: 0.0 }

    // Calculate YTD return
    val ytdReturn = if (portfolio.initialBalance > 0) {
      ((portfolio.currentBalance - portfolio.initialBalance) / portfolio.initialBalance) * 100.0
    } else {
      0.0
    }

    // Calculate total profit percentage
    val totalProfitPercentage = if (portfolio.initialBalance > 0) {
      (totalProfit / portfolio.initialBalance) * 100.0
    } else {
      0.0
    }

    // Calculate annualized return (CAGR)
    val annualizedReturn = if (portfolio.initialBalance > 0 && portfolio.createdDate != null) {
      val createdDate = portfolio.createdDate
      val currentYear = Year.now().value
      val createdYear = Year.of(createdDate.year).value
      val yearsDiff = (currentYear - createdYear).coerceAtLeast(1)

      if (yearsDiff > 0) {
        (Math.pow(portfolio.currentBalance / portfolio.initialBalance, 1.0 / yearsDiff) - 1.0) * 100.0
      } else {
        ytdReturn
      }
    } else {
      ytdReturn
    }

    // Largest win/loss
    val largestWin = wins.maxOfOrNull { ((it.realizedPnl ?: 0.0) / it.totalCost) * 100.0 }
    val largestLoss = losses.minOfOrNull { ((it.realizedPnl ?: 0.0) / it.totalCost) * 100.0 }

    return PositionStats(
      totalTrades = allPositions.size,
      openTrades = openPositions.size,
      closedTrades = closedPositions.size,
      ytdReturn = ytdReturn,
      annualizedReturn = annualizedReturn,
      avgWin = avgWin,
      avgLoss = avgLoss,
      winRate = winRate,
      provenEdge = provenEdge,
      totalProfit = totalProfit,
      totalProfitPercentage = totalProfitPercentage,
      largestWin = largestWin,
      largestLoss = largestLoss,
      numberOfWins = wins.size,
      numberOfLosses = losses.size,
    )
  }

  /**
   * Get equity curve data for portfolio
   */
  fun getEquityCurve(portfolioId: Long): EquityCurveData {
    val closedPositions =
      getPositions(portfolioId, PositionStatusDomain.CLOSED)
        .sortedBy { it.closedDate }

    val dataPoints = mutableListOf<EquityDataPoint>()
    var runningProfit = 0.0

    // Group by closed date and aggregate
    val positionsByDate = closedPositions.groupBy { it.closedDate }

    positionsByDate.keys.filterNotNull().sorted().forEach { closedDate ->
      val dayPositions = positionsByDate[closedDate] ?: emptyList()
      val dayProfit = dayPositions.sumOf { it.realizedPnl ?: 0.0 }
      runningProfit += dayProfit

      dataPoints.add(
        EquityDataPoint(
          date = closedDate,
          balance = runningProfit,
          returnPercentage = 0.0, // TODO: Calculate from initial balance
        ),
      )
    }

    return EquityCurveData(dataPoints)
  }

  /**
   * Recalculate portfolio current balance based on all closed positions
   * Useful for correcting balance after data import or migration
   */
  fun recalculatePortfolioBalance(portfolioId: Long): PortfolioDomain {
    val portfolio =
      portfolioRepository.findById(portfolioId)
        ?: throw IllegalArgumentException("Portfolio $portfolioId not found")

    // Sum all realized P&L from closed positions
    val closedPositions = getPositions(portfolioId, PositionStatusDomain.CLOSED)
    val totalRealizedPnl = closedPositions.sumOf { it.realizedPnl ?: 0.0 }

    val updatedPortfolio =
      portfolio.copy(
        currentBalance = portfolio.initialBalance + totalRealizedPnl,
        lastUpdated = LocalDateTime.now(),
      )

    logger.info(
      "Recalculated portfolio $portfolioId balance: ${portfolio.initialBalance} + $totalRealizedPnl = ${updatedPortfolio.currentBalance}",
    )

    return portfolioRepository.save(updatedPortfolio)
  }

  private fun createEmptyStats(
    totalTrades: Int,
    openTrades: Int,
  ) = PositionStats(
    totalTrades = totalTrades,
    openTrades = openTrades,
    closedTrades = 0,
    ytdReturn = 0.0,
    annualizedReturn = 0.0,
    avgWin = 0.0,
    avgLoss = 0.0,
    winRate = 0.0,
    provenEdge = 0.0,
    totalProfit = 0.0,
    totalProfitPercentage = 0.0,
  )
}
