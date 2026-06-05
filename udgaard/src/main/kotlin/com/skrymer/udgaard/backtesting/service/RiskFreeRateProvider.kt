package com.skrymer.udgaard.backtesting.service

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.TreeMap

/**
 * The single source of truth for the risk-free rate `rf_step(t)` (ADR 0016). Holds the gross
 * treasury-yield series (in percent, from Midgaard) and the SGOV expense haircut, and exposes the
 * one rate that is fed identically to (a) the idle-cash credit in the sizing spine and (b) the rf
 * subtracted in Sharpe/Sortino — the coherence that makes idle-cash crediting Sharpe-neutral.
 *
 * The expense is subtracted exactly once here (F4): the stored Midgaard series is gross.
 */
class RiskFreeRateProvider(
  grossYieldPctByDate: Map<LocalDate, Double>,
  private val expensePct: Double,
) {
  private val sortedGross = TreeMap(grossYieldPctByDate)

  /**
   * The net annual risk-free rate as a fraction (e.g. 0.04 for 4%) effective on [date]: the
   * most-recent gross yield published on-or-before [date] (no look-ahead — F3), minus the expense,
   * divided by 100, floored at 0. Returns 0.0 when no yield is known on-or-before [date]
   * (loud-fallback territory).
   *
   * The floor models SGOV's fee-waiver behavior in deep-ZIRP windows where the gross 3-month yield
   * dipped below the ~0.10% expense: idle cash never carries a negative rate (a real operator holds
   * plain cash at 0% rather than a vehicle with known negative net carry). Flooring here — the single
   * `rf_step(t)` source of truth — keeps the credit and the Sharpe/Sortino rf identical, so the
   * Sharpe-neutrality cancellation holds for the floored value. See ADR 0016 (quant-adjudicated).
   */
  fun netAnnualRate(date: LocalDate): Double {
    val grossPct = sortedGross.floorEntry(date)?.value ?: return 0.0
    return maxOf(0.0, (grossPct - expensePct) / 100.0)
  }

  /**
   * The risk-free return accrued over the step ending at [toDate] (the previous step ended at
   * [fromDate]): `netAnnualRate(toDate) · calendarDays / 360` (ACT/360, F-day-count). Calendar-day,
   * so cash earns over weekends (Fri→Mon = 3 days). This identical value is credited to idle cash
   * and subtracted in the Sharpe excess return, keeping the idle leg Sharpe-neutral.
   */
  fun stepRate(
    fromDate: LocalDate,
    toDate: LocalDate,
  ): Double {
    val calendarDays = ChronoUnit.DAYS.between(fromDate, toDate)
    return netAnnualRate(toDate) * calendarDays / DAY_COUNT_BASIS
  }

  companion object {
    private const val DAY_COUNT_BASIS = 360.0
  }
}
