package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Cluster-robust (CR0) standard error of the mean of [value] across [items], clustered by the
 * calendar month of [month]. Trades entered in the same month share the same tape and are not
 * independent draws, so an iid SE would be several times too confident.
 * `V = (1/N^2) * sum_g (sum_{i in g}(x_i - mean))^2`; SE = sqrt(V). Empty input -> 0.0.
 *
 * Shared by the per-regime decomposition and the per-sector stats so both report the same
 * entry-month-clustered SE of an edge — the same trades grouped on a different key.
 */
fun <T> entryMonthClusteredStandardError(
  items: List<T>,
  month: (T) -> LocalDate,
  value: (T) -> Double,
): Double {
  if (items.isEmpty()) return 0.0
  val mean = items.map(value).average()
  val clusterDeviationSums =
    items
      .groupBy { month(it).withDayOfMonth(1) }
      .values
      .map { cluster -> cluster.sumOf { value(it) - mean } }
  val variance = clusterDeviationSums.sumOf { it * it } / (items.size.toDouble() * items.size)
  return sqrt(variance)
}
