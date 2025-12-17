package com.skrymer.udgaard.repository.jooq

import com.skrymer.udgaard.domain.PortfolioTradeDomain
import com.skrymer.udgaard.domain.TradeStatusDomain
import com.skrymer.udgaard.jooq.tables.pojos.PortfolioTrades
import com.skrymer.udgaard.jooq.tables.references.PORTFOLIO_TRADES
import com.skrymer.udgaard.mapper.PortfolioTradeMapper
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/**
 * jOOQ-based repository for PortfolioTrade operations
 * Replaces the Hibernate PortfolioTradeRepository
 */
@Repository
class PortfolioTradeJooqRepository(
  private val dsl: DSLContext,
  private val mapper: PortfolioTradeMapper,
) {
  /**
   * Find trade by ID
   */
  fun findById(id: Long): PortfolioTradeDomain? {
    val trade =
      dsl
        .selectFrom(PORTFOLIO_TRADES)
        .where(PORTFOLIO_TRADES.ID.eq(id))
        .fetchOneInto(PortfolioTrades::class.java) ?: return null

    return mapper.toDomain(trade)
  }

  /**
   * Find all trades for a portfolio
   */
  fun findByPortfolioId(portfolioId: Long): List<PortfolioTradeDomain> {
    val trades =
      dsl
        .selectFrom(PORTFOLIO_TRADES)
        .where(PORTFOLIO_TRADES.PORTFOLIO_ID.eq(portfolioId))
        .orderBy(PORTFOLIO_TRADES.ENTRY_DATE.desc())
        .fetchInto(PortfolioTrades::class.java)

    return trades.map { mapper.toDomain(it) }
  }

  /**
   * Find trades by portfolio ID and status
   */
  fun findByPortfolioIdAndStatus(
    portfolioId: Long,
    status: TradeStatusDomain,
  ): List<PortfolioTradeDomain> {
    val jooqStatus =
      when (status) {
        TradeStatusDomain.OPEN -> com.skrymer.udgaard.jooq.enums.PortfolioTradesStatus.OPEN
        TradeStatusDomain.CLOSED -> com.skrymer.udgaard.jooq.enums.PortfolioTradesStatus.CLOSED
      }

    val trades =
      dsl
        .selectFrom(PORTFOLIO_TRADES)
        .where(
          PORTFOLIO_TRADES.PORTFOLIO_ID
            .eq(portfolioId)
            .and(PORTFOLIO_TRADES.STATUS.eq(jooqStatus)),
        ).orderBy(PORTFOLIO_TRADES.ENTRY_DATE.desc())
        .fetchInto(PortfolioTrades::class.java)

    return trades.map { mapper.toDomain(it) }
  }

  /**
   * Save trade
   * Performs an upsert (insert or update)
   */
  fun save(trade: PortfolioTradeDomain): PortfolioTradeDomain {
    val pojo = mapper.toPojo(trade)

    if (trade.id == null) {
      // Insert new trade
      val record =
        dsl
          .insertInto(PORTFOLIO_TRADES)
          .set(PORTFOLIO_TRADES.PORTFOLIO_ID, pojo.portfolioId)
          .set(PORTFOLIO_TRADES.SYMBOL, pojo.symbol)
          .set(PORTFOLIO_TRADES.INSTRUMENT_TYPE, pojo.instrumentType)
          .set(PORTFOLIO_TRADES.OPTION_TYPE, pojo.optionType)
          .set(PORTFOLIO_TRADES.STRIKE_PRICE, pojo.strikePrice)
          .set(PORTFOLIO_TRADES.EXPIRATION_DATE, pojo.expirationDate)
          .set(PORTFOLIO_TRADES.CONTRACTS, pojo.contracts)
          .set(PORTFOLIO_TRADES.MULTIPLIER, pojo.multiplier)
          .set(PORTFOLIO_TRADES.ENTRY_INTRINSIC_VALUE, pojo.entryIntrinsicValue)
          .set(PORTFOLIO_TRADES.ENTRY_EXTRINSIC_VALUE, pojo.entryExtrinsicValue)
          .set(PORTFOLIO_TRADES.EXIT_INTRINSIC_VALUE, pojo.exitIntrinsicValue)
          .set(PORTFOLIO_TRADES.EXIT_EXTRINSIC_VALUE, pojo.exitExtrinsicValue)
          .set(PORTFOLIO_TRADES.UNDERLYING_ENTRY_PRICE, pojo.underlyingEntryPrice)
          .set(PORTFOLIO_TRADES.ENTRY_PRICE, pojo.entryPrice)
          .set(PORTFOLIO_TRADES.ENTRY_DATE, pojo.entryDate)
          .set(PORTFOLIO_TRADES.EXIT_PRICE, pojo.exitPrice)
          .set(PORTFOLIO_TRADES.EXIT_DATE, pojo.exitDate)
          .set(PORTFOLIO_TRADES.QUANTITY, pojo.quantity)
          .set(PORTFOLIO_TRADES.ENTRY_STRATEGY, pojo.entryStrategy)
          .set(PORTFOLIO_TRADES.EXIT_STRATEGY, pojo.exitStrategy)
          .set(PORTFOLIO_TRADES.CURRENCY, pojo.currency)
          .set(PORTFOLIO_TRADES.STATUS, pojo.status)
          .set(PORTFOLIO_TRADES.UNDERLYING_SYMBOL, pojo.underlyingSymbol)
          .set(PORTFOLIO_TRADES.PARENT_TRADE_ID, pojo.parentTradeId)
          .set(PORTFOLIO_TRADES.ROLLED_TO_TRADE_ID, pojo.rolledToTradeId)
          .set(PORTFOLIO_TRADES.ROLL_NUMBER, pojo.rollNumber)
          .set(PORTFOLIO_TRADES.ORIGINAL_ENTRY_DATE, pojo.originalEntryDate)
          .set(PORTFOLIO_TRADES.ORIGINAL_COST_BASIS, pojo.originalCostBasis)
          .set(PORTFOLIO_TRADES.CUMULATIVE_REALIZED_PROFIT, pojo.cumulativeRealizedProfit)
          .set(PORTFOLIO_TRADES.TOTAL_ROLL_COST, pojo.totalRollCost)
          .returningResult(PORTFOLIO_TRADES.ID)
          .fetchOne()

      val newId = record?.getValue(PORTFOLIO_TRADES.ID) ?: throw IllegalStateException("Failed to insert trade")
      return trade.copy(id = newId)
    } else {
      // Update existing trade
      dsl
        .update(PORTFOLIO_TRADES)
        .set(PORTFOLIO_TRADES.PORTFOLIO_ID, pojo.portfolioId)
        .set(PORTFOLIO_TRADES.SYMBOL, pojo.symbol)
        .set(PORTFOLIO_TRADES.INSTRUMENT_TYPE, pojo.instrumentType)
        .set(PORTFOLIO_TRADES.OPTION_TYPE, pojo.optionType)
        .set(PORTFOLIO_TRADES.STRIKE_PRICE, pojo.strikePrice)
        .set(PORTFOLIO_TRADES.EXPIRATION_DATE, pojo.expirationDate)
        .set(PORTFOLIO_TRADES.CONTRACTS, pojo.contracts)
        .set(PORTFOLIO_TRADES.MULTIPLIER, pojo.multiplier)
        .set(PORTFOLIO_TRADES.ENTRY_INTRINSIC_VALUE, pojo.entryIntrinsicValue)
        .set(PORTFOLIO_TRADES.ENTRY_EXTRINSIC_VALUE, pojo.entryExtrinsicValue)
        .set(PORTFOLIO_TRADES.EXIT_INTRINSIC_VALUE, pojo.exitIntrinsicValue)
        .set(PORTFOLIO_TRADES.EXIT_EXTRINSIC_VALUE, pojo.exitExtrinsicValue)
        .set(PORTFOLIO_TRADES.UNDERLYING_ENTRY_PRICE, pojo.underlyingEntryPrice)
        .set(PORTFOLIO_TRADES.ENTRY_PRICE, pojo.entryPrice)
        .set(PORTFOLIO_TRADES.ENTRY_DATE, pojo.entryDate)
        .set(PORTFOLIO_TRADES.EXIT_PRICE, pojo.exitPrice)
        .set(PORTFOLIO_TRADES.EXIT_DATE, pojo.exitDate)
        .set(PORTFOLIO_TRADES.QUANTITY, pojo.quantity)
        .set(PORTFOLIO_TRADES.ENTRY_STRATEGY, pojo.entryStrategy)
        .set(PORTFOLIO_TRADES.EXIT_STRATEGY, pojo.exitStrategy)
        .set(PORTFOLIO_TRADES.CURRENCY, pojo.currency)
        .set(PORTFOLIO_TRADES.STATUS, pojo.status)
        .set(PORTFOLIO_TRADES.UNDERLYING_SYMBOL, pojo.underlyingSymbol)
        .set(PORTFOLIO_TRADES.PARENT_TRADE_ID, pojo.parentTradeId)
        .set(PORTFOLIO_TRADES.ROLLED_TO_TRADE_ID, pojo.rolledToTradeId)
        .set(PORTFOLIO_TRADES.ROLL_NUMBER, pojo.rollNumber)
        .set(PORTFOLIO_TRADES.ORIGINAL_ENTRY_DATE, pojo.originalEntryDate)
        .set(PORTFOLIO_TRADES.ORIGINAL_COST_BASIS, pojo.originalCostBasis)
        .set(PORTFOLIO_TRADES.CUMULATIVE_REALIZED_PROFIT, pojo.cumulativeRealizedProfit)
        .set(PORTFOLIO_TRADES.TOTAL_ROLL_COST, pojo.totalRollCost)
        .where(PORTFOLIO_TRADES.ID.eq(trade.id))
        .execute()

      return trade
    }
  }

  /**
   * Delete trade by ID
   */
  fun delete(id: Long) {
    dsl
      .deleteFrom(PORTFOLIO_TRADES)
      .where(PORTFOLIO_TRADES.ID.eq(id))
      .execute()
  }

  /**
   * Check if trade exists
   */
  fun exists(id: Long): Boolean =
    dsl
      .selectCount()
      .from(PORTFOLIO_TRADES)
      .where(PORTFOLIO_TRADES.ID.eq(id))
      .fetchOne(0, Int::class.java) ?: 0 > 0
}
