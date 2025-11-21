package com.skrymer.udgaard.model.montecarlo

import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.Trade
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class TradeShufflingTechniqueTest {

    private val technique = TradeShufflingTechnique()

    @Test
    fun `should generate correct number of scenarios`() {
        val backtest = createSimpleBacktest()
        val iterations = 100

        val scenarios = technique.generateScenarios(backtest, iterations)

        assertEquals(iterations, scenarios.size, "Should generate correct number of scenarios")
    }

    @Test
    fun `should preserve total return across all scenarios`() {
        val backtest = createSimpleBacktest()
        val originalReturn = backtest.trades.sumOf { it.profitPercentage }
        val iterations = 50

        val scenarios = technique.generateScenarios(backtest, iterations)

        scenarios.forEach { scenario ->
            assertEquals(
                originalReturn,
                scenario.totalReturnPercentage,
                0.01,
                "Each scenario should have same total return"
            )
        }
    }

    @Test
    fun `should preserve number of trades`() {
        val backtest = createSimpleBacktest()
        val originalTradeCount = backtest.trades.size
        val iterations = 50

        val scenarios = technique.generateScenarios(backtest, iterations)

        scenarios.forEach { scenario ->
            assertEquals(
                originalTradeCount,
                scenario.trades.size,
                "Each scenario should have same number of trades"
            )
        }
    }

    @Test
    fun `should randomize trade order`() {
        val backtest = createSimpleBacktest()
        val iterations = 100
        val seed = 12345L

        val scenarios = technique.generateScenarios(backtest, iterations, seed)

        // Check that at least some scenarios have different orderings
        val firstTrades = scenarios.map { it.trades.first().profit }
        val uniqueFirstTrades = firstTrades.distinct()

        assertTrue(
            uniqueFirstTrades.size > 1,
            "Trade order should be randomized across scenarios"
        )
    }

    @Test
    fun `should produce reproducible results with same seed`() {
        val backtest = createSimpleBacktest()
        val seed = 54321L

        val scenarios1 = technique.generateScenarios(backtest, 10, seed)
        val scenarios2 = technique.generateScenarios(backtest, 10, seed)

        scenarios1.zip(scenarios2).forEach { (s1, s2) ->
            assertEquals(
                s1.totalReturnPercentage,
                s2.totalReturnPercentage,
                0.01,
                "Same seed should produce same scenarios"
            )
        }
    }

    @Test
    fun `should calculate equity curve correctly`() {
        val backtest = createSimpleBacktest()

        val scenarios = technique.generateScenarios(backtest, 1)
        val scenario = scenarios.first()

        assertEquals(
            scenario.trades.size,
            scenario.equityCurve.size,
            "Equity curve should have one point per trade"
        )

        var expectedCumulative = 0.0
        scenario.trades.zip(scenario.equityCurve).forEach { (trade, point) ->
            expectedCumulative += trade.profitPercentage
            assertEquals(
                expectedCumulative,
                point.cumulativeReturnPercentage,
                0.01,
                "Equity curve should be cumulative"
            )
        }
    }

    @Test
    fun `should calculate win rate correctly`() {
        val backtest = createBacktestWithMixedTrades()

        val scenarios = technique.generateScenarios(backtest, 10)

        scenarios.forEach { scenario ->
            val expectedWinRate = scenario.trades.count { it.profitPercentage > 0 }.toDouble() / scenario.trades.size
            assertEquals(
                expectedWinRate,
                scenario.winRate,
                0.01,
                "Win rate should be calculated correctly"
            )
        }
    }

    @Test
    fun `should handle empty backtest`() {
        val backtest = BacktestReport(emptyList(), emptyList())

        val scenarios = technique.generateScenarios(backtest, 10)

        assertTrue(scenarios.isEmpty(), "Should return empty list for empty backtest")
    }

    @Test
    fun `should have correct name and description`() {
        assertEquals("Trade Shuffling", technique.name())
        assertTrue(technique.description().contains("Randomly reorders trades"))
    }

    // Helper methods

    private fun createSimpleBacktest(): BacktestReport {
        val winningTrades = listOf(
            createTrade(10.0, LocalDate.of(2024, 1, 1)),
            createTrade(5.0, LocalDate.of(2024, 1, 2)),
            createTrade(8.0, LocalDate.of(2024, 1, 3))
        )
        val losingTrades = listOf(
            createTrade(-3.0, LocalDate.of(2024, 1, 4)),
            createTrade(-2.0, LocalDate.of(2024, 1, 5))
        )

        return BacktestReport(winningTrades, losingTrades)
    }

    private fun createBacktestWithMixedTrades(): BacktestReport {
        val winningTrades = listOf(
            createTrade(10.0, LocalDate.of(2024, 1, 1)),
            createTrade(5.0, LocalDate.of(2024, 1, 2))
        )
        val losingTrades = listOf(
            createTrade(-3.0, LocalDate.of(2024, 1, 3)),
            createTrade(-2.0, LocalDate.of(2024, 1, 4))
        )

        return BacktestReport(winningTrades, losingTrades)
    }

    private fun createTrade(profitPercentage: Double, entryDate: LocalDate): Trade {
        val entryQuote = StockQuote(
            date = entryDate,
            closePrice = 100.0
        )
        val exitQuote = StockQuote(
            date = entryDate.plusDays(5),
            closePrice = 100.0 + profitPercentage
        )

        val profit = profitPercentage // Simplified for testing

        return Trade(
            stockSymbol = "TEST",
            entryQuote = entryQuote,
            quotes = listOf(exitQuote),
            exitReason = "Test exit",
            profit = profit,
            startDate = entryDate,
            sector = "Technology"
        )
    }
}
