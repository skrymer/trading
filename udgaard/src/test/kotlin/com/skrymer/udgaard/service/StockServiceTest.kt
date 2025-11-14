package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.*

class StockServiceTest() {

  private lateinit var stockService: StockService
  private lateinit var stockRepository: StockRepository
  private lateinit var ovtlyrClient: OvtlyrClient
  private lateinit var marketBreadthRepository: MarketBreadthRepository

  @BeforeEach
  fun setup() {
    stockRepository = mock<StockRepository>()
    ovtlyrClient = mock<OvtlyrClient>()
    marketBreadthRepository = mock<MarketBreadthRepository>()
    stockService = StockService(stockRepository, ovtlyrClient, marketBreadthRepository)
  }

  @Test
  fun `should do something`() {

    // given some stock quotes
    val quote1 = StockQuote(closePrice = 99.9, date = LocalDate.of(2025, 7, 1))
    val quote2 = StockQuote(closePrice = 100.0, date = LocalDate.of(2025, 7, 2))
    val quote3 = StockQuote(closePrice = 100.1, date = LocalDate.of(2025, 7, 3))
    val quote4 = StockQuote(closePrice = 100.2, date = LocalDate.of(2025, 7, 4))
    val quote5 = StockQuote(closePrice = 99.9, date = LocalDate.of(2025, 7, 7))

    val stock = Stock("TEST", "TEST_SECTOR", listOf(quote1, quote2, quote3, quote4, quote5), emptyList())

    val backtestReport =
      stockService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        listOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now()
      )
    println(backtestReport)
  }

  @Test
  fun `should calculate report results`() {
    // given some stock quotes
    val quote1 = StockQuote(closePrice = 99.9, date = LocalDate.of(2025, 7, 1))
    // Trade 1 win 3$ 3%
    val quote2 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 2))
    val quote3 = StockQuote(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 3))
    val quote4 = StockQuote(closePrice = 103.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 4))
    // Trade 2 loss 2$ 2%
    val quote5 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 8))
    val quote6 = StockQuote(closePrice = 101.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 9))
    val quote7 = StockQuote(closePrice = 98.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 10))
    // Trade 3 win 5$ 5%
    val quote8 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 11))
    val quote9 = StockQuote(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 14))
    val quote10 = StockQuote(closePrice = 105.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 15))
    // Trade 4 loss 4$ 4%
    val quote11 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 16))
    val quote12 = StockQuote(closePrice = 101.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 17))
    val quote13 = StockQuote(closePrice = 96.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 18))
    // Trade 5 win 8$ 8%
    val quote14 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 21))
    val quote15 = StockQuote(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 22))
    val quote16 = StockQuote(closePrice = 108.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 23))

    val stock = Stock(
      "TEST",
      "TEST_SECTOR",
      listOf(
        quote1,
        quote2,
        quote3,
        quote4,
        quote5,
        quote6,
        quote7,
        quote8,
        quote9,
        quote10,
        quote11,
        quote12,
        quote13,
        quote14,
        quote15,
        quote16
      ),
      emptyList()
    )

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,
      openPriceIsLessThan100,
      listOf(stock),
      LocalDate.of(2024, 1, 1),
      LocalDate.now()
    )
    Assertions.assertEquals(3, report.numberOfWinningTrades)
    Assertions.assertEquals(0.6, report.winRate)
    Assertions.assertEquals(5.33, report.averageWinPercent, 0.01)
    Assertions.assertEquals(5.33, report.averageWinAmount, 0.01)

    Assertions.assertEquals(2, report.numberOfLosingTrades)
    Assertions.assertEquals(0.4, report.lossRate)
    Assertions.assertEquals(3.0, report.averageLossPercent)
    Assertions.assertEquals(3.0, report.averageLossAmount)

    Assertions.assertEquals(1.99, report.edge, 0.01)
  }

  @Test
  fun `should include trades on boundary dates (inclusive date filtering)`() {
    // Test the fix for inclusive date filtering
    val quote1 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2024, 1, 1))
    val quote2 = StockQuote(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2024, 1, 2))
    val quote3 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2024, 12, 31))
    val quote4 = StockQuote(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 1))

    val stock = Stock("TEST", "XLK", listOf(quote1, quote2, quote3, quote4), emptyList())

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,
      openPriceIsLessThan100,
      listOf(stock),
      LocalDate.of(2024, 1, 1),   // Should include quote1
      LocalDate.of(2024, 12, 31)  // Should include quote3
    )

    // Should have 2 trades (one starting on 2024-01-01, one on 2024-12-31)
    Assertions.assertEquals(2, report.totalTrades)
  }

  @Test
  fun `should handle 100 percent win rate without division by zero`() {
    // Test the division by zero fix when all trades are winners
    val alwaysWin = object : ExitStrategy {
      override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
        quote.closePrice > (entryQuote?.closePrice ?: 0.0)
      override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = "Winner"
      override fun description() = "Always win"
    }

    val quote1 = StockQuote(closePrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuote(closePrice = 105.0, date = LocalDate.of(2025, 1, 2))

    val stock = Stock("TEST", "XLK", listOf(quote1, quote2), emptyList())

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,
      alwaysWin,
      listOf(stock),
      LocalDate.of(2024, 1, 1),
      LocalDate.now()
    )

    Assertions.assertEquals(1, report.numberOfWinningTrades)
    Assertions.assertEquals(0, report.numberOfLosingTrades)
    Assertions.assertEquals(1.0, report.winRate)
    Assertions.assertEquals(0.0, report.lossRate)
    // Should not throw division by zero
    Assertions.assertEquals(0.0, report.averageLossAmount)
    Assertions.assertEquals(0.0, report.averageLossPercent)
  }

  @Test
  fun `should handle 100 percent loss rate without division by zero`() {
    // Test the division by zero fix when all trades are losers
    val alwaysLose = object : ExitStrategy {
      override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
        quote.closePrice < (entryQuote?.closePrice ?: 0.0)
      override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = "Loser"
      override fun description() = "Always lose"
    }

    val quote1 = StockQuote(closePrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuote(closePrice = 95.0, date = LocalDate.of(2025, 1, 2))

    val stock = Stock("TEST", "XLK", listOf(quote1, quote2), emptyList())

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,
      alwaysLose,
      listOf(stock),
      LocalDate.of(2024, 1, 1),
      LocalDate.now()
    )

    Assertions.assertEquals(0, report.numberOfWinningTrades)
    Assertions.assertEquals(1, report.numberOfLosingTrades)
    Assertions.assertEquals(0.0, report.winRate)
    Assertions.assertEquals(1.0, report.lossRate)
    // Should not throw division by zero
    Assertions.assertEquals(0.0, report.averageWinAmount)
    Assertions.assertEquals(0.0, report.averageWinPercent)
  }

  @Test
  fun `should prevent overlapping trades`() {
    // Test that you can't enter a new trade while already in one
    val quote1 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuote(closePrice = 101.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 2))
    val quote3 = StockQuote(closePrice = 102.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 3))

    val stock = Stock("TEST", "XLK", listOf(quote1, quote2, quote3), emptyList())

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,
      openPriceIsLessThan100,
      listOf(stock),
      LocalDate.of(2024, 1, 1),
      LocalDate.now()
    )

    // Should only have 1 trade (enters on quote1, exits on quote3)
    // Should NOT enter on quote2 because still in trade from quote1
    Assertions.assertEquals(1, report.totalTrades)
  }

  val closePriceIsGreaterThanOrEqualTo100 = object : EntryStrategy {
    override fun description() = "Test entry strategy"
    override fun test(stock: Stock, quote: StockQuote) = quote.closePrice >= 100.0
  }

  val openPriceIsLessThan100 = object : ExitStrategy {
    override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = quote.openPrice < 100.0
    override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
      "Because stone cold said so!"

    override fun description() = ""
  }

  // ===== UNDERLYING ASSET TESTS =====

  @Test
  fun `should use underlying asset for strategy evaluation and trading stock for P&L`() {
    // Setup: Create TQQQ (trading stock) and QQQ (underlying/strategy stock)
    // QQQ triggers entry at 100, exits at 105
    // TQQQ starts at 50, ends at 60 (3x leverage simulation)

    val qqqQuote1 = StockQuote(closePrice = 99.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 1))
    val qqqQuote2 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 2))  // Entry trigger
    val qqqQuote3 = StockQuote(closePrice = 103.0, openPrice = 103.0, date = LocalDate.of(2025, 1, 3))
    val qqqQuote4 = StockQuote(closePrice = 105.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 4))  // Exit trigger

    val tqqqQuote1 = StockQuote(closePrice = 47.0, openPrice = 47.0, date = LocalDate.of(2025, 1, 1))
    val tqqqQuote2 = StockQuote(closePrice = 50.0, openPrice = 50.0, date = LocalDate.of(2025, 1, 2))  // Actual entry
    val tqqqQuote3 = StockQuote(closePrice = 55.0, openPrice = 55.0, date = LocalDate.of(2025, 1, 3))
    val tqqqQuote4 = StockQuote(closePrice = 60.0, openPrice = 48.0, date = LocalDate.of(2025, 1, 4))  // Actual exit

    val qqq = Stock("QQQ", "XLK", listOf(qqqQuote1, qqqQuote2, qqqQuote3, qqqQuote4), emptyList())
    val tqqq = Stock("TQQQ", "XLK", listOf(tqqqQuote1, tqqqQuote2, tqqqQuote3, tqqqQuote4), emptyList())

    // Mock repository to return QQQ when requested
    whenever(stockRepository.findById("QQQ")).thenReturn(Optional.of(qqq))

    // Custom map: TQQQ -> QQQ
    val customMap = mapOf("TQQQ" to "QQQ")

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,  // QQQ triggers at 100
      openPriceIsLessThan100,              // QQQ exits when openPrice < 100
      listOf(tqqq),  // Trading TQQQ
      LocalDate.of(2024, 1, 1),
      LocalDate.now(),
      useUnderlyingAssets = true,
      customUnderlyingMap = customMap
    )

    // Verify we got exactly 1 trade
    Assertions.assertEquals(1, report.totalTrades, "Should have 1 trade")

    val trade = report.trades.first()

    // Verify the trade stores both symbols
    Assertions.assertEquals("TQQQ", trade.stockSymbol, "Trade should be for TQQQ")
    Assertions.assertEquals("QQQ", trade.underlyingSymbol, "Trade should show QQQ as underlying")

    // Verify P/L is calculated from TQQQ prices (not QQQ)
    val expectedProfit = 60.0 - 50.0  // TQQQ prices
    Assertions.assertEquals(expectedProfit, trade.profit, 0.01, "Profit should be calculated from TQQQ prices")

    // Verify entry/exit prices are from TQQQ
    Assertions.assertEquals(50.0, trade.entryQuote.closePrice, 0.01, "Entry price should be from TQQQ")
  }

  @Test
  fun `should work without underlying assets when disabled`() {
    // Test that when useUnderlyingAssets = false, it works normally
    val quote1 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuote(closePrice = 105.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))

    val stock = Stock("TQQQ", "XLK", listOf(quote1, quote2), emptyList())

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,
      openPriceIsLessThan100,
      listOf(stock),
      LocalDate.of(2024, 1, 1),
      LocalDate.now(),
      useUnderlyingAssets = false,  // Disabled
      customUnderlyingMap = mapOf("TQQQ" to "QQQ")  // Should be ignored
    )

    Assertions.assertEquals(1, report.totalTrades)
    val trade = report.trades.first()

    // Should NOT have underlying symbol when disabled
    Assertions.assertNull(trade.underlyingSymbol, "Should not use underlying asset when disabled")
    Assertions.assertEquals("TQQQ", trade.stockSymbol)
  }

  @Test
  fun `should throw exception when underlying asset data is missing`() {
    // Test validation: should fail if underlying asset doesn't exist
    val quote1 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuote(closePrice = 105.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))

    val stock = Stock("TQQQ", "XLK", listOf(quote1, quote2), emptyList())

    // Custom map pointing to non-existent stock
    val customMap = mapOf("TQQQ" to "NONEXISTENT")

    val exception = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
      stockService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        listOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        useUnderlyingAssets = true,
        customUnderlyingMap = customMap
      )
    }

    Assertions.assertTrue(
      exception.message?.contains("Missing underlying asset data") == true,
      "Should mention missing underlying asset"
    )
  }

  @Test
  fun `should handle mixed stocks with and without underlying assets`() {
    // Test backtesting multiple stocks where some have underlying and some don't

    // AAPL - no underlying, trades on its own signals
    val aaplQuote1 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val aaplQuote2 = StockQuote(closePrice = 105.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))
    val aapl = Stock("AAPL", "XLK", listOf(aaplQuote1, aaplQuote2), emptyList())

    // TQQQ - has underlying QQQ (but QQQ doesn't meet entry conditions)
    val tqqqQuote1 = StockQuote(closePrice = 50.0, openPrice = 50.0, date = LocalDate.of(2025, 1, 1))
    val tqqqQuote2 = StockQuote(closePrice = 55.0, openPrice = 48.0, date = LocalDate.of(2025, 1, 2))
    val tqqq = Stock("TQQQ", "XLK", listOf(tqqqQuote1, tqqqQuote2), emptyList())

    // QQQ - underlying for TQQQ, doesn't meet entry (closePrice < 100)
    val qqqQuote1 = StockQuote(closePrice = 95.0, openPrice = 95.0, date = LocalDate.of(2025, 1, 1))
    val qqqQuote2 = StockQuote(closePrice = 96.0, openPrice = 94.0, date = LocalDate.of(2025, 1, 2))
    val qqq = Stock("QQQ", "XLK", listOf(qqqQuote1, qqqQuote2), emptyList())

    // Mock repository to return QQQ when requested
    whenever(stockRepository.findById("QQQ")).thenReturn(Optional.of(qqq))

    val customMap = mapOf("TQQQ" to "QQQ")

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,
      openPriceIsLessThan100,
      listOf(aapl, tqqq),  // Only trading AAPL and TQQQ
      LocalDate.of(2024, 1, 1),
      LocalDate.now(),
      useUnderlyingAssets = true,
      customUnderlyingMap = customMap
    )

    // Should have 1 trade (AAPL only, TQQQ doesn't enter because QQQ < 100)
    Assertions.assertEquals(1, report.totalTrades)
    val trade = report.trades.first()
    Assertions.assertEquals("AAPL", trade.stockSymbol)
    Assertions.assertNull(trade.underlyingSymbol, "AAPL should not have underlying")
  }
}