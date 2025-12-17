package com.skrymer.udgaard.repository.jooq

import com.skrymer.udgaard.domain.PortfolioDomain
import com.skrymer.udgaard.jooq.tables.pojos.Portfolios
import com.skrymer.udgaard.jooq.tables.references.PORTFOLIOS
import com.skrymer.udgaard.mapper.PortfolioMapper
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/**
 * jOOQ-based repository for Portfolio operations
 * Replaces the Hibernate PortfolioRepository
 */
@Repository
class PortfolioJooqRepository(
  private val dsl: DSLContext,
  private val mapper: PortfolioMapper,
) {
  /**
   * Find portfolio by ID
   */
  fun findById(id: Long): PortfolioDomain? {
    val portfolio =
      dsl
        .selectFrom(PORTFOLIOS)
        .where(PORTFOLIOS.ID.eq(id))
        .fetchOneInto(Portfolios::class.java) ?: return null

    return mapper.toDomain(portfolio)
  }

  /**
   * Find all portfolios
   */
  fun findAll(): List<PortfolioDomain> {
    val portfolios =
      dsl
        .selectFrom(PORTFOLIOS)
        .orderBy(PORTFOLIOS.CREATED_DATE.desc())
        .fetchInto(Portfolios::class.java)

    return portfolios.map { mapper.toDomain(it) }
  }

  /**
   * Find portfolios by user ID
   */
  fun findByUserId(userId: String): List<PortfolioDomain> {
    val portfolios =
      dsl
        .selectFrom(PORTFOLIOS)
        .where(PORTFOLIOS.USER_ID.eq(userId))
        .orderBy(PORTFOLIOS.CREATED_DATE.desc())
        .fetchInto(Portfolios::class.java)

    return portfolios.map { mapper.toDomain(it) }
  }

  /**
   * Save portfolio
   * Performs an upsert (insert or update)
   */
  fun save(portfolio: PortfolioDomain): PortfolioDomain {
    val pojo = mapper.toPojo(portfolio)

    if (portfolio.id == null) {
      // Insert new portfolio
      val record =
        dsl
          .insertInto(PORTFOLIOS)
          .set(PORTFOLIOS.USER_ID, pojo.userId)
          .set(PORTFOLIOS.NAME, pojo.name)
          .set(PORTFOLIOS.INITIAL_BALANCE, pojo.initialBalance)
          .set(PORTFOLIOS.CURRENT_BALANCE, pojo.currentBalance)
          .set(PORTFOLIOS.CURRENCY, pojo.currency)
          .set(PORTFOLIOS.CREATED_DATE, pojo.createdDate)
          .set(PORTFOLIOS.LAST_UPDATED, pojo.lastUpdated)
          .returningResult(PORTFOLIOS.ID)
          .fetchOne()

      val newId = record?.getValue(PORTFOLIOS.ID) ?: throw IllegalStateException("Failed to insert portfolio")
      return portfolio.copy(id = newId)
    } else {
      // Update existing portfolio
      dsl
        .update(PORTFOLIOS)
        .set(PORTFOLIOS.USER_ID, pojo.userId)
        .set(PORTFOLIOS.NAME, pojo.name)
        .set(PORTFOLIOS.INITIAL_BALANCE, pojo.initialBalance)
        .set(PORTFOLIOS.CURRENT_BALANCE, pojo.currentBalance)
        .set(PORTFOLIOS.CURRENCY, pojo.currency)
        .set(PORTFOLIOS.LAST_UPDATED, pojo.lastUpdated)
        .where(PORTFOLIOS.ID.eq(portfolio.id))
        .execute()

      return portfolio
    }
  }

  /**
   * Delete portfolio by ID
   */
  fun delete(id: Long) {
    dsl
      .deleteFrom(PORTFOLIOS)
      .where(PORTFOLIOS.ID.eq(id))
      .execute()
  }

  /**
   * Check if portfolio exists
   */
  fun exists(id: Long): Boolean =
    dsl
      .selectCount()
      .from(PORTFOLIOS)
      .where(PORTFOLIOS.ID.eq(id))
      .fetchOne(0, Int::class.java) ?: 0 > 0
}
