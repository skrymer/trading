package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Transaction-cost (costBps) netting at trade close. The engine charges a round-trip
 * cost split evenly across both fills, netted once into the per-share Trade.profit, so
 * every downstream realised-outcome metric inherits the net figure.
 */
class BacktestServiceCostTest {
  private lateinit var backtestService: BacktestService
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var sectorBreadthRepository: SectorBreadthRepository
  private lateinit var marketBreadthRepository: MarketBreadthRepository

  // Enters on the $50 bar; the < 55 guard stops the $55 exit bar from re-triggering entry.
  private val enterAround50 =
    object : EntryStrategy {
      override fun description() = "entry"

      override fun test(stock: Stock, quote: StockQuote, context: BacktestContext) =
        quote.closePrice >= 50.0 && quote.closePrice < 55.0
    }

  private val exitAt55 =
    object : ExitStrategy {
      override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = quote.closePrice >= 55.0

      override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = "exit"

      override fun description() = ""
    }

  // Enters on the $100.00 bar; the < 100.05 guard stops the exit bar from re-triggering entry.
  private val enterAtLeast100 =
    object : EntryStrategy {
      override fun description() = "entry"

      override fun test(stock: Stock, quote: StockQuote, context: BacktestContext) =
        quote.closePrice >= 100.0 && quote.closePrice < 100.05
    }

  private val exitAbove100 =
    object : ExitStrategy {
      override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = quote.closePrice > 100.0

      override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = "exit"

      override fun description() = ""
    }

  // Never matches — forces the delisting force-close path to close the trade.
  private val neverExit =
    object : ExitStrategy {
      override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = false

      override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = "never"

      override fun description() = ""
    }

  @BeforeEach
  fun setup() {
    stockRepository = mock(StockJooqRepository::class.java)
    sectorBreadthRepository = mock(SectorBreadthRepository::class.java)
    marketBreadthRepository = mock(MarketBreadthRepository::class.java)
    whenever(marketBreadthRepository.calculateBreadthByDate()).thenReturn(emptyMap())
    backtestService =
      BacktestService(
        stockRepository,
        sectorBreadthRepository,
        marketBreadthRepository,
        mock(LeadershipRegimeService::class.java),
        mock(RegimeReadoutService::class.java),
      )
  }

  private fun mockStocksForLoading(vararg stocks: Stock) {
    whenever(stockRepository.findBySymbols(any(), any())).thenReturn(stocks.toList())
  }

  @Test
  fun `should net the default round-trip cost into per-share profit`() {
    // Given a single $50 entry that exits at $55 (gross $5.00/share)
    val entry = StockQuote(closePrice = 50.0, openPrice = 50.0, date = LocalDate.of(2025, 1, 1))
    val exit = StockQuote(closePrice = 55.0, openPrice = 55.0, date = LocalDate.of(2025, 1, 2))
    val stock = Stock("TEST", "XLK", quotes = listOf(entry, exit))
    mockStocksForLoading(stock)

    // When backtested with the net-by-default cost (costBps = 10)
    val report =
      backtestService.backtest(
        enterAround50,
        exitAt55,
        listOf("TEST"),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        applyLiquidityFilter = false,
      )

    // Then per-share profit is netted: $5.00 - (50 + 55) * 0.0005 = $4.9475
    Assertions.assertEquals(1, report.totalTrades)
    Assertions.assertEquals(4.9475, report.trades.first().profit, 1e-9)
  }

  @Test
  fun `should leave per-share profit gross when costBps is zero`() {
    // Given the same $50 to $55 trade
    val entry = StockQuote(closePrice = 50.0, openPrice = 50.0, date = LocalDate.of(2025, 1, 1))
    val exit = StockQuote(closePrice = 55.0, openPrice = 55.0, date = LocalDate.of(2025, 1, 2))
    val stock = Stock("TEST", "XLK", quotes = listOf(entry, exit))
    mockStocksForLoading(stock)

    // When backtested with cost disabled
    val report =
      backtestService.backtest(
        enterAround50,
        exitAt55,
        listOf("TEST"),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        costBps = 0.0,
        applyLiquidityFilter = false,
      )

    // Then profit is the untouched gross $5.00/share
    Assertions.assertEquals(1, report.totalTrades)
    Assertions.assertEquals(5.0, report.trades.first().profit, 1e-9)
  }

  @Test
  fun `should net the stress cost when an explicit costBps is supplied`() {
    // Given the same $50 to $55 trade
    val entry = StockQuote(closePrice = 50.0, openPrice = 50.0, date = LocalDate.of(2025, 1, 1))
    val exit = StockQuote(closePrice = 55.0, openPrice = 55.0, date = LocalDate.of(2025, 1, 2))
    val stock = Stock("TEST", "XLK", quotes = listOf(entry, exit))
    mockStocksForLoading(stock)

    // When backtested with the stress cost (costBps = 20)
    val report =
      backtestService.backtest(
        enterAround50,
        exitAt55,
        listOf("TEST"),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        costBps = 20.0,
        applyLiquidityFilter = false,
      )

    // Then the charge doubles: $5.00 - (50 + 55) * 0.001 = $4.895/share
    Assertions.assertEquals(1, report.totalTrades)
    Assertions.assertEquals(4.895, report.trades.first().profit, 1e-9)
  }

  @Test
  fun `should count a thin gross winner eaten by cost as a net loss`() {
    // Given a $100 entry exiting at $100.05 — a $0.05/share gross win, thinner than the
    // round-trip cost of (100 + 100.05) * 0.0005 = $0.100025/share
    val entry = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 1, 1))
    val exit = StockQuote(closePrice = 100.05, openPrice = 100.05, date = LocalDate.of(2025, 1, 2))
    val stock = Stock("TEST", "XLK", quotes = listOf(entry, exit))
    mockStocksForLoading(stock)

    // When backtested with the net-by-default cost (costBps = 10)
    val report =
      backtestService.backtest(
        enterAtLeast100,
        exitAbove100,
        listOf("TEST"),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        applyLiquidityFilter = false,
      )

    // Then the trade is a loss, not a win — friction consumed the gross gain
    Assertions.assertEquals(1, report.totalTrades)
    Assertions.assertEquals(0, report.numberOfWinningTrades)
    Assertions.assertEquals(1, report.numberOfLosingTrades)
    Assertions.assertEquals(0.0, report.winRate, 1e-9)
  }

  @Test
  fun `should move aggregate Edge net-ward versus the same gross run`() {
    // Given three identical $50 to $55 round trips
    val quotes = (1..6).map { day ->
      val close = if (day % 2 == 1) 50.0 else 55.0
      StockQuote(closePrice = close, openPrice = close, date = LocalDate.of(2025, 1, day))
    }
    val stock = Stock("TEST", "XLK", quotes = quotes)
    mockStocksForLoading(stock)

    // When run gross (costBps = 0) and net (costBps = 10) over the same population
    fun runWith(costBps: Double) =
      backtestService.backtest(
        enterAround50,
        exitAt55,
        listOf("TEST"),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        costBps = costBps,
        applyLiquidityFilter = false,
      )

    val gross = runWith(0.0)
    val net = runWith(10.0)

    // Then the same trades are taken, but Edge (a World-2 realised-outcome metric) is lower net
    Assertions.assertEquals(gross.totalTrades, net.totalTrades)
    Assertions.assertEquals(3, net.totalTrades)
    Assertions.assertTrue(
      net.edge < gross.edge,
      "Net Edge ${net.edge} should be below gross Edge ${gross.edge}",
    )
  }

  @Test
  fun `should report the gross-minus-net Edge spread as average round-trip cost in return terms`() {
    // Given a single $50 to $55 trade at the net-by-default cost
    val entry = StockQuote(closePrice = 50.0, openPrice = 50.0, date = LocalDate.of(2025, 1, 1))
    val exit = StockQuote(closePrice = 55.0, openPrice = 55.0, date = LocalDate.of(2025, 1, 2))
    val stock = Stock("TEST", "XLK", quotes = listOf(entry, exit))
    mockStocksForLoading(stock)

    // When backtested with costBps = 10
    val report =
      backtestService.backtest(
        enterAround50,
        exitAt55,
        listOf("TEST"),
        LocalDate.of(2024, 1, 1),
        LocalDate.now(),
        applyLiquidityFilter = false,
      )

    // Then the reported spread is the round-trip cost in return terms:
    // costPerShare $0.0525 / entry $50 * 100 = 0.105%
    Assertions.assertEquals(0.105, report.grossMinusNetEdgeSpread, 1e-9)
  }

  @Test
  fun `should net the round-trip cost on a delisting force-close`() {
    // Given a $100 entry that never hits its exit and force-closes on the last bar at $70
    val entryDay = LocalDate.of(2008, 9, 15)
    val quotes = listOf(
      StockQuote(closePrice = 100.0, openPrice = 100.0, date = entryDay),
      StockQuote(closePrice = 70.0, openPrice = 99.0, date = entryDay.plusDays(1)),
    )
    val stock = Stock("DEAD", "XLF", quotes = quotes, delistingDate = entryDay.plusDays(1))
    mockStocksForLoading(stock)

    // When backtested with the net-by-default cost (costBps = 10)
    val report =
      backtestService.backtest(
        enterAtLeast100,
        neverExit,
        listOf("DEAD"),
        entryDay,
        entryDay.plusDays(10),
        applyLiquidityFilter = false,
      )

    // Then the force-closed loss is netted: -30.0 - (100 + 70) * 0.0005 = -30.085/share
    Assertions.assertEquals(1, report.totalTrades)
    val trade = report.trades.single()
    Assertions.assertEquals(-30.085, trade.profit, 1e-9)
    Assertions.assertEquals(0.085, trade.costPerShare, 1e-9)
  }
}
