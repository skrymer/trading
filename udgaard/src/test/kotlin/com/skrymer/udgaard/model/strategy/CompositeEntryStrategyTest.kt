package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.LogicalOperator
import com.skrymer.udgaard.model.strategy.condition.entry.BuySignalCondition
import com.skrymer.udgaard.model.strategy.condition.entry.HeatmapCondition
import com.skrymer.udgaard.model.strategy.condition.entry.UptrendCondition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class CompositeEntryStrategyTest {

    private val stock = Stock()

    @Test
    fun `should pass when all AND conditions are met`() {
        val strategy = CompositeEntryStrategy(
            conditions = listOf(
                UptrendCondition(),
                BuySignalCondition(currentOnly = false),
                HeatmapCondition(70.0)
            ),
            operator = LogicalOperator.AND
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            lastBuySignal = LocalDate.of(2024, 1, 10),
            heatmap = 65.0
        )

        assertTrue(strategy.test(stock, quote),
            "Strategy should pass when all AND conditions are met")
    }

    @Test
    fun `should fail when one AND condition is not met`() {
        val strategy = CompositeEntryStrategy(
            conditions = listOf(
                UptrendCondition(),
                BuySignalCondition(currentOnly = false),
                HeatmapCondition(70.0)
            ),
            operator = LogicalOperator.AND
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            heatmap = 65.0
        )

        assertFalse(strategy.test(stock, quote),
            "Strategy should fail when one AND condition is not met")
    }

    @Test
    fun `should pass when at least one OR condition is met`() {
        val strategy = CompositeEntryStrategy(
            conditions = listOf(
                UptrendCondition(),
                BuySignalCondition(currentOnly = false)
            ),
            operator = LogicalOperator.OR
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Downtrend",
            lastBuySignal = LocalDate.of(2024, 1, 10)
        )

        assertTrue(strategy.test(stock, quote),
            "Strategy should pass when at least one OR condition is met")
    }

    @Test
    fun `should fail when all OR conditions fail`() {
        val strategy = CompositeEntryStrategy(
            conditions = listOf(
                UptrendCondition(),
                BuySignalCondition(currentOnly = false)
            ),
            operator = LogicalOperator.OR
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Downtrend"
        )

        assertFalse(strategy.test(stock, quote),
            "Strategy should fail when all OR conditions fail")
    }

    @Test
    fun `should negate condition with NOT operator`() {
        val strategy = CompositeEntryStrategy(
            conditions = listOf(UptrendCondition()),
            operator = LogicalOperator.NOT
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend"
        )

        assertFalse(strategy.test(stock, quote),
            "Strategy should negate condition with NOT operator")
    }

    @Test
    fun `should return false when no conditions provided`() {
        val strategy = CompositeEntryStrategy(
            conditions = emptyList(),
            operator = LogicalOperator.AND
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15)
        )

        assertFalse(strategy.test(stock, quote),
            "Strategy should return false when no conditions provided")
    }

    @Test
    fun `should work with DSL builder`() {
        val strategy = entryStrategy {
            uptrend()
            buySignal(currentOnly = false)
            heatmap(70)
        }

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            lastBuySignal = LocalDate.of(2024, 1, 10),
            heatmap = 65.0
        )

        assertTrue(strategy.test(stock, quote),
            "DSL builder should create working strategy")
    }

    @Test
    fun `should provide custom description when specified`() {
        val strategy = CompositeEntryStrategy(
            conditions = listOf(UptrendCondition()),
            operator = LogicalOperator.AND,
            strategyDescription = "Custom strategy"
        )

        assertEquals("Custom strategy", strategy.description())
    }

    @Test
    fun `should generate description from conditions when not specified`() {
        val strategy = CompositeEntryStrategy(
            conditions = listOf(
                UptrendCondition(),
                BuySignalCondition(currentOnly = false)
            ),
            operator = LogicalOperator.AND
        )

        val description = strategy.description()
        assertTrue(description.contains("Stock is in uptrend"),
            "Description should include first condition")
        assertTrue(description.contains("Has buy signal"),
            "Description should include second condition")
    }
}
