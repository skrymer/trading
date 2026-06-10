package com.skrymer.udgaard.data.model

import java.time.LocalDate

/**
 * Point-in-time quarterly financial-statement line items for one fiscal period (ADR 0019 L1), the raw
 * data the gross-profitability quality signal is computed from. Mirrors the Midgaard `Fundamental` it
 * is ingested from; values are nullable because a section may be absent or a line item omitted.
 *
 * [filingDate] is the visibility key: a backtest may only see this record on a trading date on or after
 * [filingDate], never on [fiscalDateEnding] (the fiscal-period end becomes public 1-3 months later).
 * Gating on [filingDate] removes the first-order look-ahead leak — the earnings `reportedDate` pattern
 * (CONTEXT *Point-in-time fundamentals*).
 */
data class Fundamental(
  val symbol: String = "",
  val fiscalDateEnding: LocalDate = LocalDate.now(),
  val filingDate: LocalDate? = null,
  val grossProfit: Double? = null,
  val costOfRevenue: Double? = null,
  val totalRevenue: Double? = null,
  val operatingIncome: Double? = null,
  val netIncome: Double? = null,
  val totalAssets: Double? = null,
  val totalStockholderEquity: Double? = null,
  val totalCurrentAssets: Double? = null,
  val totalCurrentLiabilities: Double? = null,
) {
  /**
   * Whether this record is public knowledge on [date] — true only once its [filingDate] is set and on
   * or before [date]. A null [filingDate] is never visible (we can't prove it was public).
   */
  fun isVisibleAsOf(date: LocalDate): Boolean = filingDate != null && !filingDate.isAfter(date)
}
