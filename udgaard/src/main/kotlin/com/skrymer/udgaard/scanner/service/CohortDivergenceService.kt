package com.skrymer.udgaard.scanner.service

import com.skrymer.udgaard.scanner.model.CohortWindow
import com.skrymer.udgaard.scanner.repository.ScanRunJooqRepository
import com.skrymer.udgaard.scanner.repository.ScannerTradeJooqRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

/**
 * Thin orchestrator for the cohort-divergence diagnostic. Loads the rolling window's scan
 * runs and trades into a [CohortWindow] aggregate and asks it for the report fields — no
 * metric logic lives here. Per ADR 0001.
 */
@Service
class CohortDivergenceService(
  private val scanRunRepository: ScanRunJooqRepository,
  private val scannerTradeRepository: ScannerTradeJooqRepository,
  private val clock: Clock,
) {
  fun compute(config: DivergenceConfig): CohortDivergenceReport {
    require(config.windowDays in MIN_WINDOW_DAYS..MAX_WINDOW_DAYS) {
      "windowDays must be between $MIN_WINDOW_DAYS and $MAX_WINDOW_DAYS (got ${config.windowDays})"
    }
    val today = LocalDate.now(clock)
    val windowStart = today.minusDays(config.windowDays.toLong() - 1)

    val scanRuns = scanRunRepository.findByWindow(
      windowStart,
      today,
      config.entryStrategy,
      config.exitStrategy,
      config.ranker,
    )
    val trades = scannerTradeRepository.findBySignalDateBetween(windowStart, today)

    val window = CohortWindow(
      scanRuns = scanRuns,
      tradesEntered = trades,
      windowStart = windowStart,
      windowEnd = today,
    )

    return CohortDivergenceReport(
      config = config,
      windowStart = windowStart,
      windowEnd = today,
      scanRunsInWindow = scanRuns.size,
      today = TodayMetrics(
        scanDate = today,
        signalsEmitted = window.signalsEmittedOn(today),
        signalsTaken = window.signalsTakenOn(today),
      ),
      rolling = RollingMetrics(
        jaccard = window.rollingJaccard(),
        scannerRichDayCount = window.scannerRichDayCount(),
      ),
      alerts = Alerts(
        executionDrift = window.executionDriftAlert(),
        traderFiltering = window.traderFilteringAlert(),
      ),
    )
  }

  companion object {
    private const val MIN_WINDOW_DAYS = 1
    private const val MAX_WINDOW_DAYS = 365
  }
}

data class DivergenceConfig(
  val entryStrategy: String,
  val exitStrategy: String,
  val ranker: String = "SectorEdgeWithTightness",
  val windowDays: Int = 20,
)

data class CohortDivergenceReport(
  val config: DivergenceConfig,
  val windowStart: LocalDate,
  val windowEnd: LocalDate,
  val scanRunsInWindow: Int,
  val today: TodayMetrics,
  val rolling: RollingMetrics,
  val alerts: Alerts,
)

data class TodayMetrics(
  val scanDate: LocalDate,
  val signalsEmitted: Int,
  val signalsTaken: Int,
)

data class RollingMetrics(
  val jaccard: Double,
  val scannerRichDayCount: Int,
)

data class Alerts(
  val executionDrift: Boolean,
  val traderFiltering: Boolean,
)
