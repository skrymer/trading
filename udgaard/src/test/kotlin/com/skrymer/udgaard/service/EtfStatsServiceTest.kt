package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.Etf
import com.skrymer.udgaard.model.EtfMembership
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class EtfStatsServiceTest {

    private lateinit var etfStatsService: EtfStatsService
    private lateinit var stockService: StockService

    @BeforeEach
    fun setup() {
        stockService = mock<StockService>()
        etfStatsService = EtfStatsService(stockService)
    }

    @Test
    fun `should calculate uptrend correctly when 10 EMA greater than 20 EMA and close greater than 50 EMA`() {
        // Given: Stock in uptrend (10 EMA > 20 EMA AND Close > 50 EMA)
        val date = LocalDate.of(2025, 1, 15)
        val quote = StockQuote(
            date = date,
            closePrice = 110.0,
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 95.0,
            openPrice = 108.0
        )
        val stock = Stock("AAPL", "Technology", listOf(quote), emptyList())

        doReturn(listOf(stock)).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date, date)

        // Then
        assertEquals(100.0, result.currentStats.bullishPercent)
        assertEquals(1, result.currentStats.stocksInUptrend)
        assertEquals(0, result.currentStats.stocksInDowntrend)
        assertEquals(0, result.currentStats.stocksInNeutral)
        assertEquals(1, result.currentStats.totalStocks)
        assertTrue(result.currentStats.inUptrend)
    }

    @Test
    fun `should calculate downtrend correctly when 10 EMA less than 20 EMA and close less than 50 EMA`() {
        // Given: Stock in downtrend (10 EMA < 20 EMA AND Close < 50 EMA)
        val date = LocalDate.of(2025, 1, 15)
        val quote = StockQuote(
            date = date,
            closePrice = 90.0,
            closePriceEMA10 = 95.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 105.0,
            openPrice = 92.0
        )
        val stock = Stock("AAPL", "Technology", listOf(quote), emptyList())

        doReturn(listOf(stock)).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date, date)

        // Then
        assertEquals(0.0, result.currentStats.bullishPercent)
        assertEquals(0, result.currentStats.stocksInUptrend)
        assertEquals(1, result.currentStats.stocksInDowntrend)
        assertEquals(0, result.currentStats.stocksInNeutral)
        assertFalse(result.currentStats.inUptrend)
    }

    @Test
    fun `should calculate neutral state when conditions are mixed`() {
        // Given: Stock in neutral (10 EMA > 20 EMA BUT Close < 50 EMA)
        val date = LocalDate.of(2025, 1, 15)
        val quote = StockQuote(
            date = date,
            closePrice = 95.0,
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 110.0,
            openPrice = 96.0
        )
        val stock = Stock("AAPL", "Technology", listOf(quote), emptyList())

        doReturn(listOf(stock)).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date, date)

        // Then
        assertEquals(0.0, result.currentStats.bullishPercent)
        assertEquals(0, result.currentStats.stocksInUptrend)
        assertEquals(0, result.currentStats.stocksInDowntrend)
        assertEquals(1, result.currentStats.stocksInNeutral)
        assertFalse(result.currentStats.inUptrend)
    }

    @Test
    fun `should calculate bullish percentage correctly with mixed stock states`() {
        // Given: 3 stocks - 2 uptrend, 1 downtrend = 66.7% bullish
        val date = LocalDate.of(2025, 1, 15)

        val uptrendQuote1 = StockQuote(
            date = date,
            closePrice = 110.0,
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 95.0,
            openPrice = 108.0
        )
        val uptrendQuote2 = StockQuote(
            date = date,
            closePrice = 120.0,
            closePriceEMA10 = 115.0,
            closePriceEMA20 = 110.0,
            closePriceEMA50 = 105.0,
            openPrice = 118.0
        )
        val downtrendQuote = StockQuote(
            date = date,
            closePrice = 90.0,
            closePriceEMA10 = 95.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 105.0,
            openPrice = 92.0
        )

        val stocks = listOf(
            Stock("AAPL", "Technology", listOf(uptrendQuote1), emptyList()),
            Stock("MSFT", "Technology", listOf(uptrendQuote2), emptyList()),
            Stock("META", "Technology", listOf(downtrendQuote), emptyList())
        )

        doReturn(stocks).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date, date)

        // Then
        assertEquals(66.66666666666667, result.currentStats.bullishPercent, 0.0001)
        assertEquals(2, result.currentStats.stocksInUptrend)
        assertEquals(1, result.currentStats.stocksInDowntrend)
        assertEquals(0, result.currentStats.stocksInNeutral)
        assertEquals(3, result.currentStats.totalStocks)
        assertTrue(result.currentStats.inUptrend) // > 50%
    }

    @Test
    fun `should generate historical data over date range`() {
        // Given: Stock with quotes over 3 days
        val date1 = LocalDate.of(2025, 1, 13)
        val date2 = LocalDate.of(2025, 1, 14)
        val date3 = LocalDate.of(2025, 1, 15)

        val quote1 = StockQuote(
            date = date1,
            closePrice = 100.0,
            closePriceEMA10 = 98.0,
            closePriceEMA20 = 96.0,
            closePriceEMA50 = 94.0,
            openPrice = 99.0
        )
        val quote2 = StockQuote(
            date = date2,
            closePrice = 105.0,
            closePriceEMA10 = 103.0,
            closePriceEMA20 = 101.0,
            closePriceEMA50 = 99.0,
            openPrice = 104.0
        )
        val quote3 = StockQuote(
            date = date3,
            closePrice = 110.0,
            closePriceEMA10 = 108.0,
            closePriceEMA20 = 106.0,
            closePriceEMA50 = 104.0,
            openPrice = 109.0
        )

        val stock = Stock("AAPL", "Technology", listOf(quote1, quote2, quote3), emptyList())
        doReturn(listOf(stock)).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date1, date3)

        // Then
        assertEquals(3, result.historicalData.size)
        assertEquals(date1, result.historicalData[0].date)
        assertEquals(date2, result.historicalData[1].date)
        assertEquals(date3, result.historicalData[2].date)

        // All three quotes are uptrend
        assertEquals(100.0, result.historicalData[0].bullishPercent)
        assertEquals(100.0, result.historicalData[1].bullishPercent)
        assertEquals(100.0, result.historicalData[2].bullishPercent)
    }

    @Test
    fun `should calculate change correctly between latest and previous day`() {
        // Given: Stock changing from downtrend to uptrend
        val date1 = LocalDate.of(2025, 1, 14)
        val date2 = LocalDate.of(2025, 1, 15)

        val downtrendQuote = StockQuote(
            date = date1,
            closePrice = 90.0,
            closePriceEMA10 = 95.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 105.0,
            openPrice = 92.0
        )
        val uptrendQuote = StockQuote(
            date = date2,
            closePrice = 110.0,
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 95.0,
            openPrice = 108.0
        )

        val stock = Stock("AAPL", "Technology", listOf(downtrendQuote, uptrendQuote), emptyList())
        doReturn(listOf(stock)).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date1, date2)

        // Then
        assertEquals(100.0, result.currentStats.bullishPercent) // Latest day: uptrend
        assertEquals(100.0, result.currentStats.change) // Changed from 0% to 100%
        assertEquals(date2, result.currentStats.lastUpdated)
    }

    @Test
    fun `should handle empty stock list gracefully`() {
        // Given: No stocks
        doReturn(emptyList<Stock>()).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(
            Etf.QQQ,
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 15)
        )

        // Then
        assertEquals(0.0, result.currentStats.bullishPercent)
        assertEquals(0.0, result.currentStats.change)
        assertEquals(0, result.currentStats.totalStocks)
        assertEquals(0, result.currentStats.stocksInUptrend)
        assertEquals(0, result.currentStats.stocksInDowntrend)
        assertEquals(0, result.currentStats.stocksInNeutral)
        assertFalse(result.currentStats.inUptrend)
        assertNull(result.currentStats.lastUpdated)
        assertTrue(result.historicalData.isEmpty())
    }

    @Test
    fun `should filter stocks by ETF membership`() {
        // Given: Repository returns only stocks in QQQ (filtering done at repository level)
        val date = LocalDate.of(2025, 1, 15)

        val appleQuote = StockQuote(
            date = date,
            closePrice = 110.0,
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 95.0,
            openPrice = 108.0
        )

        // Repository query returns only stocks that match the requested symbols (in QQQ)
        val stocks = listOf(
            Stock("AAPL", "Technology", listOf(appleQuote), emptyList()) // In QQQ
        )

        doReturn(stocks).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date, date)

        // Then - Only AAPL should be counted (repository filtered by ETF membership)
        assertEquals(1, result.currentStats.totalStocks)
        assertEquals(100.0, result.currentStats.bullishPercent)
    }

    @Test
    fun `should filter quotes by date range`() {
        // Given: Stock with quotes outside and inside date range
        val beforeRange = LocalDate.of(2025, 1, 1)
        val inRange1 = LocalDate.of(2025, 1, 10)
        val inRange2 = LocalDate.of(2025, 1, 11)
        val afterRange = LocalDate.of(2025, 1, 20)

        val quotes = listOf(
            StockQuote(
                date = beforeRange,
                closePrice = 100.0,
                closePriceEMA10 = 95.0,
                closePriceEMA20 = 90.0,
                closePriceEMA50 = 85.0,
                openPrice = 99.0
            ),
            StockQuote(
                date = inRange1,
                closePrice = 110.0,
                closePriceEMA10 = 105.0,
                closePriceEMA20 = 100.0,
                closePriceEMA50 = 95.0,
                openPrice = 108.0
            ),
            StockQuote(
                date = inRange2,
                closePrice = 115.0,
                closePriceEMA10 = 110.0,
                closePriceEMA20 = 105.0,
                closePriceEMA50 = 100.0,
                openPrice = 113.0
            ),
            StockQuote(
                date = afterRange,
                closePrice = 120.0,
                closePriceEMA10 = 115.0,
                closePriceEMA20 = 110.0,
                closePriceEMA50 = 105.0,
                openPrice = 118.0
            )
        )

        val stock = Stock("AAPL", "Technology", quotes, emptyList())
        doReturn(listOf(stock)).whenever(stockService).getStocksBySymbols(any(), any())

        // When - Query only for dates in range
        val result = etfStatsService.getEtfStats(Etf.QQQ, inRange1, inRange2)

        // Then - Should only have 2 historical data points
        assertEquals(2, result.historicalData.size)
        assertEquals(inRange1, result.historicalData[0].date)
        assertEquals(inRange2, result.historicalData[1].date)
    }

    @Test
    fun `should handle stocks with missing quotes on some dates`() {
        // Given: Two stocks, one missing a quote on date2
        val date1 = LocalDate.of(2025, 1, 14)
        val date2 = LocalDate.of(2025, 1, 15)

        val stock1Quotes = listOf(
            StockQuote(
                date = date1,
                closePrice = 110.0,
                closePriceEMA10 = 105.0,
                closePriceEMA20 = 100.0,
                closePriceEMA50 = 95.0,
                openPrice = 108.0
            ),
            StockQuote(
                date = date2,
                closePrice = 115.0,
                closePriceEMA10 = 110.0,
                closePriceEMA20 = 105.0,
                closePriceEMA50 = 100.0,
                openPrice = 113.0
            )
        )

        val stock2Quotes = listOf(
            StockQuote(
                date = date1,
                closePrice = 120.0,
                closePriceEMA10 = 115.0,
                closePriceEMA20 = 110.0,
                closePriceEMA50 = 105.0,
                openPrice = 118.0
            )
            // Missing date2
        )

        val stocks = listOf(
            Stock("AAPL", "Technology", stock1Quotes, emptyList()),
            Stock("MSFT", "Technology", stock2Quotes, emptyList())
        )

        doReturn(stocks).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date1, date2)

        // Then - should only return date1 since date2 doesn't have data for all stocks
        assertEquals(1, result.historicalData.size)

        // Date1: Both stocks have data
        assertEquals(2, result.historicalData[0].totalStocks)
        assertEquals(100.0, result.historicalData[0].bullishPercent)

        // Should have a warning about missing stocks (QQQ has 102 stocks, we only provided 2)
        assertNotNull(result.warning, "Warning should not be null")
        assertTrue(result.warning!!.contains("Missing data for"), "Warning should contain 'Missing data for' but was: ${result.warning}")
        assertTrue(result.warning!!.contains("Please refresh"), "Warning should contain 'Please refresh' but was: ${result.warning}")

        // Should show expected vs actual stock counts (QQQ has 102 stocks but we only mocked 2)
        assertEquals(2, result.actualStockCount)
        assertEquals(102, result.expectedStockCount)
    }

    @Test
    fun `should throw exception for ETF with no configured stocks`() {
        // Given: ETF with no stocks configured (IWM, DIA return empty sets)
        doReturn(emptyList<Stock>()).whenever(stockService).getStocksBySymbols(any(), any())

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            etfStatsService.getEtfStats(
                Etf.IWM,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 15)
            )
        }

        assertTrue(exception.message?.contains("No stocks configured for ETF: IWM") == true)
    }

    @Test
    fun `should return correct ETF metadata`() {
        // Given: Minimal data
        val date = LocalDate.of(2025, 1, 15)
        val quote = StockQuote(
            date = date,
            closePrice = 100.0,
            closePriceEMA10 = 95.0,
            closePriceEMA20 = 90.0,
            closePriceEMA50 = 85.0,
            openPrice = 99.0
        )
        val stock = Stock("AAPL", "Technology", listOf(quote), emptyList())
        doReturn(listOf(stock)).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date, date)

        // Then - Check metadata
        assertEquals("QQQ", result.symbol)
        assertEquals("Nasdaq-100", result.name)
    }

    @Test
    fun `should calculate zero change when only one data point exists`() {
        // Given: Only one day of data
        val date = LocalDate.of(2025, 1, 15)
        val quote = StockQuote(
            date = date,
            closePrice = 110.0,
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 95.0,
            openPrice = 108.0
        )
        val stock = Stock("AAPL", "Technology", listOf(quote), emptyList())
        doReturn(listOf(stock)).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date, date)

        // Then - Change should be 0 (comparing to itself)
        assertEquals(0.0, result.currentStats.change)
        assertEquals(100.0, result.currentStats.bullishPercent)
    }

    @Test
    fun `should handle multiple stocks with different trend states on same date`() {
        // Given: 5 stocks with different states
        val date = LocalDate.of(2025, 1, 15)

        val uptrendQuote1 = StockQuote(
            date = date,
            closePrice = 110.0,
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 95.0,
            openPrice = 108.0
        )
        val uptrendQuote2 = StockQuote(
            date = date,
            closePrice = 120.0,
            closePriceEMA10 = 115.0,
            closePriceEMA20 = 110.0,
            closePriceEMA50 = 105.0,
            openPrice = 118.0
        )
        val downtrendQuote = StockQuote(
            date = date,
            closePrice = 90.0,
            closePriceEMA10 = 95.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 105.0,
            openPrice = 92.0
        )
        val neutralQuote1 = StockQuote(
            date = date,
            closePrice = 95.0,
            closePriceEMA10 = 105.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 110.0,
            openPrice = 96.0
        )
        val neutralQuote2 = StockQuote(
            date = date,
            closePrice = 105.0,
            closePriceEMA10 = 95.0,
            closePriceEMA20 = 100.0,
            closePriceEMA50 = 90.0,
            openPrice = 104.0
        )

        val stocks = listOf(
            Stock("AAPL", "Technology", listOf(uptrendQuote1), emptyList()),
            Stock("MSFT", "Technology", listOf(uptrendQuote2), emptyList()),
            Stock("META", "Technology", listOf(downtrendQuote), emptyList()),
            Stock("GOOGL", "Technology", listOf(neutralQuote1), emptyList()),
            Stock("AMZN", "Technology", listOf(neutralQuote2), emptyList())
        )

        doReturn(stocks).whenever(stockService).getStocksBySymbols(any(), any())

        // When
        val result = etfStatsService.getEtfStats(Etf.QQQ, date, date)

        // Then
        assertEquals(5, result.currentStats.totalStocks)
        assertEquals(2, result.currentStats.stocksInUptrend)
        assertEquals(1, result.currentStats.stocksInDowntrend)
        assertEquals(2, result.currentStats.stocksInNeutral)
        assertEquals(40.0, result.currentStats.bullishPercent) // 2/5 = 40%
        assertFalse(result.currentStats.inUptrend) // < 50%
    }
}
