package com.skrymer.udgaard.data.model

import java.time.LocalDate

/**
 * One stock split for a symbol. [ratio] is new-shares-per-old (numerator / denominator of EODHD's
 * `"4.000000/1.000000"`): 4.0 for a 4:1 forward split, 0.5 for a 1:2 reverse. [exDate] is the first
 * session the price trades on the post-split basis.
 *
 * The cumulative split factor `k(t)` — the product of [ratio] over every split with [exDate] strictly
 * after `t` — converts the stored split-adjusted (current-basis) share count back onto the basis of a
 * past raw close, so `(rawClose(t) / k(t)) × sharesOutstanding` is split-invariant (ADR 0027).
 */
data class Split(
  val symbol: String = "",
  val exDate: LocalDate = LocalDate.now(),
  val ratio: Double = 1.0,
)
