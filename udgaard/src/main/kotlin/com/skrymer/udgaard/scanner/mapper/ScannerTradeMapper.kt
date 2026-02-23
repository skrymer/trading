package com.skrymer.udgaard.scanner.mapper

import com.skrymer.udgaard.jooq.tables.pojos.ScannerTrades
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.scanner.model.ScannerTrade
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ ScannerTrades POJOs and domain models
 */
@Component
class ScannerTradeMapper {
  fun toDomain(pojo: ScannerTrades): ScannerTrade =
    ScannerTrade(
      id = pojo.id,
      symbol = pojo.symbol,
      sectorSymbol = pojo.sectorSymbol,
      instrumentType =
        when (pojo.instrumentType) {
          "STOCK" -> InstrumentType.STOCK
          "OPTION" -> InstrumentType.OPTION
          "LEVERAGED_ETF" -> InstrumentType.LEVERAGED_ETF
          else -> InstrumentType.STOCK
        },
      entryPrice = pojo.entryPrice.toDouble(),
      entryDate = pojo.entryDate,
      quantity = pojo.quantity,
      optionType =
        when (pojo.optionType) {
          "CALL" -> OptionType.CALL
          "PUT" -> OptionType.PUT
          else -> null
        },
      strikePrice = pojo.strikePrice?.toDouble(),
      expirationDate = pojo.expirationDate,
      multiplier = pojo.multiplier ?: 100,
      entryStrategyName = pojo.entryStrategyName,
      exitStrategyName = pojo.exitStrategyName,
      rolledCredits = pojo.rolledCredits?.toDouble() ?: 0.0,
      rollCount = pojo.rollCount ?: 0,
      notes = pojo.notes,
      createdAt = pojo.createdAt,
      updatedAt = pojo.updatedAt,
    )

  fun toPojo(trade: ScannerTrade): ScannerTrades =
    ScannerTrades(
      symbol = trade.symbol,
      sectorSymbol = trade.sectorSymbol,
      instrumentType =
        when (trade.instrumentType) {
          InstrumentType.STOCK -> "STOCK"
          InstrumentType.OPTION -> "OPTION"
          InstrumentType.LEVERAGED_ETF -> "LEVERAGED_ETF"
        },
      entryPrice = trade.entryPrice.toBigDecimal(),
      entryDate = trade.entryDate,
      quantity = trade.quantity,
      optionType =
        when (trade.optionType) {
          OptionType.CALL -> "CALL"
          OptionType.PUT -> "PUT"
          null -> null
        },
      strikePrice = trade.strikePrice?.toBigDecimal(),
      expirationDate = trade.expirationDate,
      multiplier = trade.multiplier,
      entryStrategyName = trade.entryStrategyName,
      exitStrategyName = trade.exitStrategyName,
      rolledCredits = trade.rolledCredits.toBigDecimal(),
      rollCount = trade.rollCount,
      notes = trade.notes,
      createdAt = trade.createdAt,
      updatedAt = trade.updatedAt,
    ).apply {
      id = trade.id
    }
}
