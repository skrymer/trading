package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate

class StrategySignalServiceTest {
  private lateinit var service: StrategySignalService
  private lateinit var conditionRegistry: ConditionRegistry
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var sectorBreadthRepository: SectorBreadthRepository
  private lateinit var marketBreadthRepository: MarketBreadthRepository
  private lateinit var strategyRegistry: StrategyRegistry

  @BeforeEach
  fun setUp() {
    conditionRegistry = mock(ConditionRegistry::class.java)
    stockRepository = mock(StockJooqRepository::class.java)
    sectorBreadthRepository = mock(SectorBreadthRepository::class.java)
    marketBreadthRepository = mock(MarketBreadthRepository::class.java)
    strategyRegistry = mock(StrategyRegistry::class.java)
    whenever(sectorBreadthRepository.findAllAsMap()).thenReturn(emptyMap())
    whenever(marketBreadthRepository.findAllAsMap()).thenReturn(emptyMap())
    whenever(stockRepository.findBySymbol("SPY")).thenReturn(null)

    service =
      StrategySignalService(
        strategyRegistry = strategyRegistry,
        conditionRegistry = conditionRegistry,
        marketBreadthRepository = marketBreadthRepository,
        sectorBreadthRepository = sectorBreadthRepository,
        stockRepository = stockRepository,
      )
  }

  @Test
  fun `evaluateExitConditions returns matching post-entry quotes when condition fires`() {
    // Given: stock with 4 quotes; condition that fires when closePrice >= 105
    val stock = stockWithCloses(LocalDate.of(2026, 1, 5), listOf(100.0, 102.0, 106.0, 110.0))
    val firesAtCloseGte105 = exitConditionWhere { _, _, q -> q.closePrice >= 105.0 }
    whenever(conditionRegistry.buildExitCondition(any())).thenReturn(firesAtCloseGte105)

    // When: evaluate from the first quote (entryDate = 2026-01-05, close=100)
    val result = service.evaluateExitConditions(stock, listOf(ConditionConfig("stopLoss")), "OR", LocalDate.of(2026, 1, 5))

    // Then: 3 post-entry quotes evaluated, 2 fired (106, 110)
    assertEquals(3, result.totalQuotes)
    assertEquals(2, result.matchingQuotes)
    assertEquals(LocalDate.of(2026, 1, 5), result.entryDate)
    assertTrue(result.quotesWithConditions.all { it.allConditionsMet })
  }

  @Test
  fun `evaluateExitConditions evaluates strictly after entryDate, never on the entry day`() {
    // Given: stock with 4 quotes; condition that fires on every bar
    val stock = stockWithCloses(LocalDate.of(2026, 1, 5), listOf(100.0, 102.0, 106.0, 110.0))
    val alwaysFires = exitConditionWhere { _, _, _ -> true }
    whenever(conditionRegistry.buildExitCondition(any())).thenReturn(alwaysFires)

    // When
    val result = service.evaluateExitConditions(stock, listOf(ConditionConfig("any")), "OR", LocalDate.of(2026, 1, 5))

    // Then: entry quote at 2026-01-05 is NOT evaluated; first matched is the next trading day
    assertEquals(3, result.totalQuotes)
    assertEquals(LocalDate.of(2026, 1, 6), result.quotesWithConditions.first().date)
    assertTrue(result.quotesWithConditions.none { it.date == LocalDate.of(2026, 1, 5) })
  }

  @Test
  fun `evaluateExitConditions OR operator matches when any condition fires`() {
    // Given: two conditions — one fires only on the third post-entry quote, one never fires
    val stock = stockWithCloses(LocalDate.of(2026, 1, 5), listOf(100.0, 102.0, 104.0, 110.0))
    val firesAtClose110 = exitConditionWhere { _, _, q -> q.closePrice >= 110.0 }
    val neverFires = exitConditionWhere { _, _, _ -> false }
    whenever(conditionRegistry.buildExitCondition(any()))
      .thenReturn(firesAtClose110)
      .thenReturn(neverFires)

    // When
    val result =
      service.evaluateExitConditions(
        stock,
        listOf(ConditionConfig("stopLoss"), ConditionConfig("emaCross")),
        "OR",
        LocalDate.of(2026, 1, 5),
      )

    // Then: only the close=110 quote matches; the other two fail OR (both conditions false)
    assertEquals(1, result.matchingQuotes)
    assertEquals(LocalDate.of(2026, 1, 8), result.quotesWithConditions.single().date)
  }

  @Test
  fun `evaluateExitConditions throws when entryDate has no matching quote`() {
    // Given: a stock whose history doesn't include the requested entry date
    val stock = stockWithCloses(LocalDate.of(2026, 1, 5), listOf(100.0, 102.0))
    whenever(conditionRegistry.buildExitCondition(any())).thenReturn(exitConditionWhere { _, _, _ -> true })

    // When / Then: defense-in-depth for direct API hits past the modal guard
    val ex =
      assertThrows(IllegalArgumentException::class.java) {
        service.evaluateExitConditions(
          stock,
          listOf(ConditionConfig("stopLoss")),
          "OR",
          LocalDate.of(2026, 12, 31),
        )
      }
    assertTrue(ex.message!!.contains("2026-12-31"), "expected error to mention the offending date, got: ${ex.message}")
  }

  @Test
  fun `evaluateExitConditions threads BacktestContext to context-aware conditions`() {
    // Given: a condition whose evaluator inspects the context object (mirrors breadth-only conditions
    // like MarketAndSectorDowntrendExit). Verifies the service threads context through even when no
    // condition needs the entry quote.
    val stock = stockWithCloses(LocalDate.of(2026, 1, 5), listOf(100.0, 102.0, 104.0))
    val seenContexts = mutableListOf<BacktestContext>()
    val contextRecorder =
      object : ExitCondition {
        override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean = false

        override fun shouldExit(
          stock: Stock,
          entryQuote: StockQuote?,
          quote: StockQuote,
          context: BacktestContext,
        ): Boolean {
          seenContexts.add(context)
          return false
        }

        override fun evaluateWithDetails(
          stock: Stock,
          entryQuote: StockQuote?,
          quote: StockQuote,
          context: BacktestContext,
        ): ConditionEvaluationResult {
          seenContexts.add(context)
          return ConditionEvaluationResult(conditionType = "marketAndSectorDowntrend", description = "test", passed = false)
        }

        override fun exitReason(): String = "n/a"

        override fun description(): String = "test"

        override fun getMetadata(): ConditionMetadata =
          ConditionMetadata(
            type = "marketAndSectorDowntrend",
            displayName = "x",
            description = "x",
            parameters = emptyList(),
            category = "x",
          )

        override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this
      }
    whenever(conditionRegistry.buildExitCondition(any())).thenReturn(contextRecorder)

    // When
    val result =
      service.evaluateExitConditions(
        stock,
        listOf(ConditionConfig("marketAndSectorDowntrend")),
        "OR",
        LocalDate.of(2026, 1, 5),
      )

    // Then: every post-entry quote received a non-null BacktestContext
    assertEquals(2, result.totalQuotes)
    assertEquals(2, seenContexts.size)
  }

  @Test
  fun `evaluateStrategies reports entrySignal on every bar where conditions match`() {
    // Given: a stock with 5 daily bars where the strategy matches on bars 2 and 3 (a 2-day
    // sustained breakout) and conditions stop matching on bar 4. Pre-fix, the simulation
    // state machine would suppress bar 3 (already "in a hypothetical position" from bar 2).
    // The fix decouples entrySignal reporting from simulation state.
    val start = LocalDate.of(2026, 4, 1)
    val stock = stockWithCloses(start, listOf(100.0, 110.0, 112.0, 109.0, 108.0))
    val sustainedEntry = entryStrategyMatching { quote -> quote.closePrice in 110.0..112.0 }
    val neverExits = exitStrategyMatching { _ -> false }
    whenever(strategyRegistry.createEntryStrategy("SustainStrat")).thenReturn(sustainedEntry)
    whenever(strategyRegistry.createExitStrategy("NeverExit")).thenReturn(neverExits)

    // When
    val result = service.evaluateStrategies(stock, "SustainStrat", "NeverExit")

    // Then: both bars 2 (Apr 2) and 3 (Apr 3) report entrySignal=true — the sustain is visible
    val signals = result!!.quotesWithSignals.associate { it.quote.date to it.entrySignal }
    assertEquals(false, signals[start])
    assertEquals(true, signals[start.plusDays(1)])
    assertEquals(true, signals[start.plusDays(2)], "bar 3 must report entrySignal even though bar 2 already fired")
    assertEquals(false, signals[start.plusDays(3)])
    assertEquals(false, signals[start.plusDays(4)])
  }

  @Test
  fun `evaluateStrategies cooldown still suppresses entrySignal after an exit fires`() {
    // Given: entry matches whenever closePrice in 110-114; the exit fires when price reaches
    // 120 (which also breaks the entry condition's upper bound on the same bar). On the
    // bars after the exit, the entry condition recovers — but cooldown should suppress the
    // observation regardless of what conditions now say.
    //
    //   day 1: close 100 (no entry)
    //   day 2: close 110 (entry: in window) -> entrySignal=true, pin entryQuote
    //   day 3: close 112 (entry still in window) -> entrySignal=true (sustain)
    //   day 4: close 120 (exit fires; entry condition false on this bar) -> exit, cooldownRemaining=3
    //   day 5: close 113 (entry would match, but cooldown active) -> entrySignal=false
    //   day 6: close 113 (still in cooldown) -> entrySignal=false
    //   day 7: close 113 (cooldown expires this bar) -> entrySignal=true again
    val start = LocalDate.of(2026, 4, 1)
    val stock = stockWithCloses(start, listOf(100.0, 110.0, 112.0, 120.0, 113.0, 113.0, 113.0))
    val entry = entryStrategyMatching { quote -> quote.closePrice in 110.0..114.0 }
    val exit = exitStrategyMatching { quote -> quote.closePrice >= 120.0 }
    whenever(strategyRegistry.createEntryStrategy("StratA")).thenReturn(entry)
    whenever(strategyRegistry.createExitStrategy("ExitA")).thenReturn(exit)

    // When
    val result = service.evaluateStrategies(stock, "StratA", "ExitA", cooldownDays = 2)

    // Then
    val signals = result!!.quotesWithSignals.associate { it.quote.date to it.entrySignal }
    assertEquals(true, signals[start.plusDays(1)], "day 2 — first entry pinned")
    assertEquals(true, signals[start.plusDays(2)], "day 3 — sustain (conditions still match)")
    assertEquals(false, signals[start.plusDays(3)], "day 4 — exit bar, entry condition is false here too")
    assertEquals(false, signals[start.plusDays(4)], "day 5 — cooldown suppresses despite matching conditions")
    assertEquals(false, signals[start.plusDays(5)], "day 6 — still in cooldown")
    assertEquals(true, signals[start.plusDays(6)], "day 7 — cooldown expired, entry reports again")
  }

  // ── helpers ──

  private fun entryStrategyMatching(predicate: (StockQuote) -> Boolean): EntryStrategy =
    object : EntryStrategy {
      override fun description(): String = "test entry"

      override fun test(stock: Stock, quote: StockQuote, context: BacktestContext): Boolean = predicate(quote)
    }

  private fun exitStrategyMatching(predicate: (StockQuote) -> Boolean): ExitStrategy =
    object : ExitStrategy {
      override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean = predicate(quote)

      override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): String? = "test exit"

      override fun description(): String = "test exit"
    }

  private fun stockWithCloses(start: LocalDate, closes: List<Double>): Stock {
    val quotes =
      closes.mapIndexed { i, close ->
        StockQuote(symbol = "TEST", date = start.plusDays(i.toLong()), closePrice = close, atr = 1.0)
      }
    return Stock(symbol = "TEST", quotes = quotes)
  }

  private fun exitConditionWhere(predicate: (Stock, StockQuote?, StockQuote) -> Boolean): ExitCondition =
    object : ExitCondition {
      override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean =
        predicate(stock, entryQuote, quote)

      override fun exitReason(): String = "test exit"

      override fun description(): String = "test condition"

      override fun getMetadata(): ConditionMetadata =
        ConditionMetadata(type = "test", displayName = "test", description = "test", parameters = emptyList(), category = "test")

      override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this
    }
}
