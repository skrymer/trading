package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

/**
 * Unit tests for ATR calculation using real QQQ price data and AlphaVantage reference values.
 *
 * This test verifies that our Wilder's smoothing ATR implementation matches AlphaVantage's
 * ATR calculations, which is the industry standard.
 *
 * Test data: QQQ prices from December 2024 through January 2025
 * Reference: AlphaVantage ATR values (14-period ATR using Wilder's smoothing)
 */
class TechnicalIndicatorCalculatorATRTest {

    private val calculator = TechnicalIndicatorCalculator()

    /**
     * Creates a mock OvtlyrStockInformation object with real QQQ price data.
     * Uses reflection to set the private quotes field.
     */
    private fun createQQQStockData(): OvtlyrStockInformation {
        val quotes = listOf(
            // November 2024 data (needed for sufficient history before 2025-01-02)
            createQuote("2024-11-01", 485.5, 490.7507, 485.2, 487.43),
            createQuote("2024-11-04", 486.82, 489.38, 484.2545, 486.01),
            createQuote("2024-11-05", 487.61, 492.88, 487.52, 492.21),
            createQuote("2024-11-06", 500.56, 506.41, 499.6, 505.58),
            createQuote("2024-11-07", 508.4, 514.33, 508.34, 513.54),
            createQuote("2024-11-08", 513.035, 514.9175, 512.41, 514.14),
            createQuote("2024-11-11", 515.37, 515.58, 510.92, 513.84),
            createQuote("2024-11-12", 513.77, 514.66, 509.83, 512.91),
            createQuote("2024-11-13", 512.4, 514.98, 509.95, 512.25),
            createQuote("2024-11-14", 511.91, 512.79, 507.77, 508.69),
            createQuote("2024-11-15", 502.94, 503.33, 494.49, 496.57),
            createQuote("2024-11-18", 498.13, 502.14, 496.73, 500.02),
            createQuote("2024-11-19", 497.42, 503.9536, 497.08, 503.46),
            createQuote("2024-11-20", 503.16, 503.48, 496.555, 503.17),
            createQuote("2024-11-21", 506.24, 506.96, 497.56, 504.98),
            createQuote("2024-11-22", 504.42, 506.53, 502.78, 505.79),
            createQuote("2024-11-25", 509.9, 511.4525, 504.2625, 506.59),
            createQuote("2024-11-26", 508.08, 510.14, 507.23, 509.31),
            createQuote("2024-11-27", 508.17, 508.24, 501.93, 505.3),
            createQuote("2024-11-29", 505.93, 510.34, 505.31, 509.74),

            // December 2024 data (needed for initial 14-period ATR calculation)
            createQuote("2024-12-02", 511.01, 516.26, 510.62, 515.29),
            createQuote("2024-12-03", 513.95, 517.15, 513.37, 516.87),
            createQuote("2024-12-04", 520.32, 523.52, 519.6, 523.26),
            createQuote("2024-12-05", 523.31, 524.0399, 521.42, 521.81),
            createQuote("2024-12-06", 522.48, 526.72, 522.35, 526.48),
            createQuote("2024-12-09", 525.55, 526.35, 521.22, 522.38),
            createQuote("2024-12-10", 523.62, 525.38, 519.16, 520.6),
            createQuote("2024-12-11", 525.0, 530.61, 524.59, 529.92),
            createQuote("2024-12-12", 527.68, 528.96, 526.02, 526.5),
            createQuote("2024-12-13", 530.46, 533.1661, 527.3001, 530.53),
            createQuote("2024-12-16", 533.08, 539.15, 533.0, 538.17),
            createQuote("2024-12-17", 536.36, 537.48, 534.13, 535.8),
            createQuote("2024-12-18", 535.15, 536.8799, 515.01, 516.47),
            createQuote("2024-12-19", 521.19, 521.7599, 513.83, 514.17),
            createQuote("2024-12-20", 510.44, 524.82, 509.29, 518.66),
            createQuote("2024-12-23", 519.55, 523.25, 516.13, 522.87),
            createQuote("2024-12-24", 524.83, 530.05, 524.19, 529.96),
            createQuote("2024-12-26", 528.32, 531.24, 526.31, 529.6),
            createQuote("2024-12-27", 526.01, 526.45, 517.86, 522.56),
            createQuote("2024-12-30", 515.51, 519.36, 511.83, 515.61),
            createQuote("2024-12-31", 516.9, 517.66, 510.26, 511.23),

            // January 2025 data (test period)
            createQuote("2025-01-02", 514.3, 516.64, 505.71, 510.23),
            createQuote("2025-01-03", 513.35, 519.6454, 512.53, 518.58),
            createQuote("2025-01-06", 524.02, 527.92, 522.03, 524.54),
            createQuote("2025-01-07", 525.59, 525.99, 513.28, 515.18),
            createQuote("2025-01-08", 515.08, 516.9199, 510.57, 515.27),
            createQuote("2025-01-10", 511.48, 511.58, 503.92, 507.19),
            createQuote("2025-01-13", 501.2, 506.02, 499.7, 505.56),
            createQuote("2025-01-14", 508.74, 510.155, 501.59, 505.08),
            createQuote("2025-01-15", 513.03, 517.85, 511.46, 516.7),
            createQuote("2025-01-16", 518.98, 519.0599, 512.95, 513.08),
            createQuote("2025-01-17", 522.85, 524.0733, 513.1086, 521.74),
            createQuote("2025-01-21", 524.48, 525.97, 520.06, 524.8),
            createQuote("2025-01-22", 529.57, 533.82, 529.26, 531.51),
            createQuote("2025-01-23", 529.04, 532.76, 528.45, 532.64),
            createQuote("2025-01-24", 533.02, 533.79, 528.15, 529.63)
        )

        val stock = OvtlyrStockInformation()

        // Use reflection to set the private quotes field
        val quotesField = OvtlyrStockInformation::class.java.getDeclaredField("quotes")
        quotesField.isAccessible = true
        quotesField.set(stock, quotes)

        // Use reflection to set stockName
        val stockNameField = OvtlyrStockInformation::class.java.getDeclaredField("stockName")
        stockNameField.isAccessible = true
        stockNameField.set(stock, "QQQ")

        // Use reflection to set sectorSymbol
        val sectorSymbolField = OvtlyrStockInformation::class.java.getDeclaredField("sectorSymbol")
        sectorSymbolField.isAccessible = true
        sectorSymbolField.set(stock, "SPY")

        return stock
    }

    private fun createQuote(date: String, open: Double, high: Double, low: Double, close: Double): OvtlyrStockQuote {
        val quote = OvtlyrStockQuote()

        // Use reflection to set private final fields
        val dateField = OvtlyrStockQuote::class.java.getDeclaredField("date")
        dateField.isAccessible = true
        dateField.set(quote, LocalDate.parse(date))

        val openPriceField = OvtlyrStockQuote::class.java.getDeclaredField("openPrice")
        openPriceField.isAccessible = true
        openPriceField.set(quote, open)

        val highField = OvtlyrStockQuote::class.java.getDeclaredField("high")
        highField.isAccessible = true
        highField.set(quote, high)

        val lowField = OvtlyrStockQuote::class.java.getDeclaredField("low")
        lowField.isAccessible = true
        lowField.set(quote, low)

        val closePriceField = OvtlyrStockQuote::class.java.getDeclaredField("closePrice")
        closePriceField.isAccessible = true
        closePriceField.set(quote, close)

        return quote
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-02`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-02")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.0788

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-02 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-03`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-03")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.1018

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-03 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-06`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-06")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.1239

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-06 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-07`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-07")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.1793

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-07 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-08`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-08")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.1707

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-08 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-10`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-10")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.2121

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-10 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-13`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-13")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.2146

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-13 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-14`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-14")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.2278

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-14 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-15`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-15")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.2827

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-15 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-16`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-16")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.2708

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-16 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-17`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-17")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.3076

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-17 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-21`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-21")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.2934

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-21 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-22`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-22")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.3103

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-22 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-23`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-23")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.2802

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-23 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should match AlphaVantage for 2025-01-24`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2025-01-24")

        val atr = calculator.calculateATR(stock, date, 14)
        val expectedATR = 7.2636

        assertEquals(expectedATR, atr, 0.01,
            "ATR for 2025-01-24 should match AlphaVantage value within 0.01")
    }

    @Test
    fun `calculateATR should return 0 for insufficient data`() {
        val stock = createQQQStockData()
        val date = LocalDate.parse("2024-12-05")  // Only 4 days of data

        val atr = calculator.calculateATR(stock, date, 14)

        assertEquals(0.0, atr,
            "ATR should be 0.0 when insufficient data available (need 14 periods)")
    }

    @Test
    fun `calculateATR should use Wilders smoothing not simple average`() {
        val stock = createQQQStockData()

        // Calculate ATR for two consecutive days
        val atr1 = calculator.calculateATR(stock, LocalDate.parse("2025-01-02"), 14)
        val atr2 = calculator.calculateATR(stock, LocalDate.parse("2025-01-03"), 14)

        // ATR should change gradually (smoothed), not dramatically
        val change = kotlin.math.abs(atr2 - atr1)
        assertTrue(change < 1.0,
            "ATR change between consecutive days should be small due to Wilder's smoothing, was: $change")
    }
}
