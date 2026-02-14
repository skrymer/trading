package com.skrymer.udgaard.portfolio.repository

import com.skrymer.udgaard.jooq.tables.pojos.Portfolios
import com.skrymer.udgaard.jooq.tables.references.PORTFOLIOS
import com.skrymer.udgaard.portfolio.mapper.PortfolioMapper
import com.skrymer.udgaard.portfolio.model.Portfolio
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
  fun findById(id: Long): Portfolio? {
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
  fun findAll(): List<Portfolio> {
    val portfolios =
      dsl
        .selectFrom(PORTFOLIOS)
        .orderBy(PORTFOLIOS.CREATED_AT.desc())
        .fetchInto(Portfolios::class.java)

    return portfolios.map { mapper.toDomain(it) }
  }

  /**
   * Find portfolios by user ID
   */
  fun findByUserId(userId: String): List<Portfolio> {
    val portfolios =
      dsl
        .selectFrom(PORTFOLIOS)
        .where(PORTFOLIOS.USER_ID.eq(userId))
        .orderBy(PORTFOLIOS.CREATED_AT.desc())
        .fetchInto(Portfolios::class.java)

    return portfolios.map { mapper.toDomain(it) }
  }

  /**
   * Save portfolio
   * Performs an upsert (insert or update)
   */
  fun save(portfolio: Portfolio): Portfolio {
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
          .set(PORTFOLIOS.CREATED_AT, pojo.createdAt)
          .set(PORTFOLIOS.UPDATED_AT, pojo.updatedAt)
          .set(PORTFOLIOS.BROKER, pojo.broker)
          .set(PORTFOLIOS.BROKER_CONFIG, pojo.brokerConfig)
          .set(PORTFOLIOS.LAST_SYNC_DATE, pojo.lastSyncDate)
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
        .set(PORTFOLIOS.UPDATED_AT, pojo.updatedAt)
        .set(PORTFOLIOS.BROKER, pojo.broker)
        .set(PORTFOLIOS.BROKER_CONFIG, pojo.brokerConfig)
        .set(PORTFOLIOS.LAST_SYNC_DATE, pojo.lastSyncDate)
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
