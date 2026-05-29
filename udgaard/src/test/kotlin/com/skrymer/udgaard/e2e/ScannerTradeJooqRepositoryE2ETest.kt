package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.repository.ScannerTradeJooqRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Integration test for [ScannerTradeJooqRepository] persistence of `signalSnapshot` + `signalDate`.
 *
 * The snapshot is the immutable record of what the strategy evaluator saw on the signal bar at
 * the moment the trade was added. It must survive the JSONB round-trip with structure intact —
 * later UI rendering and audit reads depend on it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannerTradeJooqRepositoryE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var repository: ScannerTradeJooqRepository

  @Test
  fun `save with non-null signalSnapshot round-trips JSONB with structure intact`() {
    // Given a scanner trade carrying an EntrySignalDetails snapshot from a Vcp match
    val snapshot = EntrySignalDetails(
      strategyName = "TestEntryStrategy",
      strategyDescription = "Volatility Contraction Pattern",
      conditions = listOf(
        ConditionEvaluationResult(
          conditionType = "MarketUptrendCondition",
          description = "Market in uptrend",
          passed = true,
          actualValue = "27.7%",
          threshold = "> 19.3% (10 EMA)",
          message = null,
        ),
        ConditionEvaluationResult(
          conditionType = "PriceNearDonchianHighCondition",
          description = "Price near Donchian high (within 3.0%)",
          passed = false,
          actualValue = "3.36% below Donchian high",
          threshold = "≤3.0%",
          message = "Price 3.36% from Donchian high (too far)",
        ),
      ),
      allConditionsMet = false,
    )
    val trade = ScannerTrade(
      id = null,
      symbol = "TESTSNAP",
      sectorSymbol = "XLI",
      instrumentType = InstrumentType.STOCK,
      entryPrice = 50.0,
      entryDate = LocalDate.of(2026, 4, 2),
      quantity = 100,
      optionType = null,
      strikePrice = null,
      expirationDate = null,
      entryStrategyName = "TestEntryStrategy",
      exitStrategyName = "TestExitStrategy",
      notes = null,
      signalDate = LocalDate.of(2026, 4, 1),
      signalSnapshot = snapshot,
    )

    // When the trade is persisted and re-loaded
    val saved = repository.save(trade)
    val reloaded = repository.findById(saved.id!!)

    // Then signalDate and signalSnapshot survive the round-trip with all condition details intact
    assertNotNull(reloaded, "expected to find the saved trade")
    assertEquals(LocalDate.of(2026, 4, 1), reloaded.signalDate)
    val reloadedSnapshot = reloaded.signalSnapshot
    assertNotNull(reloadedSnapshot, "expected signalSnapshot to be persisted")
    assertEquals("TestEntryStrategy", reloadedSnapshot.strategyName)
    assertEquals(false, reloadedSnapshot.allConditionsMet)
    assertEquals(2, reloadedSnapshot.conditions.size)
    assertEquals("MarketUptrendCondition", reloadedSnapshot.conditions[0].conditionType)
    assertEquals(true, reloadedSnapshot.conditions[0].passed)
    assertEquals("27.7%", reloadedSnapshot.conditions[0].actualValue)
    assertEquals("PriceNearDonchianHighCondition", reloadedSnapshot.conditions[1].conditionType)
    assertEquals(false, reloadedSnapshot.conditions[1].passed)
    assertEquals("3.36% below Donchian high", reloadedSnapshot.conditions[1].actualValue)
    assertEquals("≤3.0%", reloadedSnapshot.conditions[1].threshold)
  }

  @Test
  fun `save with null signalSnapshot persists null and reads back null`() {
    // Given a scanner trade with no captured signal (legacy / manual-add path)
    val trade = ScannerTrade(
      id = null,
      symbol = "TESTSNAPNULL",
      sectorSymbol = "XLF",
      instrumentType = InstrumentType.STOCK,
      entryPrice = 25.0,
      entryDate = LocalDate.of(2026, 4, 2),
      quantity = 50,
      optionType = null,
      strikePrice = null,
      expirationDate = null,
      entryStrategyName = "TestEntryStrategy",
      exitStrategyName = "TestExitStrategy",
      notes = null,
      signalDate = null,
      signalSnapshot = null,
    )

    // When the trade is persisted and re-loaded
    val saved = repository.save(trade)
    val reloaded = repository.findById(saved.id!!)

    // Then both signalDate and signalSnapshot are null on the read back — preserving the
    // "we did not capture this" semantic that the ADR pins as informative-not-error
    assertNotNull(reloaded)
    assertNull(reloaded.signalDate)
    assertNull(reloaded.signalSnapshot)
  }
}
