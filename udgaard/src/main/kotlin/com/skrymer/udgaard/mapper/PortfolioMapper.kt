package com.skrymer.udgaard.mapper

import com.skrymer.udgaard.domain.PortfolioDomain
import com.skrymer.udgaard.jooq.tables.pojos.Portfolios
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ Portfolio POJOs and domain models
 */
@Component
class PortfolioMapper {
  /**
   * Convert jOOQ Portfolio POJO to domain model
   */
  fun toDomain(portfolio: Portfolios): PortfolioDomain =
    PortfolioDomain(
      id = portfolio.id,
      userId = portfolio.userId,
      name = portfolio.name ?: "",
      initialBalance = portfolio.initialBalance ?: 0.0,
      currentBalance = portfolio.currentBalance ?: 0.0,
      currency = portfolio.currency ?: "USD",
      createdDate = portfolio.createdDate ?: java.time.LocalDateTime.now(),
      lastUpdated = portfolio.lastUpdated ?: java.time.LocalDateTime.now(),
    )

  /**
   * Convert domain model to jOOQ Portfolio POJO
   */
  fun toPojo(portfolio: PortfolioDomain): Portfolios =
    Portfolios(
      id = portfolio.id,
      userId = portfolio.userId,
      name = portfolio.name,
      initialBalance = portfolio.initialBalance,
      currentBalance = portfolio.currentBalance,
      currency = portfolio.currency,
      createdDate = portfolio.createdDate,
      lastUpdated = portfolio.lastUpdated,
    )
}
