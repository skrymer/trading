package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class StopLossExitTest {

    private val stock = Stock()

    @Test
    fun `should exit when price drops below stop loss level`() {
        val condition = StopLossExit(atrMultiplier = 2.0)

        val entryQuote = StockQuote(
            date = LocalDate.of(2024, 1, 1),
            closePrice = 100.0,
            atr = 2.0
        )

        // Stop loss level: 100 - (2 * 2) = 96
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 95.0  // Below stop loss
        )

        assertTrue(condition.shouldExit(stock, entryQuote, quote),
            "Should exit when price drops below stop loss level")
    }

    @Test
    fun `should not exit when price is above stop loss level`() {
        val condition = StopLossExit(atrMultiplier = 2.0)

        val entryQuote = StockQuote(
            date = LocalDate.of(2024, 1, 1),
            closePrice = 100.0,
            atr = 2.0
        )

        // Stop loss level: 100 - (2 * 2) = 96
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 97.0  // Above stop loss
        )

        assertFalse(condition.shouldExit(stock, entryQuote, quote),
            "Should not exit when price is above stop loss level")
    }

    @Test
    fun `should not exit when price equals stop loss level`() {
        val condition = StopLossExit(atrMultiplier = 2.0)

        val entryQuote = StockQuote(
            date = LocalDate.of(2024, 1, 1),
            closePrice = 100.0,
            atr = 2.0
        )

        // Stop loss level: 100 - (2 * 2) = 96
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 96.0  // Exactly at stop loss
        )

        assertFalse(condition.shouldExit(stock, entryQuote, quote),
            "Should not exit when price equals stop loss level")
    }

    @Test
    fun `should work with different ATR multipliers`() {
        val condition = StopLossExit(atrMultiplier = 1.5)

        val entryQuote = StockQuote(
            date = LocalDate.of(2024, 1, 1),
            closePrice = 100.0,
            atr = 2.0
        )

        // Stop loss level: 100 - (1.5 * 2) = 97
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 96.5  // Below stop loss
        )

        assertTrue(condition.shouldExit(stock, entryQuote, quote),
            "Should work with different ATR multipliers")
    }

    @Test
    fun `should work with different ATR values`() {
        val condition = StopLossExit(atrMultiplier = 2.0)

        val entryQuote = StockQuote(
            date = LocalDate.of(2024, 1, 1),
            closePrice = 100.0,
            atr = 3.0  // Different ATR
        )

        // Stop loss level: 100 - (2 * 3) = 94
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 93.0  // Below stop loss
        )

        assertTrue(condition.shouldExit(stock, entryQuote, quote),
            "Should work with different ATR values")
    }

    @Test
    fun `should not exit when entryQuote is null`() {
        val condition = StopLossExit(atrMultiplier = 2.0)

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 50.0  // Very low price
        )

        assertFalse(condition.shouldExit(stock, null, quote),
            "Should not exit when entryQuote is null")
    }

    @Test
    fun `should not exit when price is moving up from entry`() {
        val condition = StopLossExit(atrMultiplier = 2.0)

        val entryQuote = StockQuote(
            date = LocalDate.of(2024, 1, 1),
            closePrice = 100.0,
            atr = 2.0
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 105.0  // Price went up
        )

        assertFalse(condition.shouldExit(stock, entryQuote, quote),
            "Should not exit when price is moving up from entry")
    }

    @Test
    fun `should provide correct exit reason`() {
        val condition = StopLossExit(atrMultiplier = 2.0)
        assertEquals("Stop loss triggered (2.0 ATR below entry)", condition.exitReason())
    }

    @Test
    fun `should provide correct description`() {
        val condition = StopLossExit(atrMultiplier = 2.0)
        assertEquals("Stop loss (2.0 ATR)", condition.description())
    }

    @Test
    fun `should use default ATR multiplier of 2`() {
        val condition = StopLossExit()
        assertEquals("Stop loss (2.0 ATR)", condition.description())
    }

    @Test
    fun `should handle small price movements correctly`() {
        val condition = StopLossExit(atrMultiplier = 2.0)

        val entryQuote = StockQuote(
            date = LocalDate.of(2024, 1, 1),
            closePrice = 100.0,
            atr = 2.0
        )

        // Stop loss level: 100 - (2 * 2) = 96
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 96.01  // Just above stop loss
        )

        assertFalse(condition.shouldExit(stock, entryQuote, quote),
            "Should not exit when just above stop loss")

        val quote2 = StockQuote(
            date = LocalDate.of(2024, 1, 5),
            closePrice = 95.99  // Just below stop loss
        )

        assertTrue(condition.shouldExit(stock, entryQuote, quote2),
            "Should exit when just below stop loss")
    }
}
