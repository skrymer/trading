package com.skrymer.udgaard.backtesting.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.BacktestReportMetadata
import com.skrymer.udgaard.backtesting.model.BacktestReportSummary
import com.skrymer.udgaard.backtesting.repository.BacktestReportJooqRepository
import org.jooq.JSONB
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Stores `BacktestReport` as JSONB in `backtest_reports`; scalar summary fields are
 * extracted to columns so the listing endpoint reads without parsing the blob.
 *
 * Retention is "day or two max" — cleanup is manual via `BacktestReportController`,
 * not a scheduled job.
 */
@Component
class BacktestResultStore(
  private val repository: BacktestReportJooqRepository,
  private val objectMapper: ObjectMapper,
) {
  fun store(report: BacktestReport, metadata: BacktestReportMetadata): String {
    val id = UUID.randomUUID()
    val summary = BacktestReportSummary(
      totalTrades = report.totalTrades,
      edge = report.edge,
      cagr = report.cagr,
      maxDrawdownPct = report.positionSizingResult?.maxDrawdownPct,
      sharpeRatio = report.riskMetrics?.sharpeRatio,
    )
    repository.save(id, metadata, summary, JSONB.valueOf(objectMapper.writeValueAsString(report)))
    return id.toString()
  }

  // Malformed input returns null (not a thrown IllegalArgumentException) so the controller
  // `?: notFound()` paths handle it as a cache miss instead of leaking a 500.
  fun get(backtestId: String): BacktestReport? {
    val uuid = runCatching { UUID.fromString(backtestId) }.getOrNull() ?: return null
    return repository.findById(uuid)?.let { objectMapper.readValue(it.data(), BacktestReport::class.java) }
  }
}
