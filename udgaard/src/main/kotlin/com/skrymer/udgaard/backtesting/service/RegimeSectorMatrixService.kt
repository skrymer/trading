package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.sqrt

/**
 * One (regime, sector) cell of the market-scoped matrix: the sector ETF's daily returns on days
 * carrying that published label, annualized. Descriptive market structure — no strategy data.
 * [spellCount] is the honest sample size: a cell's days are not independent draws — they come in
 * contiguous regime spells, and the error bars are clustered on those.
 */
data class RegimeSectorCell(
  val label: RegimeLabel,
  val sector: String,
  val dayCount: Int,
  val spellCount: Int,
  val annualizedReturn: Double,
  val annualizedStandardError: Double?,
)

/** The strategy-blind regime x sector return matrix. */
data class RegimeSectorMatrix(
  val cells: List<RegimeSectorCell>,
)

/**
 * Buckets each sector ETF's daily returns by the day's published regime label — "which sectors
 * lead in each regime" as pure market structure. A day's return belongs to that day's label; days
 * with no defensible label contribute to no cell.
 */
@Service
class RegimeSectorMatrixService(
  private val stockRepository: StockJooqRepository,
  private val sectorBreadthRepository: SectorBreadthRepository,
  private val regimeReadoutService: RegimeReadoutService,
) {
  /**
   * The matrix over `[after, before]`: the read-out series plus every sector ETF's closes (the
   * sector universe is the sector-breadth map's keys), each loaded from the requested window.
   */
  fun loadMatrix(
    after: LocalDate,
    before: LocalDate,
  ): RegimeSectorMatrix {
    val readout = regimeReadoutService.loadReadoutSeries(after, before)
    val sectorCloseByDate =
      sectorBreadthRepository.getLatestSectorCounts().keys.associateWith { sector ->
        stockRepository
          .findBySymbol(sector, quotesAfter = after)
          ?.quotes
          ?.filter { !it.date.isAfter(before) }
          ?.associate { it.date to it.closePrice }
          ?: emptyMap()
      }
    return matrix(readout, sectorCloseByDate)
  }

  fun matrix(
    readout: Map<LocalDate, RegimeReadoutDaily>,
    sectorCloseByDate: Map<String, Map<LocalDate, Double>>,
  ): RegimeSectorMatrix {
    val spellIdByDate = spellIds(readout)
    val cells =
      sectorCloseByDate.flatMap { (sector, closeByDate) ->
        dailyReturns(closeByDate)
          .mapNotNull { (date, dailyReturn) ->
            readout[date]?.publishedLabel?.let { label -> LabeledReturn(label, spellIdByDate.getValue(date), dailyReturn) }
          }.groupBy { it.label }
          .map { (label, labeledReturns) -> cellOf(label, sector, labeledReturns) }
      }
    return RegimeSectorMatrix(cells = cells.sortedWith(compareBy({ it.label.ordinal }, { it.sector })))
  }

  private data class LabeledReturn(
    val label: RegimeLabel,
    val spellId: Int,
    val dailyReturn: Double,
  )

  private fun cellOf(
    label: RegimeLabel,
    sector: String,
    labeledReturns: List<LabeledReturn>,
  ): RegimeSectorCell {
    val mean = labeledReturns.map { it.dailyReturn }.average()
    val clusterDeviationSums =
      labeledReturns
        .groupBy { it.spellId }
        .values
        .map { spell -> spell.sumOf { it.dailyReturn - mean } }
    val variance = clusterDeviationSums.sumOf { it * it } / (labeledReturns.size.toDouble() * labeledReturns.size)
    return RegimeSectorCell(
      label = label,
      sector = sector,
      dayCount = labeledReturns.size,
      spellCount = clusterDeviationSums.size,
      annualizedReturn = mean * TRADING_DAYS_PER_YEAR,
      annualizedStandardError = sqrt(variance) * TRADING_DAYS_PER_YEAR,
    )
  }

  /**
   * Contiguous published-label runs in the read-out series, numbered in date order. A label change
   * (or an unlabeled day) starts a new spell — the unit the error bars cluster on.
   */
  private fun spellIds(readout: Map<LocalDate, RegimeReadoutDaily>): Map<LocalDate, Int> {
    val sortedDates = readout.keys.sorted()
    val ids = LinkedHashMap<LocalDate, Int>(sortedDates.size)
    var spellId = 0
    var previousLabel: RegimeLabel? = null
    for (date in sortedDates) {
      val label = readout.getValue(date).publishedLabel
      if (label != previousLabel) spellId++
      ids[date] = spellId
      previousLabel = label
    }
    return ids
  }

  private fun dailyReturns(closeByDate: Map<LocalDate, Double>): Map<LocalDate, Double> {
    val sortedDates = closeByDate.keys.sorted()
    return sortedDates
      .zipWithNext()
      .mapNotNull { (previous, current) ->
        val priorClose = closeByDate.getValue(previous)
        if (priorClose > 0.0) current to closeByDate.getValue(current) / priorClose - 1.0 else null
      }.toMap()
  }

  companion object {
    private const val TRADING_DAYS_PER_YEAR = 252.0
  }
}
