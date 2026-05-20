package com.skrymer.udgaard.data.model

import java.time.LocalDate

/**
 * A third-party buy/sell call from ovtlyr.com for a symbol on a date. Sourced from
 * Midgaard, which stores ovtlyr signals as reference data. See the "Ovtlyr signal"
 * glossary entry.
 */
data class OvtlyrSignal(
  val symbol: String,
  val signalDate: LocalDate,
  val signal: OvtlyrSignalType,
)

enum class OvtlyrSignalType {
  BUY,
  SELL,
}
