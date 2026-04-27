package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that trades surviving the entry strategy don't silently disappear
 * when the trading symbol delists before the strategy's natural exit.
 *
 * Background: `BacktestService.createTradeFromEntry` previously dropped any
 * entry whose trading-stock exit-date quote was missing — typical for
 * delisted symbols whose last bar predates the exit date. That underreported
 * pre-2010 backtest losses (survivorship bias on the loss tail). The force-
 * close path tags the trade with `Trade.EXIT_REASON_DELISTED` and uses the
 * last available bar's close as the exit price.
 */
class BacktestServiceDelistingTest {
  private lateinit var backtestService: BacktestService
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var sectorBreadthRepository: SectorBreadthRepository
  private lateinit var marketBreadthRepository: MarketBreadthRepository

  @BeforeEach
  fun setup() {
    stockRepository = mock(StockJooqRepository::class.java)
    sectorBreadthRepository = mock(SectorBreadthRepository::class.java)
    marketBreadthRepository = mock(MarketBreadthRepository::class.java)
    whenever(marketBreadthRepository.calculateBreadthByDate()).thenReturn(emptyMap())
    backtestService = BacktestService(stockRepository, sectorBreadthRepository, marketBreadthRepository)
  }

  @Test
  fun `force-closes trade on last available bar when symbol delists before strategy exit`() {
    // Given: an entry strategy that fires on the day the close is exactly $100,
    // and an exit strategy that wants closePrice > $110 — never satisfied within
    // this stock's bars. Without the fix, the trade is silently dropped because
    // the strategy's "natural" exit date has no quote (stock delisted).
    val entry =
      object : EntryStrategy {
        override fun description() = "exact close = 100"

        override fun test(
          stock: Stock,
          quote: StockQuote,
          context: com.skrymer.udgaard.backtesting.model.BacktestContext,
        ) = quote.closePrice == 100.0
      }
    val exit =
      object : ExitStrategy {
        override fun match(
          stock: Stock,
          entryQuote: StockQuote?,
          quote: StockQuote,
        ) = quote.closePrice > 110.0

        override fun reason(
          stock: Stock,
          entryQuote: StockQuote?,
          quote: StockQuote,
        ) = "above 110"

        override fun description() = "close > 110"
      }

    // 5 bars: entry day (close=100), then a slow decline. Stock effectively
    // delists on day 5 — no further bars exist to satisfy the exit condition.
    val entryDay = LocalDate.of(2008, 9, 15)
    val quotes =
      listOf(
        StockQuote(closePrice = 100.0, openPrice = 100.0, date = entryDay),
        StockQuote(closePrice = 95.0, openPrice = 99.0, date = entryDay.plusDays(1)),
        StockQuote(closePrice = 90.0, openPrice = 94.0, date = entryDay.plusDays(2)),
        StockQuote(closePrice = 80.0, openPrice = 89.0, date = entryDay.plusDays(3)),
        StockQuote(closePrice = 70.0, openPrice = 79.0, date = entryDay.plusDays(4)),
      )
    val stock = Stock(symbol = "DEAD", sectorSymbol = "XLF", quotes = quotes, delistingDate = entryDay.plusDays(4))
    whenever(stockRepository.findBySymbols(any(), any())).thenReturn(listOf(stock))

    // When
    val report =
      backtestService.backtest(
        entryStrategy = entry,
        exitStrategy = exit,
        symbols = listOf("DEAD"),
        after = entryDay,
        before = entryDay.plusDays(10),
      )

    // Then: the trade exists with the delisted exit reason and a real loss
    assertEquals(1, report.totalTrades, "trade should be force-closed, not dropped")
    val trade = report.trades.single()
    assertEquals(Trade.EXIT_REASON_DELISTED, trade.exitReason)
    // Last available close was 70.0; loss = 70 - 100 = -30
    assertEquals(-30.0, trade.profit, 0.01)
    assertTrue(trade.profit < 0, "delisted-symbol force-close should surface as a real loss")
  }
}
