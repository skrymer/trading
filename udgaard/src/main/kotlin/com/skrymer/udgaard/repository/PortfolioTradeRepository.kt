package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.PortfolioTrade
import com.skrymer.udgaard.model.TradeStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface PortfolioTradeRepository : JpaRepository<PortfolioTrade, Long> {
    fun findByPortfolioId(portfolioId: Long): List<PortfolioTrade>
    fun findByPortfolioIdAndStatus(portfolioId: Long, status: TradeStatus): List<PortfolioTrade>
    fun findByPortfolioIdAndExitDateBetween(
        portfolioId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PortfolioTrade>
    fun deleteByPortfolioId(portfolioId: Long)
}
