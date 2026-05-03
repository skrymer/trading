package com.skrymer.udgaard.backtesting.repository

import com.skrymer.udgaard.backtesting.model.BacktestReportListItem
import com.skrymer.udgaard.backtesting.model.BacktestReportMetadata
import com.skrymer.udgaard.backtesting.model.BacktestReportSummary
import com.skrymer.udgaard.jooq.tables.references.BACKTEST_REPORTS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class BacktestReportJooqRepository(
  private val dsl: DSLContext
) {
  fun save(
    backtestId: UUID,
    metadata: BacktestReportMetadata,
    summary: BacktestReportSummary,
    report: JSONB,
  ) {
    dsl
      .insertInto(BACKTEST_REPORTS)
      .set(BACKTEST_REPORTS.BACKTEST_ID, backtestId)
      .set(BACKTEST_REPORTS.ENTRY_STRATEGY_NAME, metadata.entryStrategyName)
      .set(BACKTEST_REPORTS.EXIT_STRATEGY_NAME, metadata.exitStrategyName)
      .set(BACKTEST_REPORTS.START_DATE, metadata.startDate)
      .set(BACKTEST_REPORTS.END_DATE, metadata.endDate)
      .set(BACKTEST_REPORTS.TOTAL_TRADES, summary.totalTrades)
      .set(BACKTEST_REPORTS.EDGE, summary.edge)
      .set(BACKTEST_REPORTS.CAGR, summary.cagr)
      .set(BACKTEST_REPORTS.MAX_DRAWDOWN_PCT, summary.maxDrawdownPct)
      .set(BACKTEST_REPORTS.SHARPE_RATIO, summary.sharpeRatio)
      .set(BACKTEST_REPORTS.REPORT, report)
      .execute()
  }

  fun findById(backtestId: UUID): JSONB? =
    dsl
      .select(BACKTEST_REPORTS.REPORT)
      .from(BACKTEST_REPORTS)
      .where(BACKTEST_REPORTS.BACKTEST_ID.eq(backtestId))
      .fetchOne(BACKTEST_REPORTS.REPORT)

  fun listAll(): List<BacktestReportListItem> =
    dsl
      .select(
        BACKTEST_REPORTS.BACKTEST_ID,
        BACKTEST_REPORTS.CREATED_AT,
        BACKTEST_REPORTS.ENTRY_STRATEGY_NAME,
        BACKTEST_REPORTS.EXIT_STRATEGY_NAME,
        BACKTEST_REPORTS.START_DATE,
        BACKTEST_REPORTS.END_DATE,
        BACKTEST_REPORTS.TOTAL_TRADES,
        BACKTEST_REPORTS.EDGE,
        BACKTEST_REPORTS.CAGR,
        BACKTEST_REPORTS.MAX_DRAWDOWN_PCT,
        BACKTEST_REPORTS.SHARPE_RATIO,
      ).from(BACKTEST_REPORTS)
      .orderBy(BACKTEST_REPORTS.CREATED_AT.desc())
      .fetch { record ->
        BacktestReportListItem(
          backtestId = record[BACKTEST_REPORTS.BACKTEST_ID]!!,
          createdAt = record[BACKTEST_REPORTS.CREATED_AT]!!,
          metadata = BacktestReportMetadata(
            entryStrategyName = record[BACKTEST_REPORTS.ENTRY_STRATEGY_NAME]!!,
            exitStrategyName = record[BACKTEST_REPORTS.EXIT_STRATEGY_NAME]!!,
            startDate = record[BACKTEST_REPORTS.START_DATE]!!,
            endDate = record[BACKTEST_REPORTS.END_DATE]!!,
          ),
          summary = BacktestReportSummary(
            totalTrades = record[BACKTEST_REPORTS.TOTAL_TRADES]!!,
            edge = record[BACKTEST_REPORTS.EDGE]!!,
            cagr = record[BACKTEST_REPORTS.CAGR],
            maxDrawdownPct = record[BACKTEST_REPORTS.MAX_DRAWDOWN_PCT],
            sharpeRatio = record[BACKTEST_REPORTS.SHARPE_RATIO],
          ),
        )
      }

  fun deleteById(backtestId: UUID): Int =
    dsl
      .deleteFrom(BACKTEST_REPORTS)
      .where(BACKTEST_REPORTS.BACKTEST_ID.eq(backtestId))
      .execute()

  fun deleteByIds(backtestIds: List<UUID>): Int {
    if (backtestIds.isEmpty()) return 0
    return dsl
      .deleteFrom(BACKTEST_REPORTS)
      .where(BACKTEST_REPORTS.BACKTEST_ID.`in`(backtestIds))
      .execute()
  }
}
