package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Metadata captured at backtest-store time. Lives outside `BacktestReport` because the
 * strategy names + date range come from the request, not the engine result. Persisted
 * as scalar columns on `backtest_reports` so the listing endpoint can render without
 * deserialising the 2-3 MB report blob.
 */
data class BacktestReportMetadata(
  val entryStrategyName: String,
  val exitStrategyName: String,
  val startDate: LocalDate,
  val endDate: LocalDate,
)

/**
 * Listing-relevant scalars extracted from `BacktestReport` at write time. Same rationale
 * as `BacktestReportMetadata`: keep the listing endpoint cheap.
 */
data class BacktestReportSummary(
  val totalTrades: Int,
  val edge: Double,
  val cagr: Double?,
  val maxDrawdownPct: Double?,
  val sharpeRatio: Double?,
)

/**
 * Wire shape returned by `GET /api/backtest/reports`. Never carries the heavy `report`
 * blob — that's reachable via existing single-id endpoints (`/trades`, `/missed-trades`,
 * `/api/monte-carlo/simulate`).
 */
data class BacktestReportListItem(
  val backtestId: UUID,
  val createdAt: LocalDateTime,
  val metadata: BacktestReportMetadata,
  val summary: BacktestReportSummary,
)
