package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class UptrendConditionTest {

    private val condition = UptrendCondition()
    private val stock = Stock()

    @Test
    fun `should return true when stock is in uptrend`() {
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend"
        )

        assertTrue(condition.evaluate(stock, quote),
            "Condition should be true when stock is in uptrend")
    }

    @Test
    fun `should return false when stock is not in uptrend`() {
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Downtrend"
        )

        assertFalse(condition.evaluate(stock, quote),
            "Condition should be false when stock is not in uptrend")
    }

    @Test
    fun `should provide correct description`() {
        assertEquals("Stock is in uptrend", condition.description())
    }
}
