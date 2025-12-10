package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.PortfolioTrade
import com.skrymer.udgaard.model.TradeStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

interface PortfolioTradeRepository : JpaRepository<PortfolioTrade, Long> {
  fun findByPortfolioId(portfolioId: Long): List<PortfolioTrade>

  fun findByPortfolioIdAndStatus(
    portfolioId: Long,
    status: TradeStatus,
  ): List<PortfolioTrade>

  fun findByPortfolioIdAndExitDateBetween(
    portfolioId: Long,
    startDate: LocalDate,
    endDate: LocalDate,
  ): List<PortfolioTrade>

  @Modifying
  @Transactional
  fun deleteByPortfolioId(portfolioId: Long)
}
