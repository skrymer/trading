package com.skrymer.udgaard.portfolio.repository

import com.skrymer.udgaard.jooq.tables.pojos.Positions
import com.skrymer.udgaard.jooq.tables.references.POSITIONS
import com.skrymer.udgaard.portfolio.mapper.PositionMapper
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.portfolio.model.Position
import com.skrymer.udgaard.portfolio.model.PositionStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * jOOQ-based repository for Position operations
 */
@Repository
class PositionJooqRepository(
  private val dsl: DSLContext,
  private val mapper: PositionMapper,
) {
  /**
   * Find position by ID
   */
  fun findById(id: Long): Position? {
    val position =
      dsl
        .selectFrom(POSITIONS)
        .where(POSITIONS.ID.eq(id))
        .fetchOneInto(Positions::class.java) ?: return null

    return mapper.toDomain(position)
  }

  /**
   * Find all positions for a portfolio
   */
  fun findByPortfolioId(portfolioId: Long): List<Position> {
    val positions =
      dsl
        .selectFrom(POSITIONS)
        .where(POSITIONS.PORTFOLIO_ID.eq(portfolioId))
        .orderBy(POSITIONS.OPENED_DATE.desc())
        .fetchInto(Positions::class.java)

    return positions.map { mapper.toDomain(it) }
  }

  /**
   * Find positions by portfolio ID and status
   */
  fun findByPortfolioIdAndStatus(
    portfolioId: Long,
    status: PositionStatus,
  ): List<Position> {
    val positions =
      dsl
        .selectFrom(POSITIONS)
        .where(POSITIONS.PORTFOLIO_ID.eq(portfolioId))
        .and(POSITIONS.STATUS.eq(status.name))
        .orderBy(POSITIONS.OPENED_DATE.desc())
        .fetchInto(Positions::class.java)

    return positions.map { mapper.toDomain(it) }
  }

  /**
   * Find open position by characteristics (used for broker import)
   * Matches symbol, strike, expiry, and option type for options
   * For stocks, matches just symbol
   */
  fun findOpenPositionByCharacteristics(
    portfolioId: Long,
    symbol: String,
    strikePrice: Double?,
    expirationDate: LocalDate?,
    optionType: OptionType?,
  ): Position? {
    val query =
      dsl
        .selectFrom(POSITIONS)
        .where(POSITIONS.PORTFOLIO_ID.eq(portfolioId))
        .and(POSITIONS.SYMBOL.eq(symbol))
        .and(POSITIONS.STATUS.eq(PositionStatus.OPEN.name))

    // For options, match strike, expiry, and type
    if (strikePrice != null && expirationDate != null && optionType != null) {
      query
        .and(POSITIONS.STRIKE_PRICE.eq(strikePrice.toBigDecimal()))
        .and(POSITIONS.EXPIRATION_DATE.eq(expirationDate))
        .and(POSITIONS.OPTION_TYPE.eq(optionType.name))
    }

    val position = query.fetchOneInto(Positions::class.java) ?: return null
    return mapper.toDomain(position)
  }

  /**
   * Find open position by underlying symbol (for roll consolidation)
   */
  fun findOpenPositionByUnderlying(
    portfolioId: Long,
    underlyingSymbol: String,
  ): Position? {
    val position =
      dsl
        .selectFrom(POSITIONS)
        .where(POSITIONS.PORTFOLIO_ID.eq(portfolioId))
        .and(POSITIONS.UNDERLYING_SYMBOL.eq(underlyingSymbol))
        .and(POSITIONS.STATUS.eq(PositionStatus.OPEN.name))
        .fetchOneInto(Positions::class.java)
        ?: return null

    return mapper.toDomain(position)
  }

  /**
   * Save position (insert or update)
   */
  fun save(position: Position): Position {
    val pojo = mapper.toPojo(position)

    if (position.id == null) {
      // Insert new position
      val record =
        dsl
          .insertInto(POSITIONS)
          .set(POSITIONS.PORTFOLIO_ID, pojo.portfolioId)
          .set(POSITIONS.SYMBOL, pojo.symbol)
          .set(POSITIONS.UNDERLYING_SYMBOL, pojo.underlyingSymbol)
          .set(POSITIONS.INSTRUMENT_TYPE, pojo.instrumentType)
          .set(POSITIONS.OPTION_TYPE, pojo.optionType)
          .set(POSITIONS.STRIKE_PRICE, pojo.strikePrice)
          .set(POSITIONS.EXPIRATION_DATE, pojo.expirationDate)
          .set(POSITIONS.MULTIPLIER, pojo.multiplier)
          .set(POSITIONS.CURRENT_QUANTITY, pojo.currentQuantity)
          .set(POSITIONS.CURRENT_CONTRACTS, pojo.currentContracts)
          .set(POSITIONS.AVERAGE_ENTRY_PRICE, pojo.averageEntryPrice)
          .set(POSITIONS.TOTAL_COST, pojo.totalCost)
          .set(POSITIONS.STATUS, pojo.status)
          .set(POSITIONS.OPENED_DATE, pojo.openedDate)
          .set(POSITIONS.CLOSED_DATE, pojo.closedDate)
          .set(POSITIONS.REALIZED_PNL, pojo.realizedPnl)
          .set(POSITIONS.ROLLED_TO_POSITION_ID, pojo.rolledToPositionId)
          .set(POSITIONS.PARENT_POSITION_ID, pojo.parentPositionId)
          .set(POSITIONS.ROLL_NUMBER, pojo.rollNumber)
          .set(POSITIONS.ENTRY_STRATEGY, pojo.entryStrategy)
          .set(POSITIONS.EXIT_STRATEGY, pojo.exitStrategy)
          .set(POSITIONS.NOTES, pojo.notes)
          .set(POSITIONS.CURRENCY, pojo.currency)
          .set(POSITIONS.SOURCE, pojo.source)
          .returningResult(POSITIONS.ID)
          .fetchOne()

      val newId = record?.getValue(POSITIONS.ID) ?: throw IllegalStateException("Failed to insert position")
      return position.copy(id = newId)
    } else {
      // Update existing position
      dsl
        .update(POSITIONS)
        .set(POSITIONS.PORTFOLIO_ID, pojo.portfolioId)
        .set(POSITIONS.SYMBOL, pojo.symbol)
        .set(POSITIONS.UNDERLYING_SYMBOL, pojo.underlyingSymbol)
        .set(POSITIONS.INSTRUMENT_TYPE, pojo.instrumentType)
        .set(POSITIONS.OPTION_TYPE, pojo.optionType)
        .set(POSITIONS.STRIKE_PRICE, pojo.strikePrice)
        .set(POSITIONS.EXPIRATION_DATE, pojo.expirationDate)
        .set(POSITIONS.MULTIPLIER, pojo.multiplier)
        .set(POSITIONS.CURRENT_QUANTITY, pojo.currentQuantity)
        .set(POSITIONS.CURRENT_CONTRACTS, pojo.currentContracts)
        .set(POSITIONS.AVERAGE_ENTRY_PRICE, pojo.averageEntryPrice)
        .set(POSITIONS.TOTAL_COST, pojo.totalCost)
        .set(POSITIONS.STATUS, pojo.status)
        .set(POSITIONS.OPENED_DATE, pojo.openedDate)
        .set(POSITIONS.CLOSED_DATE, pojo.closedDate)
        .set(POSITIONS.REALIZED_PNL, pojo.realizedPnl)
        .set(POSITIONS.ROLLED_TO_POSITION_ID, pojo.rolledToPositionId)
        .set(POSITIONS.PARENT_POSITION_ID, pojo.parentPositionId)
        .set(POSITIONS.ROLL_NUMBER, pojo.rollNumber)
        .set(POSITIONS.ENTRY_STRATEGY, pojo.entryStrategy)
        .set(POSITIONS.EXIT_STRATEGY, pojo.exitStrategy)
        .set(POSITIONS.NOTES, pojo.notes)
        .set(POSITIONS.CURRENCY, pojo.currency)
        .set(POSITIONS.SOURCE, pojo.source)
        .set(POSITIONS.UPDATED_AT, LocalDateTime.now())
        .where(POSITIONS.ID.eq(position.id))
        .execute()

      return position
    }
  }

  /**
   * Delete position by ID
   */
  fun delete(id: Long) {
    dsl
      .deleteFrom(POSITIONS)
      .where(POSITIONS.ID.eq(id))
      .execute()
  }
}
