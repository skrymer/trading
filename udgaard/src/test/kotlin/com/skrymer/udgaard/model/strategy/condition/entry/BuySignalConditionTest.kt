package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class BuySignalConditionTest {

    private val stock = Stock()

    @Test
    fun `should return true when has buy signal and currentOnly is false`() {
        val condition = BuySignalCondition(currentOnly = false)
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            lastBuySignal = LocalDate.of(2024, 1, 10)
        )

        assertTrue(condition.evaluate(stock, quote),
            "Condition should be true when buy signal exists")
    }

    @Test
    fun `should return false when no buy signal and currentOnly is false`() {
        val condition = BuySignalCondition(currentOnly = false)
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15)
        )

        assertFalse(condition.evaluate(stock, quote),
            "Condition should be false when no buy signal")
    }

    @Test
    fun `should return true when has current buy signal and currentOnly is true`() {
        val condition = BuySignalCondition(currentOnly = true)
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            lastBuySignal = LocalDate.of(2024, 1, 14)
        )

        assertTrue(condition.evaluate(stock, quote),
            "Condition should be true when current buy signal exists")
    }

    @Test
    fun `should return false when no current buy signal and currentOnly is true`() {
        val condition = BuySignalCondition(currentOnly = true)
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15)
        )

        assertFalse(condition.evaluate(stock, quote),
            "Condition should be false when no current buy signal")
    }

    @Test
    fun `should provide correct description for regular buy signal`() {
        val condition = BuySignalCondition(currentOnly = false)
        assertEquals("Has buy signal", condition.description())
    }

    @Test
    fun `should provide correct description for current buy signal`() {
        val condition = BuySignalCondition(currentOnly = true)
        assertEquals("Has current buy signal (< 1 day old)", condition.description())
    }
}
