package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.OrderBlock
import com.skrymer.udgaard.model.OrderBlockType
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class PlanEtfStrategyIntegrationTest {

    private val entryStrategy = PlanEtfEntryStrategy()
    private val exitStrategy = PlanEtfExitStrategy()

    @Test
    fun `entry strategy should pass when all conditions are met`() {
        // Create an order block that satisfies the belowOrderBlock condition
        // For price 102.0 to be 2% below orderBlock.low of 105.0:
        // requiredPrice = 105.0 * (1.0 - 0.02) = 102.9
        // closePrice 102.0 <= 102.9 ✓
        val orderBlock = OrderBlock(
            low = 105.0,
            high = 110.0,
            startDate = LocalDate.of(2023, 12, 1), // > 30 days before test date
            endDate = null, // Still active
            orderBlockType = OrderBlockType.BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            lastBuySignal = LocalDate.of(2024, 1, 10),
            heatmap = 65.0,
            closePrice = 102.0,
            closePriceEMA10 = 101.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 98.0,  // Uptrend: EMA10 > EMA20, closePrice > EMA50
            atr = 2.0  // Value zone: 100 to 100 + (2 * 2) = 104
        )

        assertTrue(entryStrategy.test(stock, quote),
            "Entry strategy should pass when all conditions are met")
    }

    @Test
    fun `entry strategy should fail when not in uptrend`() {
        val orderBlock = OrderBlock(
            low = 105.0,
            high = 110.0,
            startDate = LocalDate.of(2023, 12, 1),
            endDate = null,
            orderBlockType = OrderBlockType.BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Downtrend",
            lastBuySignal = LocalDate.of(2024, 1, 10),
            heatmap = 65.0,
            closePrice = 102.0,
            closePriceEMA10 = 99.0,
            closePriceEMA20 = 100.0,  // EMA10 < EMA20 = downtrend
            closePriceEMA50 = 98.0,
            atr = 2.0
        )

        assertFalse(entryStrategy.test(stock, quote),
            "Entry strategy should fail when not in uptrend")
    }

    @Test
    fun `entry strategy should fail when no buy signal`() {
        val orderBlock = OrderBlock(
            low = 105.0,
            high = 110.0,
            startDate = LocalDate.of(2023, 12, 1),
            endDate = null,
            orderBlockType = OrderBlockType.BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            heatmap = 65.0,
            closePrice = 102.0,
            closePriceEMA10 = 101.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 98.0,  // Uptrend but missing buy signal
            atr = 2.0
        )

        assertFalse(entryStrategy.test(stock, quote),
            "Entry strategy should fail when no buy signal")
    }

    @Test
    fun `entry strategy should fail when heatmap is too high`() {
        val orderBlock = OrderBlock(
            low = 105.0,
            high = 110.0,
            startDate = LocalDate.of(2023, 12, 1),
            endDate = null,
            orderBlockType = OrderBlockType.BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            lastBuySignal = LocalDate.of(2024, 1, 10),
            heatmap = 75.0,  // Fails here (>= 70)
            closePrice = 102.0,
            closePriceEMA10 = 101.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 98.0,  // Uptrend but heatmap too high
            atr = 2.0
        )

        assertFalse(entryStrategy.test(stock, quote),
            "Entry strategy should fail when heatmap is too high")
    }

    @Test
    fun `entry strategy should fail when price is outside value zone`() {
        val orderBlock = OrderBlock(
            low = 108.0, // Adjusted for higher price
            high = 115.0,
            startDate = LocalDate.of(2023, 12, 1),
            endDate = null,
            orderBlockType = OrderBlockType.BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            lastBuySignal = LocalDate.of(2024, 1, 10),
            heatmap = 65.0,
            closePrice = 106.0,  // Fails here (>= 104)
            closePriceEMA10 = 101.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 98.0,  // Uptrend but price outside value zone
            atr = 2.0  // Value zone: 100 to 100 + (2 * 2) = 104
        )

        assertFalse(entryStrategy.test(stock, quote),
            "Entry strategy should fail when price is outside value zone")
    }

    @Test
    fun `entry strategy should fail when not below order block`() {
        // Order block at 105.0, price at 104.0
        // requiredPrice = 105.0 * 0.98 = 102.9
        // closePrice 104.0 > 102.9, so it should fail
        val orderBlock = OrderBlock(
            low = 105.0,
            high = 110.0,
            startDate = LocalDate.of(2023, 12, 1),
            endDate = null,
            orderBlockType = OrderBlockType.BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            lastBuySignal = LocalDate.of(2024, 1, 10),
            heatmap = 65.0,
            closePrice = 104.0,  // Too close to order block (not 2% below)
            closePriceEMA10 = 101.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 98.0,  // Uptrend but not 2% below order block
            atr = 2.0
        )

        assertFalse(entryStrategy.test(stock, quote),
            "Entry strategy should fail when price is not 2% below order block")
    }

    @Test
    fun `exit strategy should exit when in order block`() {
        // Create an order block that:
        // - Duration is >= 30 days (from 2023-11-01 to 2024-02-01 = 92 days)
        // - startDate is before quote date (2023-11-01 < 2024-01-15)
        // - endDate is after quote date (2024-02-01 > 2024-01-15)
        // - Price (102.0) is between low (100.0) and high (105.0)
        val orderBlock = OrderBlock(
            low = 100.0,
            high = 105.0,
            startDate = LocalDate.of(2023, 11, 1),
            endDate = LocalDate.of(2024, 2, 1), // Must not be null!
            orderBlockType = OrderBlockType.BEARISH  // Default is CALCULATED
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePrice = 102.0, // Within order block range
            atr = 2.0
        )

        assertTrue(exitStrategy.match(stock, null, quote),
            "Exit strategy should exit when price is in order block")
        assertTrue(exitStrategy.reason(stock, null, quote)?.contains("order block") == true,
            "Exit reason should mention order block")
    }

    @Test
    fun `exit strategy should exit when 10 EMA crosses under 20 EMA`() {
        val stock = Stock()
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            closePriceEMA10 = 95.0,  // Crossed under
            closePriceEMA20 = 100.0,
            closePrice = 102.0,
            atr = 2.0
        )

        assertTrue(exitStrategy.match(stock, null, quote),
            "Exit strategy should exit when 10 EMA crosses under 20 EMA")
        assertEquals("10 ema has crossed under the 20 ema", exitStrategy.reason(stock, null, quote))
    }

    @Test
    fun `exit strategy should exit at profit target`() {
        val stock = Stock()
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePrice = 110.0,  // 100 + (3.5 * 2) = 107, so 110 triggers exit
            atr = 2.0
        )

        assertTrue(exitStrategy.match(stock, null, quote),
            "Exit strategy should exit at profit target (3.5 ATR above 20 EMA)")
        assertEquals("Price is 3.5 ATR above 20 EMA", exitStrategy.reason(stock, null, quote))
    }

    @Test
    fun `exit strategy should not exit when no conditions are met`() {
        val stock = Stock()
        val quote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePrice = 104.0,  // Below profit target
            atr = 2.0
        )

        assertFalse(exitStrategy.match(stock, null, quote),
            "Exit strategy should not exit when no conditions are met")
    }

    @Test
    fun `exit strategy should use previous close price when close price is invalid`() {
        val testStock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(
                StockQuote(
                    date = LocalDate.of(2024, 1, 14),
                    closePrice = 102.0
                ),
                StockQuote(
                    date = LocalDate.of(2024, 1, 15),
                    closePrice = 0.5,  // Invalid price (< 1.0)
                    signal = "Sell"
                )
            ),
            orderBlocks = mutableListOf()
        )

        val entryQuote = testStock.quotes[0]
        val exitQuote = testStock.quotes[1]

        val exitPrice = exitStrategy.exitPrice(testStock, entryQuote, exitQuote)
        assertEquals(102.0, exitPrice,
            "Exit price should use previous quote when current price is invalid")
    }

    @Test
    fun `strategies should work together in a complete trade scenario`() {
        val orderBlock = OrderBlock(
            low = 105.0,
            high = 110.0,
            startDate = LocalDate.of(2023, 12, 1),
            endDate = null,
            orderBlockType = OrderBlockType.BEARISH
        )

        val stock = Stock(
            symbol = "TEST",
            sectorSymbol = "XLK",
            quotes = mutableListOf(),
            orderBlocks = mutableListOf(orderBlock)
        )

        // Setup entry scenario
        val entryQuote = StockQuote(
            date = LocalDate.of(2024, 1, 15),
            trend = "Uptrend",
            lastBuySignal = LocalDate.of(2024, 1, 10),
            heatmap = 65.0,
            closePrice = 102.0,
            closePriceEMA10 = 101.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 98.0,  // Uptrend for entry
            atr = 2.0
        )

        // Verify entry
        assertTrue(entryStrategy.test(stock, entryQuote),
            "Should enter trade when conditions are met")

        // Setup exit scenario (profit target reached)
        val exitQuote = StockQuote(
            date = LocalDate.of(2024, 1, 20),
            closePriceEMA10 = 107.0,
            closePriceEMA20 = 105.0,
            closePrice = 113.0,  // 105 + (3.5 * 2) = 112, so 113 triggers exit
            atr = 2.0
        )

        // Verify exit
        assertTrue(exitStrategy.match(stock, entryQuote, exitQuote),
            "Should exit trade at profit target")
        assertEquals("Price is 3.5 ATR above 20 EMA", exitStrategy.reason(stock, entryQuote, exitQuote))
    }
}
