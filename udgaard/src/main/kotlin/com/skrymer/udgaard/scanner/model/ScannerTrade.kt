package com.skrymer.udgaard.scanner.model

import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import java.time.LocalDate
import java.time.LocalDateTime

enum class TradeStatus { OPEN, CLOSED }

data class ScannerTrade(
  val id: Long?,
  val symbol: String,
  val sectorSymbol: String?,
  val instrumentType: InstrumentType,
  val entryPrice: Double,
  val entryDate: LocalDate,
  val quantity: Int,
  val optionType: OptionType?,
  val strikePrice: Double?,
  val expirationDate: LocalDate?,
  val multiplier: Int = 100,
  val optionPrice: Double? = null,
  val delta: Double? = null,
  val entryStrategyName: String,
  val exitStrategyName: String,
  val rolledCredits: Double = 0.0,
  val rollCount: Int = 0,
  val notes: String?,
  val createdAt: LocalDateTime? = null,
  val updatedAt: LocalDateTime? = null,
  val status: TradeStatus = TradeStatus.OPEN,
  val exitPrice: Double? = null,
  val exitDate: LocalDate? = null,
  val realizedPnl: Double? = null,
  val closedAt: LocalDateTime? = null,
)
