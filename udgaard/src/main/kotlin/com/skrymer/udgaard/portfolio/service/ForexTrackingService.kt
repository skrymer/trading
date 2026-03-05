package com.skrymer.udgaard.portfolio.service

import com.skrymer.udgaard.portfolio.model.ForexDisposal
import com.skrymer.udgaard.portfolio.model.ForexLot
import com.skrymer.udgaard.portfolio.model.ForexLotStatus
import com.skrymer.udgaard.portfolio.repository.ForexDisposalJooqRepository
import com.skrymer.udgaard.portfolio.repository.ForexLotJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Tracks forex lots using FIFO for Australian CGT reporting.
 *
 * When trading USD instruments from an AUD account:
 * - SELL execution (USD received) → new forex lot (USD acquired at this FX rate)
 * - BUY execution (USD spent) → consume oldest lots FIFO, realize FX gain/loss
 */
@Service
class ForexTrackingService(
  private val forexLotRepository: ForexLotJooqRepository,
  private val forexDisposalRepository: ForexDisposalJooqRepository,
) {
  /**
   * Record a USD acquisition (e.g., selling stock returns USD to balance).
   * Creates a new forex lot at the given FX rate.
   */
  fun recordAcquisition(
    portfolioId: Long,
    executionId: Long?,
    date: LocalDate,
    usdAmount: Double,
    fxRate: Double,
    description: String,
  ): ForexLot {
    val costBasis = usdAmount * fxRate
    val lot =
      ForexLot(
        portfolioId = portfolioId,
        acquisitionDate = date,
        quantity = usdAmount,
        remainingQuantity = usdAmount,
        costRate = fxRate,
        costBasis = costBasis,
        sourceExecutionId = executionId,
        sourceDescription = description,
      )

    val saved = forexLotRepository.save(lot)
    logger.debug("FX lot created: {} USD at rate {} ({})", usdAmount, fxRate, description)
    return saved
  }

  /**
   * Record a USD disposal (e.g., buying stock spends USD from balance).
   * Consumes oldest lots FIFO and returns total realized FX P&L.
   */
  fun recordDisposal(
    portfolioId: Long,
    executionId: Long?,
    date: LocalDate,
    usdAmount: Double,
    fxRate: Double,
  ): Double {
    var remaining = usdAmount
    var totalFxPnl = 0.0

    val openLots = forexLotRepository.findOpenLotsByPortfolioFIFO(portfolioId)

    for (lot in openLots) {
      if (remaining <= 0) break

      val consumed = minOf(remaining, lot.remainingQuantity)
      val costBasisAud = consumed * lot.costRate
      val proceedsAud = consumed * fxRate
      val fxPnl = proceedsAud - costBasisAud

      // Record the disposal
      forexDisposalRepository.save(
        ForexDisposal(
          portfolioId = portfolioId,
          lotId = lot.id!!,
          disposalDate = date,
          quantity = consumed,
          costRate = lot.costRate,
          disposalRate = fxRate,
          costBasisAud = costBasisAud,
          proceedsAud = proceedsAud,
          realizedFxPnl = fxPnl,
          sourceExecutionId = executionId,
        ),
      )

      // Update the lot
      val newRemaining = lot.remainingQuantity - consumed
      val newStatus = if (newRemaining <= 0.01) ForexLotStatus.EXHAUSTED else ForexLotStatus.OPEN
      forexLotRepository.save(lot.copy(remainingQuantity = newRemaining, status = newStatus))

      totalFxPnl += fxPnl
      remaining -= consumed
    }

    if (remaining > 0.01) {
      logger.warn("FX disposal: {} USD unmatched (no remaining lots)", remaining)
    }

    logger.debug("FX disposal: {} USD at rate {}, realized FX P&L: {} AUD", usdAmount, fxRate, totalFxPnl)
    return totalFxPnl
  }

  /**
   * Process an execution for FX tracking.
   * Positive net cash (sell) = USD acquisition.
   * Negative net cash (buy) = USD disposal.
   */
  fun processExecution(
    portfolioId: Long,
    executionId: Long?,
    date: LocalDate,
    netCashUsd: Double,
    fxRate: Double,
    description: String,
  ): Double = if (netCashUsd > 0) {
    // Selling: USD flows in → acquire forex lot
    recordAcquisition(portfolioId, executionId, date, netCashUsd, fxRate, description)
    0.0 // No realized FX P&L on acquisition
  } else {
    // Buying: USD flows out → dispose forex lots FIFO
    recordDisposal(portfolioId, executionId, date, -netCashUsd, fxRate)
  }

  fun getForexLots(portfolioId: Long): List<ForexLot> =
    forexLotRepository.findByPortfolioId(portfolioId)

  fun getForexDisposals(portfolioId: Long): List<ForexDisposal> =
    forexDisposalRepository.findByPortfolioId(portfolioId)

  fun getForexDisposals(
    portfolioId: Long,
    startDate: LocalDate,
    endDate: LocalDate,
  ): List<ForexDisposal> =
    forexDisposalRepository.findByPortfolioIdAndDateRange(portfolioId, startDate, endDate)

  companion object {
    private val logger = LoggerFactory.getLogger(ForexTrackingService::class.java)
  }
}
