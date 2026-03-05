package com.skrymer.udgaard.portfolio.mapper

import com.skrymer.udgaard.jooq.tables.pojos.Positions
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.portfolio.model.Position
import com.skrymer.udgaard.portfolio.model.PositionSource
import com.skrymer.udgaard.portfolio.model.PositionStatus
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ Position POJOs and domain models
 */
@Component
class PositionMapper {
  /**
   * Convert jOOQ Position POJO to domain model
   */
  fun toDomain(position: Positions): Position =
    Position(
      id = position.id,
      portfolioId = position.portfolioId,
      symbol = position.symbol,
      underlyingSymbol = position.underlyingSymbol,
      instrumentType =
        when (position.instrumentType) {
          "STOCK" -> InstrumentType.STOCK
          "OPTION" -> InstrumentType.OPTION
          "LEVERAGED_ETF" -> InstrumentType.LEVERAGED_ETF
          else -> InstrumentType.STOCK
        },
      optionType =
        when (position.optionType) {
          "CALL" -> OptionType.CALL
          "PUT" -> OptionType.PUT
          else -> null
        },
      strikePrice = position.strikePrice?.toDouble(),
      expirationDate = position.expirationDate,
      multiplier = position.multiplier ?: 100,
      currentQuantity = position.currentQuantity,
      currentContracts = position.currentContracts,
      averageEntryPrice = position.averageEntryPrice.toDouble(),
      totalCost = position.totalCost.toDouble(),
      status =
        when (position.status) {
          "OPEN" -> PositionStatus.OPEN
          "CLOSED" -> PositionStatus.CLOSED
          else -> PositionStatus.OPEN
        },
      openedDate = position.openedDate,
      closedDate = position.closedDate,
      realizedPnl = position.realizedPnl?.toDouble(),
      realizedPnlBase = position.realizedPnlBase?.toDouble(),
      rolledToPositionId = position.rolledToPositionId,
      parentPositionId = position.parentPositionId,
      rollNumber = position.rollNumber ?: 0,
      entryStrategy = position.entryStrategy,
      exitStrategy = position.exitStrategy,
      notes = position.notes,
      currency = position.currency ?: "USD",
      source =
        when (position.source) {
          "BROKER" -> PositionSource.BROKER
          "MANUAL" -> PositionSource.MANUAL
          else -> PositionSource.MANUAL
        },
      createdAt = position.createdAt,
      updatedAt = position.updatedAt,
    )

  /**
   * Convert domain model to jOOQ Position POJO
   */
  fun toPojo(position: Position): Positions =
    Positions(
      portfolioId = position.portfolioId,
      symbol = position.symbol,
      underlyingSymbol = position.underlyingSymbol,
      instrumentType =
        when (position.instrumentType) {
          InstrumentType.STOCK -> "STOCK"
          InstrumentType.OPTION -> "OPTION"
          InstrumentType.LEVERAGED_ETF -> "LEVERAGED_ETF"
        },
      optionType =
        when (position.optionType) {
          OptionType.CALL -> "CALL"
          OptionType.PUT -> "PUT"
          null -> null
        },
      strikePrice = position.strikePrice?.toBigDecimal(),
      expirationDate = position.expirationDate,
      multiplier = position.multiplier,
      currentQuantity = position.currentQuantity,
      currentContracts = position.currentContracts,
      averageEntryPrice = position.averageEntryPrice.toBigDecimal(),
      totalCost = position.totalCost.toBigDecimal(),
      status =
        when (position.status) {
          PositionStatus.OPEN -> "OPEN"
          PositionStatus.CLOSED -> "CLOSED"
        },
      openedDate = position.openedDate,
      closedDate = position.closedDate,
      realizedPnl = position.realizedPnl?.toBigDecimal(),
      realizedPnlBase = position.realizedPnlBase?.toBigDecimal(),
      rolledToPositionId = position.rolledToPositionId,
      parentPositionId = position.parentPositionId,
      rollNumber = position.rollNumber,
      entryStrategy = position.entryStrategy,
      exitStrategy = position.exitStrategy,
      notes = position.notes,
      currency = position.currency,
      source =
        when (position.source) {
          PositionSource.BROKER -> "BROKER"
          PositionSource.MANUAL -> "MANUAL"
        },
      createdAt = position.createdAt,
      updatedAt = position.updatedAt,
    ).apply {
      id = position.id
    }
}
