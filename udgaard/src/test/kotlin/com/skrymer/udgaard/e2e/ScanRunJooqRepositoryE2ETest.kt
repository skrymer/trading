package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.scanner.model.MatchedSymbol
import com.skrymer.udgaard.scanner.model.ScanRun
import com.skrymer.udgaard.scanner.repository.ScanRunJooqRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration test for [ScanRunJooqRepository] — persists daily entry-candidate scan runs
 * for the cohort-divergence diagnostic. See `strategy_exploration/VCP_TRADING_PLAN.md` §8 and
 * the ADR for the durability rationale.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScanRunJooqRepositoryE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @Autowired
  private lateinit var repository: ScanRunJooqRepository

  @BeforeEach
  fun cleanTable() {
    dsl.deleteFrom(DSL.table("scan_runs")).execute()
  }

  @Test
  fun `save round-trips the scan run with all matched symbols and scalar metadata intact`() {
    // Given: a scan run with three matched symbols across two sectors
    val scanRun = ScanRun(
      id = null,
      signalDate = LocalDate.of(2026, 5, 18),
      scanTimestamp = LocalDateTime.of(2026, 5, 19, 6, 30),
      entryStrategyName = "TestEntryStrategy",
      exitStrategyName = "TestExitStrategy",
      rankerName = "SectorEdgeWithTightness",
      totalStocksScanned = 3005,
      matchedSymbols = listOf(
        MatchedSymbol(symbol = "AAPL", sectorSymbol = "XLK", closePrice = 175.50, atr = 3.50, rankScore = 109.95),
        MatchedSymbol(symbol = "NFLX", sectorSymbol = "XLC", closePrice = 425.00, atr = 8.20, rankScore = 109.92),
        MatchedSymbol(symbol = "XOM", sectorSymbol = "XLE", closePrice = 110.00, atr = 2.10, rankScore = 80.05),
      ),
    )

    // When
    val saved = repository.save(scanRun)
    val reloaded = repository.findById(saved.id!!)

    // Then: signal_date, scan_timestamp, strategy/ranker names, total_stocks_scanned and the
    // full matched_symbols payload all survive the JSONB round-trip
    assertNotNull(reloaded)
    assertEquals(LocalDate.of(2026, 5, 18), reloaded.signalDate)
    assertEquals(LocalDateTime.of(2026, 5, 19, 6, 30), reloaded.scanTimestamp)
    assertEquals("TestEntryStrategy", reloaded.entryStrategyName)
    assertEquals("TestExitStrategy", reloaded.exitStrategyName)
    assertEquals("SectorEdgeWithTightness", reloaded.rankerName)
    assertEquals(3005, reloaded.totalStocksScanned)
    assertEquals(3, reloaded.matchedSymbols.size)
    // Spot-check the first row preserves all five lean ScanResult fields
    val first = reloaded.matchedSymbols[0]
    assertEquals("AAPL", first.symbol)
    assertEquals("XLK", first.sectorSymbol)
    assertEquals(175.50, first.closePrice)
    assertEquals(3.50, first.atr)
    assertEquals(109.95, first.rankScore)
  }

  @Test
  fun `re-saving with the same signalDate plus config upserts the matched symbols and bumps the scan_timestamp`() {
    // Given: the trader scans, then re-scans the same trading day (e.g. after a mid-session
    // data refresh). Two scan calls with the same (signal_date, strategy, ranker) tuple — only
    // the latest cohort and timestamp should be retained; no duplicate row.
    val signalDate = LocalDate.of(2026, 5, 18)
    val firstScan = ScanRun(
      id = null,
      signalDate = signalDate,
      scanTimestamp = LocalDateTime.of(2026, 5, 19, 6, 0),
      entryStrategyName = "TestEntryStrategy",
      exitStrategyName = "TestExitStrategy",
      rankerName = "SectorEdgeWithTightness",
      totalStocksScanned = 3000,
      matchedSymbols = listOf(
        MatchedSymbol(symbol = "AAPL", sectorSymbol = "XLK", closePrice = 175.0, atr = 3.5, rankScore = 109.95),
      ),
    )
    val rescannedLater = firstScan.copy(
      id = null,
      scanTimestamp = LocalDateTime.of(2026, 5, 19, 7, 30),
      totalStocksScanned = 3010,
      matchedSymbols = listOf(
        MatchedSymbol(symbol = "AAPL", sectorSymbol = "XLK", closePrice = 175.0, atr = 3.5, rankScore = 109.95),
        MatchedSymbol(symbol = "NFLX", sectorSymbol = "XLC", closePrice = 425.0, atr = 8.2, rankScore = 109.92),
      ),
    )

    // When
    val firstSaved = repository.save(firstScan)
    val secondSaved = repository.save(rescannedLater)

    // Then: same row id (upsert, not insert); second save's payload + timestamp won
    assertEquals(firstSaved.id, secondSaved.id, "upsert must return the same id, not insert a new row")
    val reloaded = repository.findById(secondSaved.id!!)!!
    assertEquals(LocalDateTime.of(2026, 5, 19, 7, 30), reloaded.scanTimestamp, "scan_timestamp should advance to the latest scan")
    assertEquals(3010, reloaded.totalStocksScanned)
    assertEquals(2, reloaded.matchedSymbols.size, "matched_symbols should be replaced with the latest cohort")
    assertEquals(setOf("AAPL", "NFLX"), reloaded.matchedSymbols.map { it.symbol }.toSet())

    // Also verify: the table has exactly one row for this (signal_date, config) tuple
    val rowCount = dsl
      .selectCount()
      .from(DSL.table("scan_runs"))
      .where(DSL.field("signal_date").eq(signalDate))
      .fetchOne(0, Int::class.java)
    assertEquals(1, rowCount)
  }

  @Test
  fun `findByWindow returns only production-config runs inside the date range, in ascending signalDate order`() {
    // Given: four scan runs — two production-config, one outside the window, one with a
    // different ranker (exploratory). Only the production-config runs inside the window
    // should be returned.
    val inWindowOld = scanRun(signalDate = LocalDate.of(2026, 5, 1), ranker = "SectorEdgeWithTightness")
    val inWindowNew = scanRun(signalDate = LocalDate.of(2026, 5, 18), ranker = "SectorEdgeWithTightness")
    val outsideWindow = scanRun(signalDate = LocalDate.of(2026, 4, 1), ranker = "SectorEdgeWithTightness")
    val exploratory = scanRun(signalDate = LocalDate.of(2026, 5, 10), ranker = "SectorEdge")
    listOf(inWindowOld, inWindowNew, outsideWindow, exploratory).forEach { repository.save(it) }

    // When
    val results = repository.findByWindow(
      startInclusive = LocalDate.of(2026, 4, 30),
      endInclusive = LocalDate.of(2026, 5, 19),
      entryStrategyName = "TestEntryStrategy",
      exitStrategyName = "TestExitStrategy",
      rankerName = "SectorEdgeWithTightness",
    )

    // Then: two runs returned, in ascending signalDate order
    assertEquals(2, results.size, "expected the 2 in-window production-config runs only")
    assertEquals(LocalDate.of(2026, 5, 1), results[0].signalDate)
    assertEquals(LocalDate.of(2026, 5, 18), results[1].signalDate)
  }

  private fun scanRun(signalDate: LocalDate, ranker: String) = ScanRun(
    id = null,
    signalDate = signalDate,
    scanTimestamp = signalDate.atStartOfDay().plusHours(18),
    entryStrategyName = "TestEntryStrategy",
    exitStrategyName = "TestExitStrategy",
    rankerName = ranker,
    totalStocksScanned = 3000,
    matchedSymbols = listOf(
      MatchedSymbol(symbol = "AAPL", sectorSymbol = "XLK", closePrice = 175.0, atr = 3.5, rankScore = 109.95),
    ),
  )
}
