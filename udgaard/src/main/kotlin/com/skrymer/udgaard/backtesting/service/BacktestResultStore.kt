package com.skrymer.udgaard.backtesting.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.BacktestReportMetadata
import com.skrymer.udgaard.backtesting.model.BacktestReportSummary
import com.skrymer.udgaard.backtesting.repository.BacktestReportJooqRepository
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Stores `BacktestReport` as a gzip-compressed blob in `backtest_reports`; scalar summary
 * fields are extracted to columns so the listing endpoint reads without decompressing.
 *
 * The report is gzipped because a high-candidate strategy records an `EntryDecisionContext`
 * per missed entry, and the resulting `missedTrades` list can serialize to hundreds of MB of
 * JSON — past Postgres's ~256 MB jsonb cap. Compression (~10x) keeps it well under any limit.
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
    repository.save(id, metadata, summary, compress(report))
    return id.toString()
  }

  // Malformed input returns null (not a thrown IllegalArgumentException) so the controller
  // `?: notFound()` paths handle it as a cache miss instead of leaking a 500.
  fun get(backtestId: String): BacktestReport? {
    val uuid = runCatching { UUID.fromString(backtestId) }.getOrNull() ?: return null
    return repository.findById(uuid)?.let { decompress(it) }
  }

  // Stream the report straight through gzip — no intermediate full-size JSON String, which
  // for a hundreds-of-MB report would be a needless heap spike.
  private fun compress(report: BacktestReport): ByteArray {
    val buffer = ByteArrayOutputStream()
    GZIPOutputStream(buffer).use { objectMapper.writeValue(it, report) }
    return buffer.toByteArray()
  }

  private fun decompress(bytes: ByteArray): BacktestReport =
    GZIPInputStream(ByteArrayInputStream(bytes)).use {
      objectMapper.readValue(it, BacktestReport::class.java)
    }
}
