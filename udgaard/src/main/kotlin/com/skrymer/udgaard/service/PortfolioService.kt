package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.PortfolioDomain
import com.skrymer.udgaard.repository.jooq.PortfolioJooqRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Service for managing portfolios (CRUD operations only)
 * Position management is handled by PositionService
 */
@Service
class PortfolioService(
  private val portfolioRepository: PortfolioJooqRepository,
) {
  /**
   * Get all portfolios, optionally filtered by userId
   */
  fun getAllPortfolios(userId: String? = null): List<PortfolioDomain> =
    if (userId != null) {
      portfolioRepository.findByUserId(userId)
    } else {
      portfolioRepository.findAll()
    }

  /**
   * Create a new portfolio
   */
  fun createPortfolio(
    name: String,
    initialBalance: Double,
    currency: String,
    userId: String? = null,
  ): PortfolioDomain {
    val portfolio =
      PortfolioDomain(
        id = null,
        userId = userId,
        name = name,
        initialBalance = initialBalance,
        currentBalance = initialBalance,
        currency = currency,
        createdDate = LocalDateTime.now(),
        lastUpdated = LocalDateTime.now(),
      )
    return portfolioRepository.save(portfolio)
  }

  /**
   * Get portfolio by ID
   */
  fun getPortfolio(portfolioId: Long): PortfolioDomain? = portfolioRepository.findById(portfolioId)

  /**
   * Update portfolio balance
   */
  fun updatePortfolio(
    portfolioId: Long,
    currentBalance: Double,
  ): PortfolioDomain? {
    val portfolio = getPortfolio(portfolioId) ?: return null
    val updated =
      portfolio.copy(
        currentBalance = currentBalance,
        lastUpdated = LocalDateTime.now(),
      )
    return portfolioRepository.save(updated)
  }

  /**
   * Delete portfolio and all associated positions/executions (CASCADE)
   */
  @Transactional
  fun deletePortfolio(portfolioId: Long) {
    portfolioRepository.delete(portfolioId)
  }

  /**
   * Update portfolio with broker information
   * Used during broker import/sync to save broker-specific fields
   */
  fun updatePortfolioWithBrokerInfo(portfolio: PortfolioDomain): PortfolioDomain = portfolioRepository.save(portfolio)

  /**
   * Update portfolio's last sync date
   * Called after successful broker sync
   */
  fun updateLastSyncDate(
    portfolioId: Long,
    syncDate: LocalDateTime,
  ) {
    val portfolio = getPortfolio(portfolioId) ?: return
    val updated = portfolio.copy(lastSyncDate = syncDate, lastUpdated = LocalDateTime.now())
    portfolioRepository.save(updated)
  }
}
