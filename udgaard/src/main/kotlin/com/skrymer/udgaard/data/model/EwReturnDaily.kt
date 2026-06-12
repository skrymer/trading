package com.skrymer.udgaard.data.model

import java.time.LocalDate

/**
 * One trading day's equal-weight cross-section of trailing 20-bar simple returns over the
 * point-in-time universe. [meanReturn] is the leadership-gap deploy gate's equal-weight leg;
 * [medianReturn] is the regime read-out's gap leg — the median is immune to micro-cap
 * moonshots/bad prints (one outlier name moves a 5,000-name median by nothing) and stays
 * stationary across the year-over-year tail-quality drift that makes the mean unusable there.
 * [iqr] (p75 − p25) is the robust dispersion read backing the advisory trust flag.
 */
data class EwReturnDaily(
  val quoteDate: LocalDate,
  val meanReturn: Double,
  val crossSectionalStdev: Double,
  val contributingN: Int,
  val medianReturn: Double = 0.0,
  val iqr: Double = 0.0,
)
