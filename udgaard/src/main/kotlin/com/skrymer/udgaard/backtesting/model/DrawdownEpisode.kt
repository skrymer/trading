package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate

/**
 * One distinct drawdown excursion of the daily equity curve.
 * State machine (Magdon-Ismail rule): episode opens when DD breaches −0.5% from the running peak,
 * closes ONLY when equity makes a new all-time peak. Below-noise wiggles within an open episode
 * do NOT close it — preserves the deepest trough across noise recoveries.
 *
 * `recoveryDate` / `recoveryDays` / `totalDays` are null when the equity curve ends still underwater.
 * `recoveryDays` is measured from `troughDate` (NOT from `peakDate`); `totalDays` covers
 * peak-to-recovery.
 */
data class DrawdownEpisode(
  val peakDate: LocalDate,
  val troughDate: LocalDate,
  val recoveryDate: LocalDate?,
  val maxDrawdownPct: Double,
  val declineDays: Int,
  val recoveryDays: Int?,
  val totalDays: Int?,
)
