package com.skrymer.udgaard.scanner.model

import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain model for a scanner trade — a lightweight tracked opportunity
 */
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
  val entryStrategyName: String,
  val exitStrategyName: String,
  val rolledCredits: Double = 0.0,
  val rollCount: Int = 0,
  val notes: String?,
  val createdAt: LocalDateTime? = null,
  val updatedAt: LocalDateTime? = null,
)
