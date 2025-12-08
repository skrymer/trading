package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.OrderBlock
import com.skrymer.udgaard.model.OrderBlockType
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class OrderBlockExitTest {

    @Test
    fun `should exit when within order block older than threshold`() {
        val condition = OrderBlockExit(orderBlockAgeInDays = 120)

        // Create an order block that is 150 days old (older than 120 day threshold)
        val orderBlock = OrderBlock(
            low = 95.0,
            high = 105.0,
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 5, 30),  // 150 days duration
            orderBlockType = OrderBlockType.BEARISH,  // Must be BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 3, 1),  // Within startDate and endDate
            closePrice = 100.0  // Within order block range (> low, < high)
        )

        assertTrue(condition.shouldExit(stock, null, quote),
            "Should exit when within order block older than threshold")
    }

    @Test
    fun `should not exit when not within any order block`() {
        val condition = OrderBlockExit(orderBlockAgeInDays = 120)

        val orderBlock = OrderBlock(
            low = 95.0,
            high = 105.0,
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 5, 30),
            orderBlockType = OrderBlockType.BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 3, 1),
            closePrice = 110.0  // Outside order block range (> high)
        )

        assertFalse(condition.shouldExit(stock, null, quote),
            "Should not exit when not within order block")
    }

    @Test
    fun `should not exit when stock has no order blocks`() {
        val condition = OrderBlockExit(orderBlockAgeInDays = 120)

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf()
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 6, 1),
            closePrice = 100.0
        )

        assertFalse(condition.shouldExit(stock, null, quote),
            "Should not exit when stock has no order blocks")
    }

    @Test
    fun `should work with different age thresholds`() {
        val condition = OrderBlockExit(orderBlockAgeInDays = 30)

        // Create an order block that is 60 days old
        val orderBlock = OrderBlock(
            low = 95.0,
            high = 105.0,
            startDate = LocalDate.of(2024, 4, 1),
            endDate = LocalDate.of(2024, 5, 30),  // 60 days duration
            orderBlockType = OrderBlockType.BEARISH,
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 5, 15),  // Within startDate and endDate
            closePrice = 100.0  // Within order block range
        )

        assertTrue(condition.shouldExit(stock, null, quote),
            "Should work with custom age threshold")
    }

    @Test
    fun `should provide correct exit reason`() {
        val condition = OrderBlockExit(orderBlockAgeInDays = 120)
        assertEquals("Quote is within an order block older than 120 days", condition.exitReason())
    }

    @Test
    fun `should provide correct description`() {
        val condition = OrderBlockExit(orderBlockAgeInDays = 120)
        assertEquals("Within order block (age > 120d)", condition.description())
    }

    @Test
    fun `should use default age of 120 days`() {
        val condition = OrderBlockExit()
        assertTrue(condition.exitReason().contains("120 days"))
    }
}
