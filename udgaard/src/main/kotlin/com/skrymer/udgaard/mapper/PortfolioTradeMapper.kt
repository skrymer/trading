package com.skrymer.udgaard.mapper

import com.skrymer.udgaard.domain.InstrumentTypeDomain
import com.skrymer.udgaard.domain.OptionTypeDomain
import com.skrymer.udgaard.domain.PortfolioTradeDomain
import com.skrymer.udgaard.domain.TradeStatusDomain
import com.skrymer.udgaard.jooq.enums.PortfolioTradesInstrumentType
import com.skrymer.udgaard.jooq.enums.PortfolioTradesOptionType
import com.skrymer.udgaard.jooq.enums.PortfolioTradesStatus
import com.skrymer.udgaard.jooq.tables.pojos.PortfolioTrades
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ PortfolioTrade POJOs and domain models
 */
@Component
class PortfolioTradeMapper {
  /**
   * Convert jOOQ PortfolioTrade POJO to domain model
   */
  fun toDomain(trade: PortfolioTrades): PortfolioTradeDomain =
    PortfolioTradeDomain(
      id = trade.id,
      portfolioId = trade.portfolioId ?: 0,
      symbol = trade.symbol ?: "",
      instrumentType =
        when (trade.instrumentType) {
          PortfolioTradesInstrumentType.STOCK -> InstrumentTypeDomain.STOCK
          PortfolioTradesInstrumentType.OPTION -> InstrumentTypeDomain.OPTION
          PortfolioTradesInstrumentType.LEVERAGED_ETF -> InstrumentTypeDomain.LEVERAGED_ETF
          null -> InstrumentTypeDomain.STOCK
        },
      optionType =
        when (trade.optionType) {
          PortfolioTradesOptionType.CALL -> OptionTypeDomain.CALL
          PortfolioTradesOptionType.PUT -> OptionTypeDomain.PUT
          null -> null
        },
      strikePrice = trade.strikePrice,
      expirationDate = trade.expirationDate,
      contracts = trade.contracts,
      multiplier = trade.multiplier ?: 100,
      entryIntrinsicValue = trade.entryIntrinsicValue,
      entryExtrinsicValue = trade.entryExtrinsicValue,
      exitIntrinsicValue = trade.exitIntrinsicValue,
      exitExtrinsicValue = trade.exitExtrinsicValue,
      underlyingEntryPrice = trade.underlyingEntryPrice,
      entryPrice = trade.entryPrice ?: 0.0,
      entryDate = trade.entryDate ?: java.time.LocalDate.now(),
      exitPrice = trade.exitPrice,
      exitDate = trade.exitDate,
      quantity = trade.quantity ?: 0,
      entryStrategy = trade.entryStrategy ?: "",
      exitStrategy = trade.exitStrategy ?: "",
      currency = trade.currency ?: "USD",
      status =
        when (trade.status) {
          PortfolioTradesStatus.OPEN -> TradeStatusDomain.OPEN
          PortfolioTradesStatus.CLOSED -> TradeStatusDomain.CLOSED
          null -> TradeStatusDomain.OPEN
        },
      underlyingSymbol = trade.underlyingSymbol,
      parentTradeId = trade.parentTradeId,
      rolledToTradeId = trade.rolledToTradeId,
      rollNumber = trade.rollNumber ?: 0,
      originalEntryDate = trade.originalEntryDate,
      originalCostBasis = trade.originalCostBasis,
      cumulativeRealizedProfit = trade.cumulativeRealizedProfit,
      totalRollCost = trade.totalRollCost,
    )

  /**
   * Convert domain model to jOOQ PortfolioTrade POJO
   */
  fun toPojo(trade: PortfolioTradeDomain): PortfolioTrades =
    PortfolioTrades(
      id = trade.id,
      portfolioId = trade.portfolioId,
      symbol = trade.symbol,
      instrumentType =
        when (trade.instrumentType) {
          InstrumentTypeDomain.STOCK -> PortfolioTradesInstrumentType.STOCK
          InstrumentTypeDomain.OPTION -> PortfolioTradesInstrumentType.OPTION
          InstrumentTypeDomain.LEVERAGED_ETF -> PortfolioTradesInstrumentType.LEVERAGED_ETF
        },
      optionType =
        when (trade.optionType) {
          OptionTypeDomain.CALL -> PortfolioTradesOptionType.CALL
          OptionTypeDomain.PUT -> PortfolioTradesOptionType.PUT
          null -> null
        },
      strikePrice = trade.strikePrice,
      expirationDate = trade.expirationDate,
      contracts = trade.contracts,
      multiplier = trade.multiplier,
      entryIntrinsicValue = trade.entryIntrinsicValue,
      entryExtrinsicValue = trade.entryExtrinsicValue,
      exitIntrinsicValue = trade.exitIntrinsicValue,
      exitExtrinsicValue = trade.exitExtrinsicValue,
      underlyingEntryPrice = trade.underlyingEntryPrice,
      entryPrice = trade.entryPrice,
      entryDate = trade.entryDate,
      exitPrice = trade.exitPrice,
      exitDate = trade.exitDate,
      quantity = trade.quantity,
      entryStrategy = trade.entryStrategy,
      exitStrategy = trade.exitStrategy,
      currency = trade.currency,
      status =
        when (trade.status) {
          TradeStatusDomain.OPEN -> PortfolioTradesStatus.OPEN
          TradeStatusDomain.CLOSED -> PortfolioTradesStatus.CLOSED
        },
      underlyingSymbol = trade.underlyingSymbol,
      parentTradeId = trade.parentTradeId,
      rolledToTradeId = trade.rolledToTradeId,
      rollNumber = trade.rollNumber,
      originalEntryDate = trade.originalEntryDate,
      originalCostBasis = trade.originalCostBasis,
      cumulativeRealizedProfit = trade.cumulativeRealizedProfit,
      totalRollCost = trade.totalRollCost,
    )
}
