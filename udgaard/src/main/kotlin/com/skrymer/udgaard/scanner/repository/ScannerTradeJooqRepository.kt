package com.skrymer.udgaard.scanner.repository

import com.skrymer.udgaard.jooq.tables.pojos.ScannerTrades
import com.skrymer.udgaard.jooq.tables.references.SCANNER_TRADES
import com.skrymer.udgaard.scanner.mapper.ScannerTradeMapper
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.model.TradeStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ScannerTradeJooqRepository(
  private val dsl: DSLContext,
  private val mapper: ScannerTradeMapper,
) {
  fun findOpen(): List<ScannerTrade> =
    dsl
      .selectFrom(SCANNER_TRADES)
      .where(SCANNER_TRADES.STATUS.eq(TradeStatus.OPEN.name))
      .orderBy(SCANNER_TRADES.ENTRY_DATE.desc())
      .fetchInto(ScannerTrades::class.java)
      .map { mapper.toDomain(it) }

  fun findClosed(): List<ScannerTrade> =
    dsl
      .selectFrom(SCANNER_TRADES)
      .where(SCANNER_TRADES.STATUS.eq(TradeStatus.CLOSED.name))
      .orderBy(SCANNER_TRADES.CLOSED_AT.desc())
      .fetchInto(ScannerTrades::class.java)
      .map { mapper.toDomain(it) }

  fun findAll(): List<ScannerTrade> =
    dsl
      .selectFrom(SCANNER_TRADES)
      .orderBy(SCANNER_TRADES.ENTRY_DATE.desc())
      .fetchInto(ScannerTrades::class.java)
      .map { mapper.toDomain(it) }

  fun findById(id: Long): ScannerTrade? {
    val trade =
      dsl
        .selectFrom(SCANNER_TRADES)
        .where(SCANNER_TRADES.ID.eq(id))
        .fetchOneInto(ScannerTrades::class.java) ?: return null

    return mapper.toDomain(trade)
  }

  fun save(trade: ScannerTrade): ScannerTrade {
    val pojo = mapper.toPojo(trade)
    return if (trade.id == null) insertNew(trade, pojo) else updateExisting(trade, pojo)
  }

  private fun insertNew(
    trade: ScannerTrade,
    pojo: ScannerTrades,
  ): ScannerTrade {
    val record =
      dsl
        .insertInto(SCANNER_TRADES)
        .set(SCANNER_TRADES.SYMBOL, pojo.symbol)
        .set(SCANNER_TRADES.SECTOR_SYMBOL, pojo.sectorSymbol)
        .set(SCANNER_TRADES.INSTRUMENT_TYPE, pojo.instrumentType)
        .set(SCANNER_TRADES.ENTRY_PRICE, pojo.entryPrice)
        .set(SCANNER_TRADES.ENTRY_DATE, pojo.entryDate)
        .set(SCANNER_TRADES.QUANTITY, pojo.quantity)
        .set(SCANNER_TRADES.OPTION_TYPE, pojo.optionType)
        .set(SCANNER_TRADES.STRIKE_PRICE, pojo.strikePrice)
        .set(SCANNER_TRADES.EXPIRATION_DATE, pojo.expirationDate)
        .set(SCANNER_TRADES.MULTIPLIER, pojo.multiplier)
        .set(SCANNER_TRADES.OPTION_PRICE, pojo.optionPrice)
        .set(SCANNER_TRADES.DELTA, pojo.delta)
        .set(SCANNER_TRADES.ENTRY_STRATEGY_NAME, pojo.entryStrategyName)
        .set(SCANNER_TRADES.EXIT_STRATEGY_NAME, pojo.exitStrategyName)
        .set(SCANNER_TRADES.ROLLED_CREDITS, pojo.rolledCredits)
        .set(SCANNER_TRADES.ROLL_COUNT, pojo.rollCount)
        .set(SCANNER_TRADES.NOTES, pojo.notes)
        .set(SCANNER_TRADES.STATUS, pojo.status)
        .set(SCANNER_TRADES.SIGNAL_DATE, pojo.signalDate)
        .set(SCANNER_TRADES.SIGNAL_SNAPSHOT, pojo.signalSnapshot)
        .returningResult(SCANNER_TRADES.ID)
        .fetchOne()

    val newId = record?.getValue(SCANNER_TRADES.ID) ?: throw IllegalStateException("Failed to insert scanner trade")
    return trade.copy(id = newId)
  }

  // signal_date and signal_snapshot are intentionally omitted from the update set —
  // they're write-once at insert time per docs/adr/0004. Updates touch mutable trade
  // state only (notes, close fields, roll bookkeeping).
  private fun updateExisting(
    trade: ScannerTrade,
    pojo: ScannerTrades,
  ): ScannerTrade {
    dsl
      .update(SCANNER_TRADES)
      .set(SCANNER_TRADES.SYMBOL, pojo.symbol)
      .set(SCANNER_TRADES.SECTOR_SYMBOL, pojo.sectorSymbol)
      .set(SCANNER_TRADES.INSTRUMENT_TYPE, pojo.instrumentType)
      .set(SCANNER_TRADES.ENTRY_PRICE, pojo.entryPrice)
      .set(SCANNER_TRADES.ENTRY_DATE, pojo.entryDate)
      .set(SCANNER_TRADES.QUANTITY, pojo.quantity)
      .set(SCANNER_TRADES.OPTION_TYPE, pojo.optionType)
      .set(SCANNER_TRADES.STRIKE_PRICE, pojo.strikePrice)
      .set(SCANNER_TRADES.EXPIRATION_DATE, pojo.expirationDate)
      .set(SCANNER_TRADES.MULTIPLIER, pojo.multiplier)
      .set(SCANNER_TRADES.OPTION_PRICE, pojo.optionPrice)
      .set(SCANNER_TRADES.DELTA, pojo.delta)
      .set(SCANNER_TRADES.ENTRY_STRATEGY_NAME, pojo.entryStrategyName)
      .set(SCANNER_TRADES.EXIT_STRATEGY_NAME, pojo.exitStrategyName)
      .set(SCANNER_TRADES.ROLLED_CREDITS, pojo.rolledCredits)
      .set(SCANNER_TRADES.ROLL_COUNT, pojo.rollCount)
      .set(SCANNER_TRADES.NOTES, pojo.notes)
      .set(SCANNER_TRADES.STATUS, pojo.status)
      .set(SCANNER_TRADES.EXIT_PRICE, pojo.exitPrice)
      .set(SCANNER_TRADES.EXIT_DATE, pojo.exitDate)
      .set(SCANNER_TRADES.REALIZED_PNL, pojo.realizedPnl)
      .set(SCANNER_TRADES.CLOSED_AT, pojo.closedAt)
      .set(SCANNER_TRADES.UPDATED_AT, LocalDateTime.now())
      .where(SCANNER_TRADES.ID.eq(trade.id))
      .execute()

    return trade
  }

  fun delete(id: Long) {
    dsl
      .deleteFrom(SCANNER_TRADES)
      .where(SCANNER_TRADES.ID.eq(id))
      .execute()
  }

  fun deleteAll(): Int =
    dsl
      .deleteFrom(SCANNER_TRADES)
      .execute()
}
