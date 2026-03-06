package com.skrymer.udgaard.portfolio.service

import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.portfolio.model.CashTransaction
import com.skrymer.udgaard.portfolio.model.CashTransactionType
import com.skrymer.udgaard.portfolio.model.EquityCurveData
import com.skrymer.udgaard.portfolio.model.EquityDataPoint
import com.skrymer.udgaard.portfolio.model.Portfolio
import com.skrymer.udgaard.portfolio.model.Position
import com.skrymer.udgaard.portfolio.model.PositionStats
import com.skrymer.udgaard.portfolio.model.PositionStatus
import com.skrymer.udgaard.portfolio.repository.ExecutionJooqRepository
import com.skrymer.udgaard.portfolio.repository.PortfolioJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Service
class PortfolioStatsService(
  private val positionService: PositionService,
  private val executionRepository: ExecutionJooqRepository,
  private val portfolioRepository: PortfolioJooqRepository,
  private val midgaardClient: MidgaardClient,
  private val cashTransactionService: CashTransactionService,
) {
  private val logger = LoggerFactory.getLogger(PortfolioStatsService::class.java)

  fun calculateStats(portfolioId: Long): PositionStats {
    val portfolio = portfolioRepository.findById(portfolioId)
      ?: throw IllegalArgumentException("Portfolio not found: $portfolioId")

    val allPositions = positionService.getPositions(portfolioId)
    val closedPositions = allPositions.filter { it.status == PositionStatus.CLOSED }
    val openPositions = allPositions.filter { it.status == PositionStatus.OPEN }

    if (closedPositions.isEmpty()) {
      return createEmptyStats(allPositions.size, openPositions.size)
    }

    val tradeMetrics = calculateTradeMetrics(closedPositions)
    val totalCommissions = allPositions
      .mapNotNull { it.id }
      .flatMap { executionRepository.findByPositionId(it) }
      .sumOf { it.commission ?: 0.0 }

    val cashTransactions = cashTransactionService.getCashTransactions(portfolioId)
    val fxResult = calculateFxPnl(portfolio, cashTransactions)
    val returnMetrics = calculateReturnMetrics(portfolio, tradeMetrics.totalProfit)

    val totalDeposits = cashTransactions
      .filter { it.type == CashTransactionType.DEPOSIT }
      .sumOf { it.convertedAmount ?: it.amount }
    val totalWithdrawals = cashTransactions
      .filter { it.type == CashTransactionType.WITHDRAWAL }
      .sumOf { it.convertedAmount ?: it.amount }

    return PositionStats(
      totalTrades = allPositions.size,
      openTrades = openPositions.size,
      closedTrades = closedPositions.size,
      ytdReturn = returnMetrics.ytdReturn,
      annualizedReturn = returnMetrics.annualizedReturn,
      avgWin = tradeMetrics.avgWin,
      avgLoss = tradeMetrics.avgLoss,
      winRate = tradeMetrics.winRate,
      provenEdge = tradeMetrics.provenEdge,
      profitFactor = tradeMetrics.profitFactor,
      totalProfit = tradeMetrics.totalProfit,
      totalProfitPercentage = returnMetrics.totalProfitPercentage,
      largestWin = tradeMetrics.largestWin,
      largestLoss = tradeMetrics.largestLoss,
      numberOfWins = tradeMetrics.numberOfWins,
      numberOfLosses = tradeMetrics.numberOfLosses,
      totalCommissions = totalCommissions,
      totalRealizedFxPnl = fxResult.fxPnl,
      currentFxRate = fxResult.currentFxRate,
      totalDeposits = totalDeposits,
      totalWithdrawals = totalWithdrawals,
    )
  }

  fun getEquityCurve(portfolioId: Long): EquityCurveData {
    val portfolio = portfolioRepository.findById(portfolioId)
    val initialBalance = portfolio?.initialBalance ?: 0.0

    val closedPositions = positionService
      .getPositions(portfolioId, PositionStatus.CLOSED)
      .sortedBy { it.closedDate }

    val dataPoints = mutableListOf<EquityDataPoint>()
    var runningProfit = 0.0

    val positionsByDate = closedPositions.groupBy { it.closedDate }

    positionsByDate.keys.filterNotNull().sorted().forEach { closedDate ->
      val dayPositions = positionsByDate[closedDate] ?: emptyList()
      val dayProfit = dayPositions.sumOf { it.realizedPnl ?: 0.0 }
      runningProfit += dayProfit

      val returnPercentage = if (initialBalance > 0) {
        (runningProfit / initialBalance) * 100.0
      } else {
        0.0
      }

      dataPoints.add(
        EquityDataPoint(
          date = closedDate,
          balance = runningProfit,
          returnPercentage = returnPercentage,
        ),
      )
    }

    return EquityCurveData(dataPoints)
  }

  fun recalculatePortfolioBalance(portfolioId: Long): Portfolio {
    val portfolio = portfolioRepository.findById(portfolioId)
      ?: throw IllegalArgumentException("Portfolio $portfolioId not found")

    val closedPositions = positionService.getPositions(portfolioId, PositionStatus.CLOSED)
    val totalRealizedPnl = closedPositions.sumOf { it.realizedPnl ?: 0.0 }

    val allPositions = positionService.getPositions(portfolioId)
    val totalCommissions = allPositions
      .mapNotNull { it.id }
      .flatMap { executionRepository.findByPositionId(it) }
      .sumOf { it.commission ?: 0.0 }

    val netCashFlow = cashTransactionService.getNetCashFlow(portfolioId)

    val updatedPortfolio = portfolio.copy(
      currentBalance = portfolio.initialBalance + totalRealizedPnl + totalCommissions + netCashFlow,
      lastUpdated = LocalDateTime.now(),
    )

    logger.info(
      "Recalculated portfolio $portfolioId balance: " +
        "${portfolio.initialBalance} + $totalRealizedPnl + $totalCommissions + $netCashFlow = ${updatedPortfolio.currentBalance}",
    )

    return portfolioRepository.save(updatedPortfolio)
  }

  private data class TradeMetrics(
    val winRate: Double,
    val avgWin: Double,
    val avgLoss: Double,
    val provenEdge: Double,
    val profitFactor: Double?,
    val totalProfit: Double,
    val largestWin: Double?,
    val largestLoss: Double?,
    val numberOfWins: Int,
    val numberOfLosses: Int,
  )

  private fun calculateTradeMetrics(closedPositions: List<Position>): TradeMetrics {
    val wins = closedPositions.filter { (it.realizedPnl ?: 0.0) > 0 }
    val losses = closedPositions.filter { (it.realizedPnl ?: 0.0) < 0 }
    val winRate = (wins.size.toDouble() / closedPositions.size) * 100.0
    val avgWin = calculateAvgPnlPercentage(wins)
    val avgLoss = calculateAvgPnlPercentage(losses)
    val lossRate = 100.0 - winRate

    val profitFactor = if (losses.isNotEmpty()) {
      val grossProfit = wins.sumOf { it.realizedPnl ?: 0.0 }
      val grossLoss = abs(losses.sumOf { it.realizedPnl ?: 0.0 })
      if (grossLoss > 0.0) grossProfit / grossLoss else null
    } else {
      null
    }

    return TradeMetrics(
      winRate = winRate,
      avgWin = avgWin,
      avgLoss = avgLoss,
      provenEdge = (winRate / 100.0 * avgWin) - (lossRate / 100.0 * abs(avgLoss)),
      profitFactor = profitFactor,
      totalProfit = closedPositions.sumOf { it.realizedPnl ?: 0.0 },
      largestWin = wins
        .filter { it.totalCost > 0 }
        .maxOfOrNull { ((it.realizedPnl ?: 0.0) / it.totalCost) * 100.0 },
      largestLoss = losses
        .filter { it.totalCost > 0 }
        .minOfOrNull { ((it.realizedPnl ?: 0.0) / it.totalCost) * 100.0 },
      numberOfWins = wins.size,
      numberOfLosses = losses.size,
    )
  }

  private data class ReturnMetrics(
    val ytdReturn: Double,
    val annualizedReturn: Double,
    val totalProfitPercentage: Double,
  )

  private fun calculateReturnMetrics(
    portfolio: Portfolio,
    totalProfit: Double,
  ): ReturnMetrics {
    if (portfolio.initialBalance <= 0) {
      return ReturnMetrics(0.0, 0.0, 0.0)
    }

    val ytdReturn = ((portfolio.currentBalance - portfolio.initialBalance) / portfolio.initialBalance) * 100.0
    val totalProfitPercentage = (totalProfit / portfolio.initialBalance) * 100.0

    val daysSinceCreation = ChronoUnit.DAYS.between(
      portfolio.createdDate.toLocalDate(),
      LocalDate.now(),
    )
    val fractionalYears = (daysSinceCreation / 365.25).coerceAtLeast(0.1)
    val annualizedReturn =
      (Math.pow(portfolio.currentBalance / portfolio.initialBalance, 1.0 / fractionalYears) - 1.0) * 100.0

    return ReturnMetrics(ytdReturn, annualizedReturn, totalProfitPercentage)
  }

  private data class FxResult(
    val fxPnl: Double?,
    val currentFxRate: Double?,
  )

  private fun calculateFxPnl(
    portfolio: Portfolio,
    cashTransactions: List<CashTransaction>,
  ): FxResult {
    val initialFxRate = portfolio.initialFxRate
    val isFxPortfolio = initialFxRate != null && portfolio.baseCurrency != portfolio.currency
    val currentFxRate = if (isFxPortfolio) {
      midgaardClient.getExchangeRate(portfolio.currency, portfolio.baseCurrency)
    } else {
      null
    }

    if (initialFxRate == null || currentFxRate == null) {
      if (isFxPortfolio) {
        logger.warn("Could not fetch current FX rate ${portfolio.currency}/${portfolio.baseCurrency} for FX P&L")
      }
      return FxResult(null, null)
    }

    val depositsInBase = cashTransactions
      .filter { it.type == CashTransactionType.DEPOSIT }
      .sumOf { it.amount * (it.fxRateToBase ?: 1.0) }
    val withdrawalsInBase = cashTransactions
      .filter { it.type == CashTransactionType.WITHDRAWAL }
      .sumOf { it.amount * (it.fxRateToBase ?: 1.0) }
    val depositsInTrade = cashTransactions
      .filter { it.type == CashTransactionType.DEPOSIT }
      .sumOf { it.convertedAmount ?: it.amount }
    val withdrawalsInTrade = cashTransactions
      .filter { it.type == CashTransactionType.WITHDRAWAL }
      .sumOf { it.convertedAmount ?: it.amount }

    val totalCapitalInBase = portfolio.initialBalance + depositsInBase - withdrawalsInBase
    val totalCapitalInTrade =
      portfolio.initialBalance / initialFxRate + depositsInTrade - withdrawalsInTrade

    val avgAcquisitionRate = if (totalCapitalInTrade > 0) {
      totalCapitalInBase / totalCapitalInTrade
    } else {
      initialFxRate
    }

    val fxPnl = portfolio.currentBalance * (1.0 - avgAcquisitionRate / currentFxRate)
    return FxResult(fxPnl, currentFxRate)
  }

  private fun calculateAvgPnlPercentage(
    positions: List<Position>,
  ): Double {
    if (positions.isEmpty()) return 0.0
    return positions
      .filter { it.totalCost > 0 }
      .map { ((it.realizedPnl ?: 0.0) / it.totalCost) * 100.0 }
      .ifEmpty { null }
      ?.average() ?: 0.0
  }

  private fun createEmptyStats(totalTrades: Int, openTrades: Int) = PositionStats(
    totalTrades = totalTrades,
    openTrades = openTrades,
    closedTrades = 0,
    ytdReturn = 0.0,
    annualizedReturn = 0.0,
    avgWin = 0.0,
    avgLoss = 0.0,
    winRate = 0.0,
    provenEdge = 0.0,
    profitFactor = null,
    totalProfit = 0.0,
    totalProfitPercentage = 0.0,
  )
}
