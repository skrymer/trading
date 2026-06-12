package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate

/**
 * One trading day's regime read. [rawLabel] is the instantaneous decision-table result;
 * [publishedLabel] is what an operator consulting the read-out live would have seen (raw debounced
 * by the dwell). Both are null on a day with no defensible read (fail-closed unlabeled).
 */
data class RegimeReadoutDaily(
  val quoteDate: LocalDate,
  val rawLabel: RegimeLabel?,
  val publishedLabel: RegimeLabel?,
)
