package com.skrymer.udgaard.scanner.model

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
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
  // Populated by ScannerService.getTrades() for open-trade responses; null on DB reads and
  // write-path responses where the number of trading days since entry is not relevant.
  val tradingDaysHeld: Int? = null,
  // The OHLCV bar the scanner matched on — immutable after add. Null on legacy rows
  // (pre-V21) and any path that adds a trade without a scanner match. See docs/adr/0004.
  val signalDate: LocalDate? = null,
  // Verbatim record of the per-condition evaluation for [signalDate]. Persisted as JSONB
  // and never recomputed on read — retroactive recomputes (breadth, indicator backfills)
  // would otherwise destroy the audit trail. See docs/adr/0004.
  val signalSnapshot: EntrySignalDetails? = null,
) {
  fun computeRealizedPnl(exitPrice: Double): Double =
    if (instrumentType == InstrumentType.OPTION) {
      (exitPrice - (optionPrice ?: entryPrice)) * quantity * multiplier + rolledCredits
    } else {
      (exitPrice - entryPrice) * quantity
    }

  fun withClosed(
    exitDate: LocalDate,
    exitPrice: Double,
    closedAt: LocalDateTime = LocalDateTime.now(),
  ): ScannerTrade =
    copy(
      status = TradeStatus.CLOSED,
      exitPrice = exitPrice,
      exitDate = exitDate,
      realizedPnl = computeRealizedPnl(exitPrice),
      closedAt = closedAt,
    )

  fun withNotes(notes: String?): ScannerTrade = copy(notes = notes)
}
