package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.sqrt

/**
 * One closed trade reduced to what the regime decomposition consumes: when it entered, what it
 * returned (per-trade %, net of cost), and which sector it traded.
 */
data class RegimeTradeSample(
  val entryDate: LocalDate,
  val returnPct: Double,
  val sector: String,
)

/**
 * One regime's slice of a backtest's trades. [label] null is the unlabeled bucket (trades entered
 * on days with no defensible regime read). A bucket below the trade floor keeps its [tradeCount]
 * but carries no statistics ([insufficient] — "do not infer"): a thin bucket's numbers are
 * seductive and meaningless.
 */
data class RegimeDecompositionRow(
  val label: RegimeLabel?,
  val tradeCount: Int,
  val edge: Double?,
  val standardError: Double?,
  val insufficient: Boolean,
  val sectors: List<SectorCell>,
)

/**
 * One sector's slice of a regime bucket. The trade floor applies per cell — at 11 sectors x 5
 * regimes most cells are expected to be too thin to infer from, and must say so.
 */
data class SectorCell(
  val sector: String,
  val tradeCount: Int,
  val edge: Double?,
  val insufficient: Boolean,
)

/**
 * The per-regime decomposition of one backtest's trades — descriptive only, never a gate.
 * [rawPublishedDivergenceCount] counts trades entered on transition-boundary days (the raw label
 * disagreed with the published one mid-dwell) — where regime attribution is genuinely ambiguous.
 */
data class RegimeDecomposition(
  val rows: List<RegimeDecompositionRow>,
  val totalTrades: Int,
  val rawPublishedDivergenceCount: Int,
)

/**
 * Decomposes a backtest's closed trades by the regime label at each trade's entry date —
 * deployment-timing/sizing context for the operator, computed on already-made trades. Buckets on
 * the published (dwell-debounced) label: what an operator consulting the read-out live would have
 * seen when the trade was entered.
 */
@Service
class RegimeDecompositionService {
  fun decompose(
    samples: List<RegimeTradeSample>,
    readout: Map<LocalDate, RegimeReadoutDaily>,
    minTradesPerBucket: Int = MIN_TRADES_PER_BUCKET,
  ): RegimeDecomposition {
    val rows =
      samples
        .groupBy { readout[it.entryDate]?.publishedLabel }
        .map { (label, bucket) ->
          val insufficient = bucket.size < minTradesPerBucket
          RegimeDecompositionRow(
            label = label,
            tradeCount = bucket.size,
            edge = if (insufficient) null else bucket.map { it.returnPct }.average(),
            standardError = if (insufficient) null else clusteredStandardError(bucket),
            insufficient = insufficient,
            sectors = sectorCells(bucket, minTradesPerBucket),
          )
        }.sortedBy { it.label?.ordinal ?: RegimeLabel.entries.size }
    val divergent =
      samples.count { sample ->
        readout[sample.entryDate]?.let { it.rawLabel != it.publishedLabel } ?: false
      }
    return RegimeDecomposition(rows = rows, totalTrades = samples.size, rawPublishedDivergenceCount = divergent)
  }

  private fun sectorCells(
    bucket: List<RegimeTradeSample>,
    minTradesPerBucket: Int,
  ): List<SectorCell> =
    bucket
      .groupBy { it.sector }
      .map { (sector, cell) ->
        val insufficient = cell.size < minTradesPerBucket
        SectorCell(
          sector = sector,
          tradeCount = cell.size,
          edge = if (insufficient) null else cell.map { it.returnPct }.average(),
          insufficient = insufficient,
        )
      }.sortedByDescending { it.tradeCount }

  /**
   * Cluster-robust (CR0) standard error of the bucket's mean return, clustered by entry month —
   * trades entered in the same regime spell share the same tape and are not independent draws, so
   * an iid SE would be several times too confident. `V = (1/N^2) * sum_g (sum_{i in g}(x_i - mean))^2`.
   */
  private fun clusteredStandardError(bucket: List<RegimeTradeSample>): Double {
    val mean = bucket.map { it.returnPct }.average()
    val clusterDeviationSums =
      bucket
        .groupBy { it.entryDate.withDayOfMonth(1) }
        .values
        .map { cluster -> cluster.sumOf { it.returnPct - mean } }
    val variance = clusterDeviationSums.sumOf { it * it } / (bucket.size.toDouble() * bucket.size)
    return sqrt(variance)
  }

  companion object {
    /** The G8 rationale applied per bucket: below ~30 trades a slice's statistics are noise. */
    const val MIN_TRADES_PER_BUCKET = 30
  }
}
