package com.skrymer.udgaard.scanner.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.jooq.tables.pojos.ScannerTrades
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.model.TradeStatus
import org.jooq.JSONB
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ScannerTradeMapper(
  private val objectMapper: ObjectMapper,
) {
  private val logger = LoggerFactory.getLogger(ScannerTradeMapper::class.java)

  fun toDomain(pojo: ScannerTrades): ScannerTrade =
    ScannerTrade(
      id = pojo.id,
      symbol = pojo.symbol,
      sectorSymbol = pojo.sectorSymbol,
      instrumentType =
        when (pojo.instrumentType) {
          "STOCK" -> InstrumentType.STOCK
          "OPTION" -> InstrumentType.OPTION
          "ETF" -> InstrumentType.ETF
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
      optionPrice = pojo.optionPrice?.toDouble(),
      delta = pojo.delta?.toDouble(),
      entryStrategyName = pojo.entryStrategyName,
      exitStrategyName = pojo.exitStrategyName,
      rolledCredits = pojo.rolledCredits?.toDouble() ?: 0.0,
      rollCount = pojo.rollCount ?: 0,
      notes = pojo.notes,
      createdAt = pojo.createdAt,
      updatedAt = pojo.updatedAt,
      status =
        when (pojo.status) {
          "CLOSED" -> TradeStatus.CLOSED
          else -> TradeStatus.OPEN
        },
      exitPrice = pojo.exitPrice?.toDouble(),
      exitDate = pojo.exitDate,
      realizedPnl = pojo.realizedPnl?.toDouble(),
      closedAt = pojo.closedAt,
      signalDate = pojo.signalDate,
      signalSnapshot = pojo.signalSnapshot?.let { deserializeSnapshot(pojo.id, it) },
    )

  // A single corrupted blob must not take down findOpen()/findAll() for every other trade.
  // Per ADR 0004, "NULL is informative" — degrade to NULL on parse failure and log loudly so
  // the row stays readable and audits can flag the bad blob.
  private fun deserializeSnapshot(tradeId: Long?, blob: JSONB): EntrySignalDetails? =
    runCatching { objectMapper.readValue(blob.data(), EntrySignalDetails::class.java) }
      .onFailure { logger.warn("Failed to deserialize signal_snapshot for scanner_trade id=$tradeId — storing as null", it) }
      .getOrNull()

  fun toPojo(trade: ScannerTrade): ScannerTrades =
    ScannerTrades(
      symbol = trade.symbol,
      sectorSymbol = trade.sectorSymbol,
      instrumentType =
        when (trade.instrumentType) {
          InstrumentType.STOCK -> "STOCK"
          InstrumentType.OPTION -> "OPTION"
          InstrumentType.ETF -> "ETF"
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
      optionPrice = trade.optionPrice?.toBigDecimal(),
      delta = trade.delta?.toBigDecimal(),
      entryStrategyName = trade.entryStrategyName,
      exitStrategyName = trade.exitStrategyName,
      rolledCredits = trade.rolledCredits.toBigDecimal(),
      rollCount = trade.rollCount,
      notes = trade.notes,
      createdAt = trade.createdAt,
      updatedAt = trade.updatedAt,
      status = trade.status.name,
      exitPrice = trade.exitPrice?.toBigDecimal(),
      exitDate = trade.exitDate,
      realizedPnl = trade.realizedPnl?.toBigDecimal(),
      closedAt = trade.closedAt,
      signalDate = trade.signalDate,
      signalSnapshot = trade.signalSnapshot?.let { JSONB.valueOf(objectMapper.writeValueAsString(it)) },
    ).apply {
      id = trade.id
    }
}
