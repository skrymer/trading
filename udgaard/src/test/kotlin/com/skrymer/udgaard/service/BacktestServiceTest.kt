package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.repository.jooq.BreadthJooqRepository
import com.skrymer.udgaard.repository.jooq.StockJooqRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
import java.time.LocalDate

class BacktestServiceTest {
  private val logger = LoggerFactory.getLogger(BacktestServiceTest::class.java)

  private lateinit var backtestService: BacktestService
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var breadthRepository: BreadthJooqRepository

  @BeforeEach
  fun setup() {
    // Create mock repositories
    stockRepository = mock(StockJooqRepository::class.java)
    breadthRepository = mock(BreadthJooqRepository::class.java)

    backtestService = BacktestService(stockRepository, breadthRepository)
  }

  @Test
  fun `should calculate report results`() {
    // given some stock quotes
    val quote1 = StockQuoteDomain(closePrice = 99.9, date = LocalDate.of(2025, 7, 1))
    // Trade 1 win 3$ 3%
    val quote2 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 2))
    val quote3 = StockQuoteDomain(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 3))
    val quote4 = StockQuoteDomain(closePrice = 103.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 4))
    // Trade 2 loss 2$ 2%
    val quote5 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 8))
    val quote6 = StockQuoteDomain(closePrice = 101.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 9))
    val quote7 = StockQuoteDomain(closePrice = 98.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 10))
    // Trade 3 win 5$ 5%
    val quote8 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 11))
    val quote9 = StockQuoteDomain(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 14))
    val quote10 = StockQuoteDomain(closePrice = 105.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 15))
    // Trade 4 loss 4$ 4%
    val quote11 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 16))
    val quote12 = StockQuoteDomain(closePrice = 101.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 17))
    val quote13 = StockQuoteDomain(closePrice = 96.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 18))
    // Trade 5 win 8$ 8%
    val quote14 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 21))
    val quote15 = StockQuoteDomain(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 22))
    val quote16 = StockQuoteDomain(closePrice = 108.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 23))

    val stock =
      StockDomain(
        "TEST",
        "TEST_SECTOR",
        quotes = listOf(
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
          quote16,
        )
      )

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
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
    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2024, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2024, 1, 2))
    val quote3 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2024, 12, 31))
    val quote4 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 1))

    val stock = StockDomain("TEST", "XLK", quotes = listOf(quote1, quote2, quote3, quote4))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1), // Should include quote1
        LocalDate.of(2024, 12, 31), // Should include quote3
      )

    // Should have 2 trades (one starting on 2024-01-01, one on 2024-12-31)
    Assertions.assertEquals(2, report.totalTrades)
  }

  @Test
  fun `should handle 100 percent win rate without division by zero`() {
    // Test the division by zero fix when all trades are winners
    val alwaysWin =
      object : ExitStrategy {
        override fun match(
          stock: StockDomain,
          entryQuote: StockQuoteDomain?,
          quote: StockQuoteDomain,
        ) = quote.closePrice > (entryQuote?.closePrice ?: 0.0)

        override fun reason(
          stock: StockDomain,
          entryQuote: StockQuoteDomain?,
          quote: StockQuoteDomain,
        ) = "Winner"

        override fun description() = "Always win"
      }

    val quote1 = StockQuoteDomain(closePrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 105.0, date = LocalDate.of(2025, 1, 2))

    val stock = StockDomain("TEST", "XLK", quotes = listOf(quote1, quote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        alwaysWin,
        listOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
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
    val alwaysLose =
      object : ExitStrategy {
        override fun match(
          stock: StockDomain,
          entryQuote: StockQuoteDomain?,
          quote: StockQuoteDomain,
        ) = quote.closePrice < (entryQuote?.closePrice ?: 0.0)

        override fun reason(
          stock: StockDomain,
          entryQuote: StockQuoteDomain?,
          quote: StockQuoteDomain,
        ) = "Loser"

        override fun description() = "Always lose"
      }

    val quote1 = StockQuoteDomain(closePrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 95.0, date = LocalDate.of(2025, 1, 2))

    val stock = StockDomain("TEST", "XLK", quotes = listOf(quote1, quote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        alwaysLose,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
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
    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 2))
    val quote3 = StockQuoteDomain(closePrice = 102.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 3))

    val stock = StockDomain("TEST", "XLK", quotes = listOf(quote1, quote2, quote3))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
      )

    // Should only have 1 trade (enters on quote1, exits on quote3)
    // Should NOT enter on quote2 because still in trade from quote1
    Assertions.assertEquals(1, report.totalTrades)
  }

  val closePriceIsGreaterThanOrEqualTo100 =
    object : EntryStrategy {
      override fun description() = "Test entry strategy"

      override fun test(
        stock: StockDomain,
        quote: StockQuoteDomain,
      ) = quote.closePrice >= 100.0
    }

  val openPriceIsLessThan100 =
    object : ExitStrategy {
      override fun match(
        stock: StockDomain,
        entryQuote: StockQuoteDomain?,
        quote: StockQuoteDomain,
      ) = quote.openPrice < 100.0

      override fun reason(
        stock: StockDomain,
        entryQuote: StockQuoteDomain?,
        quote: StockQuoteDomain,
      ) = "Because stone cold said so!"

      override fun description() = ""
    }

  // ===== UNDERLYING ASSET TESTS =====

  @Test
  fun `should use underlying asset for strategy evaluation and trading stock for P&L`() {
    // Setup: Create TQQQ (trading stock) and QQQ (underlying/strategy stock)
    // QQQ triggers entry at 100, exits at 105
    // TQQQ starts at 50, ends at 60 (3x leverage simulation)

    val qqqQuote1 = StockQuoteDomain(closePrice = 99.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 1))
    val qqqQuote2 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 2)) // Entry trigger
    val qqqQuote3 = StockQuoteDomain(closePrice = 103.0, openPrice = 103.0, date = LocalDate.of(2025, 1, 3))
    val qqqQuote4 = StockQuoteDomain(closePrice = 105.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 4)) // Exit trigger

    val tqqqQuote1 = StockQuoteDomain(closePrice = 47.0, openPrice = 47.0, date = LocalDate.of(2025, 1, 1))
    val tqqqQuote2 = StockQuoteDomain(closePrice = 50.0, openPrice = 50.0, date = LocalDate.of(2025, 1, 2)) // Actual entry
    val tqqqQuote3 = StockQuoteDomain(closePrice = 55.0, openPrice = 55.0, date = LocalDate.of(2025, 1, 3))
    val tqqqQuote4 = StockQuoteDomain(closePrice = 60.0, openPrice = 48.0, date = LocalDate.of(2025, 1, 4)) // Actual exit

    val qqq = StockDomain("QQQ", "XLK", quotes = listOf(qqqQuote1, qqqQuote2, qqqQuote3, qqqQuote4))
    val tqqq = StockDomain("TQQQ", "XLK", quotes = listOf(tqqqQuote1, tqqqQuote2, tqqqQuote3, tqqqQuote4))

    // Custom map: TQQQ -> QQQ
    val customMap = mapOf("TQQQ" to "QQQ")

    // Mock the repository to return QQQ when requested (BacktestService will fetch it)
    org.mockito.Mockito
      .`when`(stockRepository.findBySymbol("QQQ"))
      .thenReturn(qqq)

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100, // QQQ triggers at 100
        openPriceIsLessThan100, // QQQ exits when openPrice < 100
        mutableListOf(tqqq), // Only pass TQQQ as trading stock (QQQ will be fetched as underlying)
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        useUnderlyingAssets = true,
        customUnderlyingMap = customMap,
      )

    // Verify we got exactly 1 trade
    Assertions.assertEquals(1, report.totalTrades, "Should have 1 trade")

    val trade = report.trades.first()

    // Verify the trade stores both symbols
    Assertions.assertEquals("TQQQ", trade.stockSymbol, "Trade should be for TQQQ")
    Assertions.assertEquals("QQQ", trade.underlyingSymbol, "Trade should show QQQ as underlying")

    // Verify P/L is calculated from TQQQ prices (not QQQ)
    val expectedProfit = 60.0 - 50.0 // TQQQ prices
    Assertions.assertEquals(expectedProfit, trade.profit, 0.01, "Profit should be calculated from TQQQ prices")

    // Verify entry/exit prices are from TQQQ
    Assertions.assertEquals(50.0, trade.entryQuote.closePrice, 0.01, "Entry price should be from TQQQ")
  }

  @Test
  fun `should work without underlying assets when disabled`() {
    // Test that when useUnderlyingAssets = false, it works normally
    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 105.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))

    val stock = StockDomain("TQQQ", "XLK", quotes = listOf(quote1, quote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        useUnderlyingAssets = false, // Disabled
        customUnderlyingMap = mapOf("TQQQ" to "QQQ"), // Should be ignored
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
    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 105.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))

    val stock = StockDomain("TQQQ", "XLK", quotes = listOf(quote1, quote2))

    // Custom map pointing to non-existent stock
    val customMap = mapOf("TQQQ" to "NONEXISTENT")

    val exception =
      org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
        backtestService.backtest(
          closePriceIsGreaterThanOrEqualTo100,
          openPriceIsLessThan100,
          mutableListOf(stock),
          LocalDate.of(2024, 1, 1),
          LocalDate.now(),
          useUnderlyingAssets = true,
          customUnderlyingMap = customMap,
        )
      }

    Assertions.assertTrue(
      exception.message?.contains("Underlying asset") == true && exception.message?.contains("not found in database") == true,
      "Should mention missing underlying asset",
    )
  }

  @Test
  fun `should handle mixed stocks with and without underlying assets`() {
    // Test backtesting multiple stocks where some have underlying and some don't

    // AAPL - no underlying, trades on its own signals
    val aaplQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val aaplQuote2 = StockQuoteDomain(closePrice = 105.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))
    val aapl = StockDomain("AAPL", "XLK", quotes = listOf(aaplQuote1, aaplQuote2))

    // TQQQ - has underlying QQQ (but QQQ doesn't meet entry conditions)
    val tqqqQuote1 = StockQuoteDomain(closePrice = 50.0, openPrice = 50.0, date = LocalDate.of(2025, 1, 1))
    val tqqqQuote2 = StockQuoteDomain(closePrice = 55.0, openPrice = 48.0, date = LocalDate.of(2025, 1, 2))
    val tqqq = StockDomain("TQQQ", "XLK", quotes = listOf(tqqqQuote1, tqqqQuote2))

    // QQQ - underlying for TQQQ, doesn't meet entry (closePrice < 100)
    val qqqQuote1 = StockQuoteDomain(closePrice = 95.0, openPrice = 95.0, date = LocalDate.of(2025, 1, 1))
    val qqqQuote2 = StockQuoteDomain(closePrice = 96.0, openPrice = 94.0, date = LocalDate.of(2025, 1, 2))
    val qqq = StockDomain("QQQ", "XLK", quotes = listOf(qqqQuote1, qqqQuote2))

    val customMap = mapOf("TQQQ" to "QQQ")

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(aapl, tqqq, qqq), // Include all stocks: AAPL, TQQQ, and underlying QQQ
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        useUnderlyingAssets = true,
        customUnderlyingMap = customMap,
      )

    // Should have 1 trade (AAPL only, TQQQ doesn't enter because QQQ < 100)
    Assertions.assertEquals(1, report.totalTrades)
    val trade = report.trades.first()
    Assertions.assertEquals("AAPL", trade.stockSymbol)
    Assertions.assertNull(trade.underlyingSymbol, "AAPL should not have underlying")
  }

  // ===== COOLDOWN PERIOD TESTS =====

  @Test
  fun `should allow immediate re-entry when cooldown is disabled (0 days)`() {
    // Test that with cooldown = 0, stocks can be re-entered immediately after exit

    // Entry condition: closePrice >= 100
    // Exit condition: openPrice < 100
    // Timeline:
    // Day 1: Entry (closePrice = 100)
    // Day 2: Exit (openPrice = 99)
    // Day 3: Entry again (closePrice = 100) - should be allowed with cooldown = 0
    // Day 4: Exit again (openPrice = 99)

    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))
    val quote3 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 3))
    val quote4 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 4))

    val stock = StockDomain("TEST", "XLK", quotes = listOf(quote1, quote2, quote3, quote4))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        cooldownDays = 0, // Cooldown disabled
      )

    // Should have 2 trades (both entries allowed)
    Assertions.assertEquals(2, report.totalTrades, "Should allow immediate re-entry with cooldown disabled")

    // Verify both trades are from the same stock
    Assertions.assertEquals("TEST", report.trades[0].stockSymbol)
    Assertions.assertEquals("TEST", report.trades[1].stockSymbol)
  }

  @Test
  fun `should block re-entry when within cooldown period`() {
    // Test that cooldown blocks re-entry within the specified period

    // Timeline with 5 TRADING day cooldown (not inclusive):
    // Jan 1: Entry (trading day 0)
    // Jan 2: Exit (trading day 1 - cooldown starts)
    // Jan 3: Would re-enter but BLOCKED (trading day 2 - only 1 trading day since exit)
    // Jan 4: Would re-enter but BLOCKED (trading day 3 - only 2 trading days since exit)
    // Jan 5: BLOCKED (trading day 4 - only 3 trading days)
    // Jan 6: BLOCKED (trading day 5 - only 4 trading days)
    // Jan 7: BLOCKED (trading day 6 - only 5 trading days, not inclusive)
    // Jan 8: Entry ALLOWED (trading day 7 - 6 trading days since Jan 2, more than 5)
    // Jan 9: Exit

    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))
    val quote3 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 3))
    val quote4 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 4))
    val quote5 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 5))
    val quote6 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 6))
    val quote7 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 7))
    val quote8 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 8))
    val quote9 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 9))

    val stock =
      StockDomain("TEST", "XLK", quotes = listOf(quote1, quote2, quote3, quote4, quote5, quote6, quote7, quote8, quote9))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        cooldownDays = 5, // 5 TRADING day cooldown (not inclusive)
      )

    // Should have 2 trades:
    // Trade 1: Entry Jan 1, Exit Jan 2
    // Trade 2: Entry Jan 8 (6 trading days after Jan 2, more than 5), Exit Jan 9
    Assertions.assertEquals(2, report.totalTrades, "Should block re-entry during cooldown period")

    val trade1 = report.trades[0]
    val trade2 = report.trades[1]

    Assertions.assertEquals(LocalDate.of(2025, 1, 1), trade1.entryQuote.date)
    Assertions.assertEquals(
      LocalDate.of(2025, 1, 8),
      trade2.entryQuote.date,
      "Second entry should be on day 8, which is 6 trading days (more than 5) after Jan 2 exit",
    )
  }

  @Test
  fun `should allow re-entry after cooldown period expires`() {
    // Test that cooldown allows re-entry exactly when the period expires

    // Timeline with 3-day cooldown (not inclusive):
    // Day 1: Entry
    // Day 2: Exit
    // Day 3: BLOCKED (1 day since exit)
    // Day 4: BLOCKED (2 days since exit)
    // Day 5: BLOCKED (3 days since exit, not inclusive)
    // Day 6: ALLOWED (4 days since exit, more than 3, cooldown expired)

    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))
    val quote3 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 3))
    val quote4 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 4))
    val quote5 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 5))
    val quote6 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 6))
    val quote7 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 7))

    val stock = StockDomain("TEST", "XLK", quotes = listOf(quote1, quote2, quote3, quote4, quote5, quote6, quote7))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        cooldownDays = 3,
      )

    Assertions.assertEquals(2, report.totalTrades)

    val trade2 = report.trades[1]
    Assertions.assertEquals(
      LocalDate.of(2025, 1, 6),
      trade2.entryQuote.date,
      "Should allow re-entry 4 days after exit (more than 3, not inclusive)",
    )
  }

  @Test
  fun `should enforce global cooldown blocking all stocks after any exit`() {
    // Test that cooldown is GLOBAL - after ANY exit, ALL entries are blocked

    // Timeline with 3 TRADING day cooldown (not inclusive):
    // Trading day 0 (Jan 1): Stock A entry (no previous exits, allowed)
    // Trading day 1 (Jan 2): Stock A exit (global cooldown starts)
    // Trading day 2 (Jan 3): Stock B would enter but BLOCKED (only 1 trading day since exit)
    // Trading day 3 (Jan 4): Stock B would enter but BLOCKED (only 2 trading days)
    // Trading day 4 (Jan 5): Stock B would enter but BLOCKED (only 3 trading days, not inclusive)
    // Trading day 5 (Jan 6): Stock B entry ALLOWED (4 trading days since Jan 2 exit, more than 3)
    // Trading day 6 (Jan 7): Stock B exit (new global cooldown starts)
    // Trading day 7 (Jan 8): Stock A would re-enter but BLOCKED (only 1 trading day since Jan 7)
    // Trading day 8 (Jan 9): BLOCKED (only 2 trading days)
    // Trading day 9 (Jan 10): BLOCKED (only 3 trading days, not inclusive)
    // Trading day 10 (Jan 11): Stock A re-entry ALLOWED (4 trading days since Jan 7 exit, more than 3)

    val stockAQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val stockAQuote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))
    val stockAQuote3 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 8))
    val stockAQuote4 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 9))
    val stockAQuote5 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 10))
    val stockAQuote6 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 11))
    val stockAQuote7 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 12))

    val stockBQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 3))
    val stockBQuote2 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 4))
    val stockBQuote3 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 5))
    val stockBQuote4 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 6))
    val stockBQuote5 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 7))

    val stockA =
      StockDomain(
        "STOCK_A",
        "XLK",
        quotes = listOf(stockAQuote1, stockAQuote2, stockAQuote3, stockAQuote4, stockAQuote5, stockAQuote6, stockAQuote7),
      )
    val stockB =
      StockDomain("STOCK_B", "XLK", quotes = listOf(stockBQuote1, stockBQuote2, stockBQuote3, stockBQuote4, stockBQuote5))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stockA, stockB),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        cooldownDays = 3,
      )

    // Total should be 3 trades:
    // Stock A: Jan 1-2 (first entry, no cooldown)
    // Stock B: Jan 6-7 (4 trading days after Stock A exit, more than 3)
    // Stock A: Jan 11-12 (4 trading days after Stock B exit, more than 3)
    Assertions.assertEquals(3, report.totalTrades, "Should have 3 trades total with global cooldown")

    val stockATrades = report.trades.filter { it.stockSymbol == "STOCK_A" }
    Assertions.assertEquals(2, stockATrades.size, "Stock A should have 2 trades")
    Assertions.assertEquals(LocalDate.of(2025, 1, 1), stockATrades[0].entryQuote.date)
    Assertions.assertEquals(LocalDate.of(2025, 1, 11), stockATrades[1].entryQuote.date)

    val stockBTrades = report.trades.filter { it.stockSymbol == "STOCK_B" }
    Assertions.assertEquals(1, stockBTrades.size, "Stock B should have 1 trade")
    Assertions.assertEquals(LocalDate.of(2025, 1, 6), stockBTrades[0].entryQuote.date)
  }

  @Test
  fun `should handle multiple exits and track most recent for cooldown`() {
    // Test that cooldown tracks the most recent exit, not the first exit

    // Timeline with 5 TRADING day cooldown (not inclusive, using consecutive dates):
    // Trading day 0 (Jan 1): Entry 1
    // Trading day 1 (Jan 2): Exit 1 (cooldown starts)
    // Trading days 2-6: BLOCKED (Jan 3-7, only 1-5 trading days since exit, not inclusive)
    // Trading day 7 (Jan 8): Entry 2 ALLOWED (6 trading days since Jan 2, more than 5)
    // Trading day 8 (Jan 9): Exit 2 (new cooldown starts)
    // Trading days 9-13: BLOCKED (Jan 10-14, only 1-5 trading days since exit, not inclusive)
    // Trading day 14 (Jan 15): Entry 3 ALLOWED (6 trading days since Jan 9, more than 5)
    // Trading day 15 (Jan 16): Exit 3

    // Create quotes for all consecutive days
    val quotes =
      (1..17).map { day ->
        val date = LocalDate.of(2025, 1, day)
        when {
          day == 2 || day == 9 || day == 16 -> StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = date) // Exit days
          else -> StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = date) // Entry days
        }
      }

    val stock = StockDomain("TEST", "XLK", quotes = quotes.toList())

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        cooldownDays = 5,
      )

    // Should have 3 trades
    Assertions.assertEquals(3, report.totalTrades, "Should track most recent exit for cooldown")

    Assertions.assertEquals(LocalDate.of(2025, 1, 1), report.trades[0].entryQuote.date)
    Assertions.assertEquals(
      LocalDate.of(2025, 1, 2),
      report.trades[0]
        .quotes
        .last()
        .date,
      "First exit on Jan 2",
    )
    Assertions.assertEquals(
      LocalDate.of(2025, 1, 8),
      report.trades[1].entryQuote.date,
      "Second entry 6 trading days after first exit (more than 5)",
    )
    Assertions.assertEquals(
      LocalDate.of(2025, 1, 9),
      report.trades[1]
        .quotes
        .last()
        .date,
      "Second exit on Jan 9",
    )
    Assertions.assertEquals(
      LocalDate.of(2025, 1, 15),
      report.trades[2].entryQuote.date,
      "Third entry 6 trading days after second exit (more than 5)",
    )
    Assertions.assertEquals(
      LocalDate.of(2025, 1, 16),
      report.trades[2]
        .quotes
        .last()
        .date,
      "Third exit on Jan 16",
    )
  }

  @Test
  fun `should work with cooldown and position limiting together`() {
    // Test that global cooldown works correctly with position limiting

    // Two stocks with 5 TRADING day cooldown (not inclusive) and max 1 position:
    // Note: Lower heatmap = better score (HeatmapRanker uses 100 - heatmap)
    // Trading day 0 (Jan 1): Both trigger, Stock A wins (heatmap 20 beats 60), Stock B blocked by position limit
    // Trading day 1 (Jan 2): Stock A exits (global cooldown starts)
    // Trading days 2-6 (Jan 3-7): Both BLOCKED by cooldown (only 1-5 trading days since exit, not inclusive)
    // Trading day 7 (Jan 8): Both can enter (6 trading days passed, more than 5), Stock A wins again (better heatmap)
    // Trading day 8 (Jan 9): Stock A exits

    val stockAQuotes =
      mutableListOf(
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 1)),
        StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 2)),
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 3)),
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 4)),
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 5)),
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 6)),
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 7)),
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 8)),
        StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 9)),
      )

    val stockBQuotes =
      mutableListOf(
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 60.0, date = LocalDate.of(2025, 1, 1)),
        StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 60.0, date = LocalDate.of(2025, 1, 8)),
      )

    val stockA = StockDomain("STOCK_A", "XLK", quotes = stockAQuotes.toMutableList())
    val stockB = StockDomain("STOCK_B", "XLK", quotes = stockBQuotes.toMutableList())

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stockA, stockB),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        maxPositions = 1, // Only 1 position at a time
        cooldownDays = 5,
      )

    // Should have 2 trades (both Stock A due to better heatmap):
    // Trade 1: Stock A (Jan 1-2) - wins position limit on trading day 0
    // Trade 2: Stock A (Jan 8-9) - wins position limit on trading day 7 (6 trading days after exit, more than 5)
    Assertions.assertEquals(2, report.totalTrades)
    Assertions.assertEquals("STOCK_A", report.trades[0].stockSymbol)
    Assertions.assertEquals(LocalDate.of(2025, 1, 1), report.trades[0].entryQuote.date)
    Assertions.assertEquals("STOCK_A", report.trades[1].stockSymbol)
    Assertions.assertEquals(LocalDate.of(2025, 1, 8), report.trades[1].entryQuote.date)
  }

  // ===== POSITION LIMIT (OPEN POSITIONS) TESTS =====

  @Test
  fun `should limit total simultaneous open positions across multiple stocks`() {
    // Test that maxPositions counts currently open positions, not just per-day entries
    // With maxPositions=1, if Stock A is still open, Stock B cannot enter

    // Stock A: long-running trade (Jan 1 entry, Jan 10 exit)
    val stockAQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val stockAQuote2 = StockQuoteDomain(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 2))
    val stockAQuote3 = StockQuoteDomain(closePrice = 103.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 3))
    val stockAQuote4 = StockQuoteDomain(closePrice = 104.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 6))
    val stockAQuote5 = StockQuoteDomain(closePrice = 105.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 7))
    val stockAQuote6 = StockQuoteDomain(closePrice = 106.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 8))
    val stockAQuote7 = StockQuoteDomain(closePrice = 107.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 9))
    val stockAQuote8 = StockQuoteDomain(closePrice = 108.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 10)) // Exit

    // Stock B: tries to enter on Jan 3 while Stock A is still open
    val stockBQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 3))
    val stockBQuote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 6))

    val stockA =
      StockDomain(
        "STOCK_A",
        "XLK",
        quotes = listOf(stockAQuote1, stockAQuote2, stockAQuote3, stockAQuote4, stockAQuote5, stockAQuote6, stockAQuote7, stockAQuote8),
      )
    val stockB = StockDomain("STOCK_B", "XLF", quotes = listOf(stockBQuote1, stockBQuote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stockA, stockB),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        maxPositions = 1,
      )

    // Only Stock A should trade - Stock B is blocked because Stock A is still open on Jan 3
    Assertions.assertEquals(1, report.totalTrades, "Should only allow 1 simultaneous position")
    Assertions.assertEquals("STOCK_A", report.trades[0].stockSymbol)
  }

  @Test
  fun `should allow new entry after open position exits when using maxPositions`() {
    // Test that when an open position exits, the slot becomes available

    // Stock A: short trade (Jan 1 entry, Jan 2 exit)
    val stockAQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val stockAQuote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2)) // Exit

    // Stock B: enters on Jan 3 after Stock A has exited
    val stockBQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 3))
    val stockBQuote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 6))

    val stockA = StockDomain("STOCK_A", "XLK", quotes = listOf(stockAQuote1, stockAQuote2))
    val stockB = StockDomain("STOCK_B", "XLF", quotes = listOf(stockBQuote1, stockBQuote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stockA, stockB),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        maxPositions = 1,
      )

    // Both trades should execute - Stock A exits before Stock B enters
    Assertions.assertEquals(2, report.totalTrades, "Should allow new entry after previous position exits")
    Assertions.assertEquals("STOCK_A", report.trades[0].stockSymbol)
    Assertions.assertEquals("STOCK_B", report.trades[1].stockSymbol)
  }

  @Test
  fun `should allow multiple simultaneous positions up to maxPositions limit`() {
    // Test maxPositions=2: two stocks can be open simultaneously, third is blocked
    // Note: closePrice < 100 on holding days prevents re-triggering entry (avoids wasting ranking slots)

    // Stock A: Jan 1 entry, Jan 8 exit (long trade)
    val stockAQuote1 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 100.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 1))
    val stockAQuote2 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 99.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 2))
    val stockAQuote3 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 99.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 3))
    val stockAQuote4 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 99.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 6))
    val stockAQuote5 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 99.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 7))
    val stockAQuote6 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 99.0, openPrice = 99.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 8)) // Exit

    // Stock B: Jan 2 entry, Jan 7 exit (long trade)
    val stockBQuote1 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 2))
    val stockBQuote2 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 99.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 3))
    val stockBQuote3 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 99.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 6))
    val stockBQuote4 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 99.0, openPrice = 99.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 7)) // Exit

    // Stock C: Jan 3 entry - should be BLOCKED because A and B are both open (2 positions filled)
    val stockCQuote1 = StockQuoteDomain(symbol = "STOCK_C", closePrice = 100.0, openPrice = 100.0, heatmap = 30.0, date = LocalDate.of(2025, 1, 3))
    val stockCQuote2 = StockQuoteDomain(symbol = "STOCK_C", closePrice = 99.0, openPrice = 99.0, heatmap = 30.0, date = LocalDate.of(2025, 1, 6))

    val stockA =
      StockDomain(
        "STOCK_A",
        "XLK",
        quotes = listOf(stockAQuote1, stockAQuote2, stockAQuote3, stockAQuote4, stockAQuote5, stockAQuote6),
      )
    val stockB =
      StockDomain("STOCK_B", "XLF", quotes = listOf(stockBQuote1, stockBQuote2, stockBQuote3, stockBQuote4))
    val stockC =
      StockDomain("STOCK_C", "XLE", quotes = listOf(stockCQuote1, stockCQuote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stockA, stockB, stockC),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        maxPositions = 2,
      )

    // Stock A and B should trade, Stock C should be blocked on Jan 3 (2 positions already open)
    Assertions.assertEquals(2, report.totalTrades, "Should allow exactly maxPositions=2 simultaneous positions")
    val tradeSymbols = report.trades.map { it.stockSymbol }.toSet()
    Assertions.assertTrue(tradeSymbols.contains("STOCK_A"), "Stock A should have a trade")
    Assertions.assertTrue(tradeSymbols.contains("STOCK_B"), "Stock B should have a trade")
  }

  @Test
  fun `should have unlimited positions when maxPositions is null`() {
    // Test that maxPositions=null allows any number of simultaneous positions
    // Note: each stock's quotes must have unique `symbol` field to avoid false containsQuote matches

    // Three stocks all entering on Jan 1
    val stockAQuote1 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val stockAQuote2 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))

    val stockBQuote1 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val stockBQuote2 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))

    val stockCQuote1 = StockQuoteDomain(symbol = "STOCK_C", closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val stockCQuote2 = StockQuoteDomain(symbol = "STOCK_C", closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))

    val stockA = StockDomain("STOCK_A", "XLK", quotes = listOf(stockAQuote1, stockAQuote2))
    val stockB = StockDomain("STOCK_B", "XLF", quotes = listOf(stockBQuote1, stockBQuote2))
    val stockC = StockDomain("STOCK_C", "XLE", quotes = listOf(stockCQuote1, stockCQuote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stockA, stockB, stockC),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        maxPositions = null, // Unlimited
      )

    // All 3 stocks should trade simultaneously
    Assertions.assertEquals(3, report.totalTrades, "Should allow unlimited positions when maxPositions is null")
  }

  @Test
  fun `should fill freed slot when position exits mid-backtest with maxPositions`() {
    // Test that after a position exits, the freed slot is available for new entries
    // Note: closePrice < 100 on holding days prevents re-triggering entry

    // Stock A: short trade (Jan 1 entry, Jan 2 exit)
    val stockAQuote1 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 100.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 1))
    val stockAQuote2 = StockQuoteDomain(symbol = "STOCK_A", closePrice = 99.0, openPrice = 99.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 2)) // Exit

    // Stock B: long trade (Jan 1 entry, Jan 8 exit)
    val stockBQuote1 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 100.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 1))
    val stockBQuote2 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 99.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 2))
    val stockBQuote3 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 99.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 3))
    val stockBQuote4 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 99.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 6))
    val stockBQuote5 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 99.0, openPrice = 100.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 7))
    val stockBQuote6 = StockQuoteDomain(symbol = "STOCK_B", closePrice = 99.0, openPrice = 99.0, heatmap = 20.0, date = LocalDate.of(2025, 1, 8)) // Exit

    // Stock C: enters on Jan 3 after Stock A exits on Jan 2, filling the freed slot
    val stockCQuote1 = StockQuoteDomain(symbol = "STOCK_C", closePrice = 100.0, openPrice = 100.0, heatmap = 30.0, date = LocalDate.of(2025, 1, 3))
    val stockCQuote2 = StockQuoteDomain(symbol = "STOCK_C", closePrice = 99.0, openPrice = 99.0, heatmap = 30.0, date = LocalDate.of(2025, 1, 6))

    val stockA = StockDomain("STOCK_A", "XLK", quotes = listOf(stockAQuote1, stockAQuote2))
    val stockB =
      StockDomain(
        "STOCK_B",
        "XLF",
        quotes = listOf(stockBQuote1, stockBQuote2, stockBQuote3, stockBQuote4, stockBQuote5, stockBQuote6),
      )
    val stockC = StockDomain("STOCK_C", "XLE", quotes = listOf(stockCQuote1, stockCQuote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stockA, stockB, stockC),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        maxPositions = 2,
      )

    // All 3 stocks should trade:
    // Jan 1: A and B enter (2 positions)
    // Jan 2: A exits (1 position open: B)
    // Jan 3: C enters in freed slot (2 positions: B + C)
    Assertions.assertEquals(3, report.totalTrades, "Should fill freed slot after position exits")
    val tradeSymbols = report.trades.map { it.stockSymbol }.toSet()
    Assertions.assertTrue(tradeSymbols.contains("STOCK_A"), "Stock A should have traded")
    Assertions.assertTrue(tradeSymbols.contains("STOCK_B"), "Stock B should have traded")
    Assertions.assertTrue(tradeSymbols.contains("STOCK_C"), "Stock C should have entered in freed slot")
  }

  @Test
  fun `should track missed trades when blocked by open position limit`() {
    // Test that entries blocked by position limit are tracked as missed trades

    // Stock A: long trade (Jan 1 entry, Jan 6 exit) - wins ranking due to lower heatmap
    val stockAQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 1))
    val stockAQuote2 = StockQuoteDomain(closePrice = 102.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 2))
    val stockAQuote3 = StockQuoteDomain(closePrice = 103.0, openPrice = 100.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 3))
    val stockAQuote4 = StockQuoteDomain(closePrice = 104.0, openPrice = 99.0, heatmap = 10.0, date = LocalDate.of(2025, 1, 6)) // Exit

    // Stock B: triggers on Jan 2, but blocked because A is open and maxPositions=1
    val stockBQuote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, heatmap = 50.0, date = LocalDate.of(2025, 1, 2))
    val stockBQuote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, heatmap = 50.0, date = LocalDate.of(2025, 1, 3))

    val stockA =
      StockDomain("STOCK_A", "XLK", quotes = listOf(stockAQuote1, stockAQuote2, stockAQuote3, stockAQuote4))
    val stockB = StockDomain("STOCK_B", "XLF", quotes = listOf(stockBQuote1, stockBQuote2))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stockA, stockB),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        maxPositions = 1,
      )

    // Stock A trades, Stock B is missed
    Assertions.assertEquals(1, report.totalTrades, "Only 1 active trade due to position limit")
    Assertions.assertEquals("STOCK_A", report.trades[0].stockSymbol)
    Assertions.assertTrue(report.missedOpportunitiesCount > 0, "Should track missed trades due to position limit")
  }

  @Test
  fun `should not apply cooldown to first entry of a stock`() {
    // Test that cooldown only applies after an exit has occurred

    // Timeline:
    // Day 1: First entry for stock (no previous exits, should always be allowed)
    // Day 2: Exit
    // Day 3: Re-entry blocked by cooldown

    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val quote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 2))
    val quote3 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 3))

    val stock = StockDomain("TEST", "XLK", quotes = mutableListOf(quote1, quote2, quote3))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        cooldownDays = 10, // Long cooldown
      )

    // Should have 1 trade (first entry allowed, second blocked by cooldown)
    Assertions.assertEquals(1, report.totalTrades, "First entry should always be allowed regardless of cooldown")
    Assertions.assertEquals(LocalDate.of(2025, 1, 1), report.trades[0].entryQuote.date)
  }

  @Test
  fun `should count trading days not calendar days for cooldown`() {
    // Test that cooldown counts TRADING days, not calendar days
    // This test uses dates with gaps to simulate weekends/holidays

    // Timeline (5 trading day cooldown, not inclusive):
    // Mon Jan 6: Entry
    // Tue Jan 7: Exit (cooldown starts - need more than 5 TRADING days)
    // Wed Jan 8: Trading day 1 since exit - BLOCKED
    // Thu Jan 9: Trading day 2 since exit - BLOCKED
    // Fri Jan 10: Trading day 3 since exit - BLOCKED
    // [Sat Jan 11, Sun Jan 12: Weekend - NOT trading days]
    // Mon Jan 13: Trading day 4 since exit - BLOCKED
    // Tue Jan 14: Trading day 5 since exit - BLOCKED (not inclusive)
    // Wed Jan 15: Trading day 6 since exit - ALLOWED (more than 5 trading days)
    // Note: Jan 15 is 8 CALENDAR days but 6 TRADING days since Jan 7 exit

    val quote1 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 6)) // Mon
    val quote2 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 7)) // Tue - Exit
    val quote3 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 8)) // Wed - Day 1, blocked
    val quote4 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 9)) // Thu - Day 2, blocked
    val quote5 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 10)) // Fri - Day 3, blocked
    // Weekend: No quotes for Jan 11, 12
    val quote6 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 13)) // Mon - Day 4, blocked
    val quote7 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 14)) // Tue - Day 5, BLOCKED (not inclusive)
    val quote8 = StockQuoteDomain(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 15)) // Wed - Day 6, ALLOWED
    val quote9 = StockQuoteDomain(closePrice = 101.0, openPrice = 99.0, date = LocalDate.of(2025, 1, 16)) // Thu - Exit

    val stock =
      StockDomain("TEST", "XLK", quotes = mutableListOf(quote1, quote2, quote3, quote4, quote5, quote6, quote7, quote8, quote9))

    val report =
      backtestService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        mutableListOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        cooldownDays = 5, // 5 TRADING days (not inclusive)
      )

    // Should have 2 trades
    Assertions.assertEquals(2, report.totalTrades, "Should count trading days, not calendar days")

    val trade1 = report.trades[0]
    val trade2 = report.trades[1]

    Assertions.assertEquals(LocalDate.of(2025, 1, 6), trade1.entryQuote.date, "First entry")
    Assertions.assertEquals(LocalDate.of(2025, 1, 7), trade1.quotes.last().date, "First exit")

    // Second entry should be on Jan 15 (6 trading days after Jan 7 exit, more than 5)
    // Even though it's 8 calendar days, the weekend doesn't count
    Assertions.assertEquals(
      LocalDate.of(2025, 1, 15),
      trade2.entryQuote.date,
      "Second entry should be 6 trading days after exit (more than 5, not inclusive)",
    )
  }
}
