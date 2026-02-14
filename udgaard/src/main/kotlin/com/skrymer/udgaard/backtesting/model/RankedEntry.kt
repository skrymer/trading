package com.skrymer.udgaard.backtesting.model

/**
 * Represents a potential entry with its ranking score.
 *
 * Used for sorting potential entries when position limits are enforced.
 * Higher scores indicate better trading candidates.
 *
 * @property entry - the potential entry being ranked
 * @property score - the ranking score assigned by the StockRanker
 */
data class RankedEntry(
  val entry: PotentialEntry,
  val score: Double,
)
