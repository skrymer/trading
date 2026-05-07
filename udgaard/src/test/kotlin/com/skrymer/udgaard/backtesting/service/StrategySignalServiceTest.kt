package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
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

  @BeforeEach
  fun setUp() {
    conditionRegistry = mock(ConditionRegistry::class.java)
    stockRepository = mock(StockJooqRepository::class.java)
    sectorBreadthRepository = mock(SectorBreadthRepository::class.java)
    marketBreadthRepository = mock(MarketBreadthRepository::class.java)
    whenever(sectorBreadthRepository.findAllAsMap()).thenReturn(emptyMap())
    whenever(marketBreadthRepository.findAllAsMap()).thenReturn(emptyMap())
    whenever(stockRepository.findBySymbol("SPY")).thenReturn(null)

    service =
      StrategySignalService(
        strategyRegistry = mock(StrategyRegistry::class.java),
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

  // ── helpers ──

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
