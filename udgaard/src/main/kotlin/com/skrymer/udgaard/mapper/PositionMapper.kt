package com.skrymer.udgaard.mapper

import com.skrymer.udgaard.domain.InstrumentTypeDomain
import com.skrymer.udgaard.domain.OptionTypeDomain
import com.skrymer.udgaard.domain.PositionDomain
import com.skrymer.udgaard.domain.PositionSourceDomain
import com.skrymer.udgaard.domain.PositionStatusDomain
import com.skrymer.udgaard.jooq.tables.pojos.Positions
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ Position POJOs and domain models
 */
@Component
class PositionMapper {
  /**
   * Convert jOOQ Position POJO to domain model
   */
  fun toDomain(position: Positions): PositionDomain =
    PositionDomain(
      id = position.id,
      portfolioId = position.portfolioId ?: 0,
      symbol = position.symbol ?: "",
      underlyingSymbol = position.underlyingSymbol,
      instrumentType =
        when (position.instrumentType) {
          "STOCK" -> InstrumentTypeDomain.STOCK
          "OPTION" -> InstrumentTypeDomain.OPTION
          "LEVERAGED_ETF" -> InstrumentTypeDomain.LEVERAGED_ETF
          else -> InstrumentTypeDomain.STOCK
        },
      optionType =
        when (position.optionType) {
          "CALL" -> OptionTypeDomain.CALL
          "PUT" -> OptionTypeDomain.PUT
          else -> null
        },
      strikePrice = position.strikePrice?.toDouble(),
      expirationDate = position.expirationDate,
      multiplier = position.multiplier ?: 100,
      currentQuantity = position.currentQuantity ?: 0,
      currentContracts = position.currentContracts,
      averageEntryPrice = position.averageEntryPrice?.toDouble() ?: 0.0,
      totalCost = position.totalCost?.toDouble() ?: 0.0,
      status =
        when (position.status) {
          "OPEN" -> PositionStatusDomain.OPEN
          "CLOSED" -> PositionStatusDomain.CLOSED
          else -> PositionStatusDomain.OPEN
        },
      openedDate = position.openedDate ?: java.time.LocalDate.now(),
      closedDate = position.closedDate,
      realizedPnl = position.realizedPnl?.toDouble(),
      rolledToPositionId = position.rolledToPositionId,
      parentPositionId = position.parentPositionId,
      rollNumber = position.rollNumber ?: 0,
      entryStrategy = position.entryStrategy,
      exitStrategy = position.exitStrategy,
      notes = position.notes,
      currency = position.currency ?: "USD",
      source =
        when (position.source) {
          "BROKER" -> PositionSourceDomain.BROKER
          "MANUAL" -> PositionSourceDomain.MANUAL
          else -> PositionSourceDomain.MANUAL
        },
      createdAt = position.createdAt,
      updatedAt = position.updatedAt,
    )

  /**
   * Convert domain model to jOOQ Position POJO
   */
  fun toPojo(position: PositionDomain): Positions =
    Positions(
      portfolioId = position.portfolioId,
      symbol = position.symbol,
      underlyingSymbol = position.underlyingSymbol,
      instrumentType =
        when (position.instrumentType) {
          InstrumentTypeDomain.STOCK -> "STOCK"
          InstrumentTypeDomain.OPTION -> "OPTION"
          InstrumentTypeDomain.LEVERAGED_ETF -> "LEVERAGED_ETF"
        },
      optionType =
        when (position.optionType) {
          OptionTypeDomain.CALL -> "CALL"
          OptionTypeDomain.PUT -> "PUT"
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
          PositionStatusDomain.OPEN -> "OPEN"
          PositionStatusDomain.CLOSED -> "CLOSED"
        },
      openedDate = position.openedDate,
      closedDate = position.closedDate,
      realizedPnl = position.realizedPnl?.toBigDecimal(),
      rolledToPositionId = position.rolledToPositionId,
      parentPositionId = position.parentPositionId,
      rollNumber = position.rollNumber,
      entryStrategy = position.entryStrategy,
      exitStrategy = position.exitStrategy,
      notes = position.notes,
      currency = position.currency,
      source =
        when (position.source) {
          PositionSourceDomain.BROKER -> "BROKER"
          PositionSourceDomain.MANUAL -> "MANUAL"
        },
      createdAt = position.createdAt,
      updatedAt = position.updatedAt,
    ).apply {
      id = position.id
    }
}
