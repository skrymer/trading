package com.skrymer.udgaard.model.montecarlo

import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.Trade
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class BootstrapResamplingTechniqueTest {

    private val technique = BootstrapResamplingTechnique()

    @Test
    fun `should generate correct number of scenarios`() {
        val backtest = createSimpleBacktest()
        val iterations = 100

        val scenarios = technique.generateScenarios(backtest, iterations)

        assertEquals(iterations, scenarios.size, "Should generate correct number of scenarios")
    }

    @Test
    fun `should preserve number of trades through resampling`() {
        val backtest = createSimpleBacktest()
        val originalTradeCount = backtest.trades.size
        val iterations = 50

        val scenarios = technique.generateScenarios(backtest, iterations)

        scenarios.forEach { scenario ->
            assertEquals(
                originalTradeCount,
                scenario.trades.size,
                "Each scenario should have same number of trades as original"
            )
        }
    }

    @Test
    fun `should allow trades to appear multiple times`() {
        val backtest = createBacktestWithDistinctProfits()
        val iterations = 100

        val scenarios = technique.generateScenarios(backtest, iterations)

        // Check that at least one scenario has duplicate trades
        val hasScenarioWithDuplicates = scenarios.any { scenario ->
            val profits = scenario.trades.map { it.profitPercentage }
            profits.size != profits.distinct().size
        }

        assertTrue(
            hasScenarioWithDuplicates,
            "Bootstrap should allow trades to appear multiple times"
        )
    }

    @Test
    fun `should produce different total returns across scenarios`() {
        val backtest = createBacktestWithDistinctProfits()
        val iterations = 100

        val scenarios = technique.generateScenarios(backtest, iterations)

        val totalReturns = scenarios.map { it.totalReturnPercentage }.distinct()

        assertTrue(
            totalReturns.size > 1,
            "Bootstrap should produce different total returns due to resampling"
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
        assertEquals("Bootstrap Resampling", technique.name())
        assertTrue(technique.description().contains("replacement"))
    }

    @Test
    fun `mean return should approximate original with many iterations`() {
        val backtest = createSimpleBacktest()
        val originalReturn = backtest.trades.sumOf { it.profitPercentage }
        val iterations = 1000

        val scenarios = technique.generateScenarios(backtest, iterations)

        val meanReturn = scenarios.map { it.totalReturnPercentage }.average()

        // Mean should be within 20% of original (bootstrap property)
        assertTrue(
            kotlin.math.abs(meanReturn - originalReturn) < originalReturn * 0.2,
            "Mean return should approximate original with many iterations"
        )
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

    private fun createBacktestWithDistinctProfits(): BacktestReport {
        val winningTrades = listOf(
            createTrade(10.0, LocalDate.of(2024, 1, 1)),
            createTrade(20.0, LocalDate.of(2024, 1, 2)),
            createTrade(30.0, LocalDate.of(2024, 1, 3))
        )
        val losingTrades = listOf(
            createTrade(-5.0, LocalDate.of(2024, 1, 4)),
            createTrade(-10.0, LocalDate.of(2024, 1, 5))
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
