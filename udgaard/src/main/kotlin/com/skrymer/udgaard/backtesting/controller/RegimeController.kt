package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.backtesting.model.RegimeReadoutVersion
import com.skrymer.udgaard.backtesting.service.BacktestResultStore
import com.skrymer.udgaard.backtesting.service.RegimeDecomposition
import com.skrymer.udgaard.backtesting.service.RegimeDecompositionService
import com.skrymer.udgaard.backtesting.service.RegimeReadoutService
import com.skrymer.udgaard.backtesting.service.RegimeSectorMatrix
import com.skrymer.udgaard.backtesting.service.RegimeSectorMatrixService
import com.skrymer.udgaard.backtesting.service.RegimeTradeSample
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate

/**
 * REST API for the pre-registered 5-label regime read-out (ADR 0023).
 *
 * Endpoints:
 * - GET /api/regime/readout?after=&before=&version= — the daily label series over a window; version
 *   defaults to the frozen-and-live V2 (ADR 0024), V3 serves the provisional trend-efficiency
 *   candidate (ADR 0028) for validation only — transitional, removed once v3 is resolved
 * - GET /api/regime/current — the latest available read as of the NY trading day
 * - GET /api/regime/decomposition/{backtestId} — a stored backtest's trades bucketed by regime at entry
 * - GET /api/regime/sector-matrix?after=&before= — the strategy-blind regime x sector return matrix
 */
@RestController
@RequestMapping("/api/regime")
class RegimeController(
  private val regimeReadoutService: RegimeReadoutService,
  private val regimeDecompositionService: RegimeDecompositionService,
  private val regimeSectorMatrixService: RegimeSectorMatrixService,
  private val backtestResultStore: BacktestResultStore,
  private val clock: Clock,
) {
  @GetMapping("/readout")
  fun getReadout(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) after: LocalDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) before: LocalDate,
    @RequestParam(defaultValue = "V2") version: String = "V2",
  ): ResponseEntity<List<RegimeReadoutDaily>> {
    if (after.isAfter(before)) return ResponseEntity.badRequest().build()
    // Parse case-insensitively so a manual ?version=v3 binds (the default enum converter is strict).
    val readoutVersion =
      runCatching { RegimeReadoutVersion.valueOf(version.trim().uppercase()) }
        .getOrElse { return ResponseEntity.badRequest().build() }
    val series =
      when (readoutVersion) {
        RegimeReadoutVersion.V2 -> regimeReadoutService.loadReadoutSeries(after, before)
        RegimeReadoutVersion.V3 -> regimeReadoutService.loadReadoutSeriesV3(after, before)
      }
    return ResponseEntity.ok(series.values.sortedBy { it.quoteDate })
  }

  @GetMapping("/decomposition/{backtestId}")
  fun getDecomposition(
    @PathVariable backtestId: String,
  ): ResponseEntity<RegimeDecomposition> {
    val report = backtestResultStore.get(backtestId) ?: return ResponseEntity.notFound().build()
    val samples =
      report.trades.map { RegimeTradeSample(entryDate = it.entryQuote.date, returnPct = it.profitPercentage, sector = it.sector) }
    if (samples.isEmpty()) {
      return ResponseEntity.ok(RegimeDecomposition(rows = emptyList(), totalTrades = 0, rawPublishedDivergenceCount = 0))
    }
    val readout = regimeReadoutService.loadReadoutSeries(samples.minOf { it.entryDate }, samples.maxOf { it.entryDate })
    return ResponseEntity.ok(regimeDecompositionService.decompose(samples, readout))
  }

  @GetMapping("/sector-matrix")
  fun getSectorMatrix(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) after: LocalDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) before: LocalDate,
  ): ResponseEntity<RegimeSectorMatrix> {
    if (after.isAfter(before)) return ResponseEntity.badRequest().build()
    return ResponseEntity.ok(regimeSectorMatrixService.loadMatrix(after, before))
  }

  @GetMapping("/current")
  fun getCurrent(): ResponseEntity<RegimeReadoutDaily> {
    val today = LocalDate.now(clock)
    val series = regimeReadoutService.loadReadoutSeries(today.minusDays(CURRENT_LOOKBACK_DAYS), today)
    val latest = series.values.maxByOrNull { it.quoteDate } ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(latest)
  }

  companion object {
    /** Wide enough to bridge a long weekend, holidays, and a lagging data refresh. */
    private const val CURRENT_LOOKBACK_DAYS = 30L
  }
}
