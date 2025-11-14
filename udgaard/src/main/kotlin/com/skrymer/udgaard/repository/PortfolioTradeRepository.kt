package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.PortfolioTrade
import com.skrymer.udgaard.model.TradeStatus
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.LocalDate

interface PortfolioTradeRepository : MongoRepository<PortfolioTrade, String> {
    fun findByPortfolioId(portfolioId: String): List<PortfolioTrade>
    fun findByPortfolioIdAndStatus(portfolioId: String, status: TradeStatus): List<PortfolioTrade>
    fun findByPortfolioIdAndExitDateBetween(
        portfolioId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PortfolioTrade>
    fun deleteByPortfolioId(portfolioId: String)
}
