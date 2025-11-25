package com.skrymer.udgaard.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate
import kotlin.math.abs

class BacktestReportSectorStatsTest {

    @Test
    fun `should group trades by sector correctly`() {
        val techTrade1 = createTrade("AAPL", "Technology", 10.0, LocalDate.of(2024, 1, 1))
        val techTrade2 = createTrade("MSFT", "Technology", -5.0, LocalDate.of(2024, 1, 2))
        val healthTrade1 = createTrade("JNJ", "Healthcare", 8.0, LocalDate.of(2024, 1, 3))
        val healthTrade2 = createTrade("PFE", "Healthcare", 12.0, LocalDate.of(2024, 1, 4))

        val report = BacktestReport(
            winningTrades = listOf(techTrade1, healthTrade1, healthTrade2),
            losingTrades = listOf(techTrade2)
        )

        val sectorStats = report.sectorStats

        assertEquals(2, sectorStats.size, "Should have 2 sectors")

        val techStats = sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats, "Should have Technology sector stats")
        assertEquals(2, techStats!!.totalTrades, "Technology should have 2 trades")
        assertEquals(1, techStats.winningTrades, "Technology should have 1 winning trade")
        assertEquals(1, techStats.losingTrades, "Technology should have 1 losing trade")

        val healthStats = sectorStats.find { it.sector == "Healthcare" }
        assertNotNull(healthStats, "Should have Healthcare sector stats")
        assertEquals(2, healthStats!!.totalTrades, "Healthcare should have 2 trades")
        assertEquals(2, healthStats.winningTrades, "Healthcare should have 2 winning trades")
        assertEquals(0, healthStats.losingTrades, "Healthcare should have 0 losing trades")
    }

    @Test
    fun `should calculate sector win rate correctly`() {
        val techTrade1 = createTrade("AAPL", "Technology", 10.0, LocalDate.of(2024, 1, 1))
        val techTrade2 = createTrade("MSFT", "Technology", -5.0, LocalDate.of(2024, 1, 2))
        val techTrade3 = createTrade("GOOGL", "Technology", 8.0, LocalDate.of(2024, 1, 3))
        val techTrade4 = createTrade("NVDA", "Technology", -3.0, LocalDate.of(2024, 1, 4))

        val report = BacktestReport(
            winningTrades = listOf(techTrade1, techTrade3),
            losingTrades = listOf(techTrade2, techTrade4)
        )

        val techStats = report.sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats)
        assertEquals(0.5, techStats!!.winRate, 0.001, "Win rate should be 50% (2 wins out of 4 trades)")
    }

    @Test
    fun `should calculate sector edge correctly`() {
        // Technology: 2 wins (10%, 8%) and 2 losses (-5%, -3%)
        // Avg win: 9%, Avg loss: 4%, Win rate: 50%
        // Edge = (9% * 0.5) - (4% * 0.5) = 4.5% - 2% = 2.5%
        val techTrade1 = createTrade("AAPL", "Technology", 10.0, LocalDate.of(2024, 1, 1))
        val techTrade2 = createTrade("MSFT", "Technology", -5.0, LocalDate.of(2024, 1, 2))
        val techTrade3 = createTrade("GOOGL", "Technology", 8.0, LocalDate.of(2024, 1, 3))
        val techTrade4 = createTrade("NVDA", "Technology", -3.0, LocalDate.of(2024, 1, 4))

        val report = BacktestReport(
            winningTrades = listOf(techTrade1, techTrade3),
            losingTrades = listOf(techTrade2, techTrade4)
        )

        val techStats = report.sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats)

        val expectedAvgWin = (10.0 + 8.0) / 2.0  // 9.0%
        val expectedAvgLoss = abs((-5.0 + -3.0) / 2.0)  // 4.0%
        val expectedEdge = (expectedAvgWin * 0.5) - (expectedAvgLoss * 0.5)  // 2.5%

        assertEquals(expectedAvgWin, techStats!!.averageWinPercent, 0.001)
        assertEquals(expectedAvgLoss, techStats.averageLossPercent, 0.001)
        assertEquals(expectedEdge, techStats.edge, 0.001)
    }

    @Test
    fun `should calculate total profit percentage for sector`() {
        val techTrade1 = createTrade("AAPL", "Technology", 10.0, LocalDate.of(2024, 1, 1))
        val techTrade2 = createTrade("MSFT", "Technology", -5.0, LocalDate.of(2024, 1, 2))
        val techTrade3 = createTrade("GOOGL", "Technology", 8.0, LocalDate.of(2024, 1, 3))

        val report = BacktestReport(
            winningTrades = listOf(techTrade1, techTrade3),
            losingTrades = listOf(techTrade2)
        )

        val techStats = report.sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats)

        val expectedTotal = 10.0 + (-5.0) + 8.0  // 13.0%
        assertEquals(expectedTotal, techStats!!.totalProfitPercentage, 0.001)
    }

    @Test
    fun `should calculate max drawdown for sector`() {
        // Create trades with specific dates to test drawdown calculation
        // Cumulative: 10% -> 5% (drawdown 5%) -> 13% -> 10% (drawdown 3%)
        val trade1 = createTrade("AAPL", "Technology", 10.0, LocalDate.of(2024, 1, 1))
        val trade2 = createTrade("MSFT", "Technology", -5.0, LocalDate.of(2024, 1, 2))
        val trade3 = createTrade("GOOGL", "Technology", 8.0, LocalDate.of(2024, 1, 3))
        val trade4 = createTrade("NVDA", "Technology", -3.0, LocalDate.of(2024, 1, 4))

        val report = BacktestReport(
            winningTrades = listOf(trade1, trade3),
            losingTrades = listOf(trade2, trade4)
        )

        val techStats = report.sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats)

        // Peak at trade1: 10%, then drops to 5% (drawdown = 5%)
        // Peak at trade3: 13%, then drops to 10% (drawdown = 3%)
        // Max drawdown should be 5%
        assertEquals(5.0, techStats!!.maxDrawdown, 0.001, "Max drawdown should be 5%")
    }

    @Test
    fun `should handle sector with only winning trades`() {
        val healthTrade1 = createTrade("JNJ", "Healthcare", 10.0, LocalDate.of(2024, 1, 1))
        val healthTrade2 = createTrade("PFE", "Healthcare", 8.0, LocalDate.of(2024, 1, 2))

        val report = BacktestReport(
            winningTrades = listOf(healthTrade1, healthTrade2),
            losingTrades = emptyList()
        )

        val healthStats = report.sectorStats.find { it.sector == "Healthcare" }
        assertNotNull(healthStats)
        assertEquals(2, healthStats!!.totalTrades)
        assertEquals(2, healthStats.winningTrades)
        assertEquals(0, healthStats.losingTrades)
        assertEquals(1.0, healthStats.winRate, 0.001, "Win rate should be 100%")
        assertEquals(0.0, healthStats.averageLossPercent, 0.001, "Avg loss should be 0%")
        assertEquals(0.0, healthStats.maxDrawdown, 0.001, "Max drawdown should be 0% with only wins")
    }

    @Test
    fun `should handle sector with only losing trades`() {
        val energyTrade1 = createTrade("XOM", "Energy", -5.0, LocalDate.of(2024, 1, 1))
        val energyTrade2 = createTrade("CVX", "Energy", -3.0, LocalDate.of(2024, 1, 2))

        val report = BacktestReport(
            winningTrades = emptyList(),
            losingTrades = listOf(energyTrade1, energyTrade2)
        )

        val energyStats = report.sectorStats.find { it.sector == "Energy" }
        assertNotNull(energyStats)
        assertEquals(2, energyStats!!.totalTrades)
        assertEquals(0, energyStats.winningTrades)
        assertEquals(2, energyStats.losingTrades)
        assertEquals(0.0, energyStats.winRate, 0.001, "Win rate should be 0%")
        assertEquals(0.0, energyStats.averageWinPercent, 0.001, "Avg win should be 0%")
        assertTrue(energyStats.edge < 0, "Edge should be negative")
    }

    @Test
    fun `should return empty list when no trades exist`() {
        val report = BacktestReport(
            winningTrades = emptyList(),
            losingTrades = emptyList()
        )

        val sectorStats = report.sectorStats
        assertEquals(0, sectorStats.size, "Should return empty list when no trades exist")
    }

    @Test
    fun `should sort sectors by edge in descending order`() {
        val techTrade1 = createTrade("AAPL", "Technology", 20.0, LocalDate.of(2024, 1, 1))
        val techTrade2 = createTrade("MSFT", "Technology", 18.0, LocalDate.of(2024, 1, 2))

        val healthTrade1 = createTrade("JNJ", "Healthcare", 10.0, LocalDate.of(2024, 1, 3))
        val healthTrade2 = createTrade("PFE", "Healthcare", -5.0, LocalDate.of(2024, 1, 4))

        val energyTrade1 = createTrade("XOM", "Energy", 5.0, LocalDate.of(2024, 1, 5))
        val energyTrade2 = createTrade("CVX", "Energy", -8.0, LocalDate.of(2024, 1, 6))

        val report = BacktestReport(
            winningTrades = listOf(techTrade1, techTrade2, healthTrade1, energyTrade1),
            losingTrades = listOf(healthTrade2, energyTrade2)
        )

        val sectorStats = report.sectorStats

        assertEquals(3, sectorStats.size)
        // Technology has best edge (all wins), should be first
        assertEquals("Technology", sectorStats[0].sector)
        assertTrue(sectorStats[0].edge > sectorStats[1].edge, "Sectors should be sorted by edge descending")
        assertTrue(sectorStats[1].edge > sectorStats[2].edge, "Sectors should be sorted by edge descending")
    }

    @Test
    fun `should calculate complex drawdown scenario correctly`() {
        // Scenario: Peak at 20%, drop to 10% (drawdown 10%), recover to 15%, drop to 5% (drawdown 15% from peak 20%)
        // Then recover to 25% (new peak), drop to 20% (drawdown 5%)
        // Max drawdown should be 15%
        val trades = listOf(
            createTrade("STOCK1", "Technology", 20.0, LocalDate.of(2024, 1, 1)),  // Cumulative: 20% (peak)
            createTrade("STOCK2", "Technology", -10.0, LocalDate.of(2024, 1, 2)), // Cumulative: 10% (drawdown 10% from peak 20%)
            createTrade("STOCK3", "Technology", 5.0, LocalDate.of(2024, 1, 3)),   // Cumulative: 15%
            createTrade("STOCK4", "Technology", -10.0, LocalDate.of(2024, 1, 4)), // Cumulative: 5% (drawdown 15% from peak 20%)
            createTrade("STOCK5", "Technology", 20.0, LocalDate.of(2024, 1, 5)),  // Cumulative: 25% (new peak)
            createTrade("STOCK6", "Technology", -5.0, LocalDate.of(2024, 1, 6))   // Cumulative: 20% (drawdown 5% from peak 25%)
        )

        val report = BacktestReport(
            winningTrades = trades.filter { it.profit > 0 },
            losingTrades = trades.filter { it.profit <= 0 }
        )

        val techStats = report.sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats)
        assertEquals(15.0, techStats!!.maxDrawdown, 0.001, "Max drawdown should be 15%")
    }

    @Test
    fun `should include all trades in sector stats`() {
        val techTrade1 = createTrade("AAPL", "Technology", 10.0, LocalDate.of(2024, 1, 1))
        val techTrade2 = createTrade("MSFT", "Technology", -5.0, LocalDate.of(2024, 1, 2))
        val healthTrade1 = createTrade("JNJ", "Healthcare", 8.0, LocalDate.of(2024, 1, 3))

        val report = BacktestReport(
            winningTrades = listOf(techTrade1, healthTrade1),
            losingTrades = listOf(techTrade2)
        )

        val techStats = report.sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats)
        assertEquals(2, techStats!!.trades.size, "Should include all trades for the sector")
        assertTrue(techStats.trades.contains(techTrade1), "Should contain winning tech trade")
        assertTrue(techStats.trades.contains(techTrade2), "Should contain losing tech trade")

        val healthStats = report.sectorStats.find { it.sector == "Healthcare" }
        assertNotNull(healthStats)
        assertEquals(1, healthStats!!.trades.size, "Should include all trades for the sector")
        assertTrue(healthStats.trades.contains(healthTrade1), "Should contain health trade")
    }

    @Test
    fun `should handle edge case with zero profit trades`() {
        val techTrade1 = createTrade("AAPL", "Technology", 0.0, LocalDate.of(2024, 1, 1))
        val techTrade2 = createTrade("MSFT", "Technology", 5.0, LocalDate.of(2024, 1, 2))

        val report = BacktestReport(
            winningTrades = listOf(techTrade2),
            losingTrades = listOf(techTrade1)
        )

        val techStats = report.sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats)
        assertEquals(2, techStats!!.totalTrades)
        assertEquals(1, techStats.winningTrades, "Zero profit should count as losing trade")
        assertEquals(1, techStats.losingTrades)
    }

    @Test
    fun `should calculate average loss as absolute value`() {
        val techTrade1 = createTrade("AAPL", "Technology", -10.0, LocalDate.of(2024, 1, 1))
        val techTrade2 = createTrade("MSFT", "Technology", -6.0, LocalDate.of(2024, 1, 2))

        val report = BacktestReport(
            winningTrades = emptyList(),
            losingTrades = listOf(techTrade1, techTrade2)
        )

        val techStats = report.sectorStats.find { it.sector == "Technology" }
        assertNotNull(techStats)

        val expectedAvgLoss = 8.0 // abs((-10 + -6) / 2) = 8
        assertEquals(expectedAvgLoss, techStats!!.averageLossPercent, 0.001, "Average loss should be positive (absolute value)")
    }

    // Helper method to create test trades
    private fun createTrade(
        symbol: String,
        sector: String,
        profitPercentage: Double,
        entryDate: LocalDate
    ): Trade {
        val entryQuote = StockQuote(
            date = entryDate,
            closePrice = 100.0
        )
        val exitQuote = StockQuote(
            date = entryDate.plusDays(5),
            closePrice = 100.0 + profitPercentage
        )

        return Trade(
            stockSymbol = symbol,
            entryQuote = entryQuote,
            quotes = listOf(exitQuote),
            exitReason = "Test exit",
            profit = profitPercentage,
            startDate = entryDate,
            sector = sector
        )
    }
}
