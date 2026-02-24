package com.skrymer.udgaard.data.service

import java.time.LocalDate

/**
 * Session-scoped context for stock refresh operations.
 *
 * This context is created once per refresh session and reused across all stocks
 * in that session.
 */
data class RefreshContext(
  /**
   * Minimum date for data filtering. Only data from this date onwards is included.
   * Defaults to 2016-01-01 to provide 10 years of history for backtesting.
   */
  val minDate: LocalDate = LocalDate.of(2016, 1, 1),
)
