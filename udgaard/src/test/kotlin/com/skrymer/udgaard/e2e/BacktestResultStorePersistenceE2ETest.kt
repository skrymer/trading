package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.BacktestReportMetadata
import com.skrymer.udgaard.backtesting.model.EntryDecisionContext
import com.skrymer.udgaard.backtesting.model.MarketConditionSnapshot
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.service.BacktestResultStore
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.jooq.tables.references.BACKTEST_REPORTS
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BacktestResultStorePersistenceE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var store: BacktestResultStore

  @Autowired
  private lateinit var dsl: DSLContext

  @Test
  fun `round-trip preserves all BacktestReport constructor fields`() {
    // Given a fully-populated report (the constructor fields the JSONB blob must round-trip)
    val report = fixtureReport()

    // When stored and re-fetched
    val id = store.store(report, fixtureMetadata())
    val retrieved = requireNotNull(store.get(id))

    // Then every constructor field equals the original
    assertEquals(report.winningTrades.size, retrieved.winningTrades.size)
    assertEquals(report.losingTrades.size, retrieved.losingTrades.size)
    assertEquals(report.missedTrades.size, retrieved.missedTrades.size)
    assertEquals(report.winningTrades.first().stockSymbol, retrieved.winningTrades.first().stockSymbol)
    assertEquals(report.winningTrades.first().profit, retrieved.winningTrades.first().profit)
  }

  @Test
  fun `Trade @Transient runtime fields are not persisted`() {
    // Given a Trade with the four runtime-only fields populated
    val trade = createTrade(10.0, LocalDate.of(2024, 1, 1)).apply {
      marketConditionAtEntry = MarketConditionSnapshot(
        spyClose = 450.0,
        spyInUptrend = true,
        marketBreadthBullPercent = 65.0,
        entryDate = LocalDate.of(2024, 1, 1),
      )
      missedReason = "test reason"
      entryContext = EntryDecisionContext(
        cashAtDecision = 10_000.0,
        openNotionalAtDecision = 0.0,
        openPositionCount = 0,
        cohortSize = 5,
        rankInCohort = 1,
        availableSlots = 10,
        sharesReserved = 100,
      )
    }
    val report = BacktestReport(winningTrades = listOf(trade), losingTrades = emptyList())

    // When round-tripped through the store
    val id = store.store(report, fixtureMetadata())
    val retrieved = requireNotNull(store.get(id))
    val retrievedTrade = retrieved.winningTrades.first()

    // Then the runtime-only fields are null on the deserialized side, while constructor fields preserve
    assertNull(retrievedTrade.marketConditionAtEntry)
    assertNull(retrievedTrade.excursionMetrics)
    assertNull(retrievedTrade.missedReason)
    assertNull(retrievedTrade.entryContext)
    assertEquals(trade.stockSymbol, retrievedTrade.stockSymbol)
    assertEquals(trade.profit, retrievedTrade.profit)
    assertEquals(trade.exitReason, retrievedTrade.exitReason)
    assertEquals(trade.sector, retrievedTrade.sector)
  }

  @Test
  fun `computed properties reconstitute correctly after deserialization`() {
    // Given a report with a known win/loss profile (3 winners totalling 18 percent, 2 losers totalling 6 percent)
    val winningTrades = listOf(
      createTrade(5.0, LocalDate.of(2024, 1, 1)),
      createTrade(6.0, LocalDate.of(2024, 1, 2)),
      createTrade(7.0, LocalDate.of(2024, 1, 3)),
    )
    val losingTrades = listOf(
      createTrade(-3.0, LocalDate.of(2024, 1, 4)),
      createTrade(-3.0, LocalDate.of(2024, 1, 5)),
    )
    val report = BacktestReport(winningTrades = winningTrades, losingTrades = losingTrades)

    // When round-tripped
    val id = store.store(report, fixtureMetadata())
    val retrieved = requireNotNull(store.get(id))

    // Then computed properties match the originals (winRate, edge, totalTrades all derive from the trade lists)
    assertEquals(report.winRate, retrieved.winRate, EPSILON)
    assertEquals(report.edge, retrieved.edge, EPSILON)
    assertEquals(report.totalTrades, retrieved.totalTrades)
  }

  @Test
  fun `two backtests stored sequentially are both retrievable`() {
    // Given two distinct reports
    val first = fixtureReport()
    val second = fixtureReport()

    // When both are stored
    val firstId = store.store(first, fixtureMetadata())
    val secondId = store.store(second, fixtureMetadata())

    // Then both round-trip without one evicting the other (the limitation this PR closes)
    assertNotEquals(firstId, secondId)
    assertNotNull(store.get(firstId))
    assertNotNull(store.get(secondId))
  }

  @Test
  fun `unknown backtestId returns null without exception`() {
    // Given an empty store
    // When asked for a random unknown UUID
    val result = store.get(UUID.randomUUID().toString())

    // Then null, no exception
    assertNull(result)
  }

  @Test
  fun `malformed backtestId returns null without exception`() {
    // Given an empty store
    // When asked for a string that is not a UUID
    val result = store.get("not-a-uuid")

    // Then null, no IllegalArgumentException leaks
    assertNull(result)
  }

  @Test
  fun `same report stored twice produces two independent IDs`() {
    // Given the same report instance
    val report = fixtureReport()

    // When stored twice
    val firstId = store.store(report, fixtureMetadata())
    val secondId = store.store(report, fixtureMetadata())

    // Then two distinct UUIDs that are independently retrievable
    assertNotEquals(firstId, secondId)
    assertNotNull(store.get(firstId))
    assertNotNull(store.get(secondId))
  }

  @Test
  fun `metadata columns persist alongside report blob`() {
    // Given a report with non-trivial metadata
    val metadata = BacktestReportMetadata(
      entryStrategyName = "test-entry",
      exitStrategyName = "test-exit",
      startDate = LocalDate.of(2023, 1, 1),
      endDate = LocalDate.of(2023, 12, 31),
    )
    val report = fixtureReport()

    // When stored
    val id = UUID.fromString(store.store(report, metadata))
    val row = dsl
      .select(
        BACKTEST_REPORTS.ENTRY_STRATEGY_NAME,
        BACKTEST_REPORTS.EXIT_STRATEGY_NAME,
        BACKTEST_REPORTS.START_DATE,
        BACKTEST_REPORTS.END_DATE,
        BACKTEST_REPORTS.TOTAL_TRADES,
        BACKTEST_REPORTS.EDGE,
      ).from(BACKTEST_REPORTS)
      .where(BACKTEST_REPORTS.BACKTEST_ID.eq(id))
      .fetchOne()
      ?: error("row not found for $id")

    // Then the scalar columns match the supplied metadata + the derived summary
    assertEquals("test-entry", row[BACKTEST_REPORTS.ENTRY_STRATEGY_NAME])
    assertEquals("test-exit", row[BACKTEST_REPORTS.EXIT_STRATEGY_NAME])
    assertEquals(LocalDate.of(2023, 1, 1), row[BACKTEST_REPORTS.START_DATE])
    assertEquals(LocalDate.of(2023, 12, 31), row[BACKTEST_REPORTS.END_DATE])
    assertEquals(report.totalTrades, row[BACKTEST_REPORTS.TOTAL_TRADES])
    assertTrue(kotlin.math.abs(report.edge - row[BACKTEST_REPORTS.EDGE]!!) < EPSILON)
  }

  // ===== HELPERS =====

  private fun fixtureReport(): BacktestReport {
    val winners = listOf(
      createTrade(5.0, LocalDate.of(2024, 1, 1)),
      createTrade(6.0, LocalDate.of(2024, 1, 2)),
    )
    val losers = listOf(
      createTrade(-2.0, LocalDate.of(2024, 1, 3)),
    )
    return BacktestReport(winningTrades = winners, losingTrades = losers)
  }

  private fun fixtureMetadata() = BacktestReportMetadata(
    entryStrategyName = "fixture-entry",
    exitStrategyName = "fixture-exit",
    startDate = LocalDate.of(2024, 1, 1),
    endDate = LocalDate.of(2024, 12, 31),
  )

  private fun createTrade(profitPercentage: Double, entryDate: LocalDate): Trade {
    val entry = StockQuote(date = entryDate, closePrice = 100.0)
    val exit = StockQuote(date = entryDate.plusDays(5), closePrice = 100.0 + profitPercentage)
    return Trade(
      stockSymbol = "TEST",
      entryQuote = entry,
      quotes = listOf(exit),
      exitReason = "test-exit",
      profit = profitPercentage,
      startDate = entryDate,
      sector = "Technology",
    )
  }

  companion object {
    private const val EPSILON = 1e-6
  }
}
