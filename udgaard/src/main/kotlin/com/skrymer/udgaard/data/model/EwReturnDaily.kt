package com.skrymer.udgaard.data.model

import java.time.LocalDate

/**
 * One trading day's equal-weight cross-section of trailing 20-bar simple returns over the
 * point-in-time universe. The mean is the equal-weight (one-vote-per-name) leg of the leadership
 * gap; the cross-sectional stdev and contributing count drive the regime-read trust check
 * (standard error of the mean = [crossSectionalStdev] / sqrt([contributingN])).
 */
data class EwReturnDaily(
  val quoteDate: LocalDate,
  val meanReturn: Double,
  val crossSectionalStdev: Double,
  val contributingN: Int,
)
