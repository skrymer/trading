package com.skrymer.udgaard.scanner.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.skrymer.udgaard.jooq.tables.references.SCAN_RUNS
import com.skrymer.udgaard.scanner.model.ScanRun
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ScanRunJooqRepository(
  private val dsl: DSLContext,
  private val objectMapper: ObjectMapper,
) {
  /**
   * Upsert by `(signalDate, entryStrategyName, exitStrategyName, rankerName)`. Multiple
   * scans on the same trading day with the same config overwrite — the latest captured
   * cohort is the one the trader operationally saw.
   */
  fun save(scanRun: ScanRun): ScanRun {
    val matchedJson = JSONB.valueOf(objectMapper.writeValueAsString(scanRun.matchedSymbols))
    val newId = dsl
      .insertInto(SCAN_RUNS)
      .set(SCAN_RUNS.SIGNAL_DATE, scanRun.signalDate)
      .set(SCAN_RUNS.SCAN_TIMESTAMP, scanRun.scanTimestamp)
      .set(SCAN_RUNS.ENTRY_STRATEGY_NAME, scanRun.entryStrategyName)
      .set(SCAN_RUNS.EXIT_STRATEGY_NAME, scanRun.exitStrategyName)
      .set(SCAN_RUNS.RANKER_NAME, scanRun.rankerName)
      .set(SCAN_RUNS.TOTAL_STOCKS_SCANNED, scanRun.totalStocksScanned)
      .set(SCAN_RUNS.MATCH_COUNT, scanRun.matchCount)
      .set(SCAN_RUNS.MATCHED_SYMBOLS, matchedJson)
      .onConflict(
        SCAN_RUNS.SIGNAL_DATE,
        SCAN_RUNS.ENTRY_STRATEGY_NAME,
        SCAN_RUNS.EXIT_STRATEGY_NAME,
        SCAN_RUNS.RANKER_NAME,
      ).doUpdate()
      .set(SCAN_RUNS.SCAN_TIMESTAMP, scanRun.scanTimestamp)
      .set(SCAN_RUNS.TOTAL_STOCKS_SCANNED, scanRun.totalStocksScanned)
      .set(SCAN_RUNS.MATCH_COUNT, scanRun.matchCount)
      .set(SCAN_RUNS.MATCHED_SYMBOLS, matchedJson)
      .returningResult(SCAN_RUNS.ID)
      .fetchOne()
      ?.value1()
      ?: throw IllegalStateException("Failed to upsert scan_run")
    return scanRun.copy(id = newId)
  }

  fun findById(id: Long): ScanRun? =
    dsl
      .selectFrom(SCAN_RUNS)
      .where(SCAN_RUNS.ID.eq(id))
      .fetchOne()
      ?.let(::toDomain)

  /**
   * All scan runs with `signalDate` in `[startInclusive, endInclusive]`, filtered to the
   * production config tuple, ordered ascending by signal_date for window aggregation.
   */
  fun findByWindow(
    startInclusive: LocalDate,
    endInclusive: LocalDate,
    entryStrategyName: String,
    exitStrategyName: String,
    rankerName: String,
  ): List<ScanRun> =
    dsl
      .selectFrom(SCAN_RUNS)
      .where(SCAN_RUNS.SIGNAL_DATE.between(startInclusive, endInclusive))
      .and(SCAN_RUNS.ENTRY_STRATEGY_NAME.eq(entryStrategyName))
      .and(SCAN_RUNS.EXIT_STRATEGY_NAME.eq(exitStrategyName))
      .and(SCAN_RUNS.RANKER_NAME.eq(rankerName))
      .orderBy(SCAN_RUNS.SIGNAL_DATE.asc())
      .fetch()
      .map(::toDomain)

  private fun toDomain(record: Record): ScanRun =
    ScanRun(
      id = record.get(SCAN_RUNS.ID),
      signalDate = record.get(SCAN_RUNS.SIGNAL_DATE)!!,
      scanTimestamp = record.get(SCAN_RUNS.SCAN_TIMESTAMP)!!,
      entryStrategyName = record.get(SCAN_RUNS.ENTRY_STRATEGY_NAME)!!,
      exitStrategyName = record.get(SCAN_RUNS.EXIT_STRATEGY_NAME)!!,
      rankerName = record.get(SCAN_RUNS.RANKER_NAME)!!,
      totalStocksScanned = record.get(SCAN_RUNS.TOTAL_STOCKS_SCANNED)!!,
      matchedSymbols = objectMapper.readValue(
        record.get(SCAN_RUNS.MATCHED_SYMBOLS)!!.data(),
      ),
    )
}
