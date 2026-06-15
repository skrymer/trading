package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestContext
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Verifies that the backtest engine only trades names in the *tradable universe* — the realistically
 * fillable opportunity set gated on point-in-time price/liquidity/age (ADR 0026). Two structurally
 * identical names that differ only in dollar-volume must diverge: the liquid one trades, the thin one
 * is skipped. The gate is default-on; `applyLiquidityFilter = false` reproduces the pre-#173 runs.
 */
class BacktestServiceLiquidityFilterTest {
  private lateinit var backtestService: BacktestService
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var sectorBreadthRepository: SectorBreadthRepository
  private lateinit var marketBreadthRepository: MarketBreadthRepository

  // bar 0..251 build the 252-bar history (age gate), bar 251 is the entry decision bar, bar 252 exits.
  private val startDate = LocalDate.of(2008, 1, 1)
  private val entryDate = startDate.plusDays(251)
  private val lastBar = startDate.plusDays(259)

  /** Entry fires once, on the decision bar; exit fires the next bar (close >= 21). Cost off to keep it simple. */
  private val entry =
    object : EntryStrategy {
      override fun description() = "entry on the decision bar"

      override fun test(
        stock: Stock,
        quote: StockQuote,
        context: BacktestContext,
      ) = quote.date == entryDate
    }

  private val exit =
    object : ExitStrategy {
      override fun match(
        stock: Stock,
        entryQuote: StockQuote?,
        quote: StockQuote,
      ) = quote.closePrice >= 21.0

      override fun reason(
        stock: Stock,
        entryQuote: StockQuote?,
        quote: StockQuote,
      ) = "close >= 21"

      override fun description() = "close >= 21"
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

  /** 260 bars at $20 (bar 252 jumps to $21 to trigger the exit), constant [dailyVolume] shares per bar. */
  private fun stockWith(symbol: String, dailyVolume: Long): Stock {
    val quotes =
      (0..259).map { i ->
        val close = if (i == 252) 21.0 else 20.0
        StockQuote(symbol = symbol, date = startDate.plusDays(i.toLong()), closePrice = close, openPrice = close, volume = dailyVolume)
      }
    return Stock(symbol = symbol, sectorSymbol = "XLF", quotes = quotes)
  }

  /**
   * Stubs the repository to truncate each stock's quotes at the requested `quotesAfter`, exactly as the
   * real DB load does. This is load-bearing: the age gate counts bars-as-of the decision bar, so if the
   * engine loaded only from the window start the long-lived LIQUID name would look freshly-listed and be
   * wrongly skipped. The test passes only because the engine loads the minBars liquidity warmup (ADR 0026).
   */
  private fun stubLoad(vararg stocks: Stock) {
    whenever(stockRepository.findBySymbols(any(), anyOrNull())).thenAnswer { invocation ->
      val quotesAfter = invocation.getArgument<LocalDate?>(1)
      stocks.map { stock ->
        if (quotesAfter == null) stock else stock.copy(quotes = stock.quotes.filter { !it.date.isBefore(quotesAfter) })
      }
    }
  }

  @Test
  fun `default-on liquidity filter trades the liquid name and skips the thin one`() {
    // Given: two identical $20 names with >=252 bars, differing only in dollar-volume — LIQUID at
    // $4M/day (200k shares) clears the $1M floor, THIN at $600k/day (30k shares) does not.
    val liquid = stockWith("LIQUID", dailyVolume = 200_000)
    val thin = stockWith("THIN", dailyVolume = 30_000)
    stubLoad(liquid, thin)

    // When: a default backtest (filter on)
    val report =
      backtestService.backtest(
        entryStrategy = entry,
        exitStrategy = exit,
        symbols = listOf("LIQUID", "THIN"),
        after = entryDate,
        before = lastBar,
        costBps = 0.0,
      )

    // Then: only the liquid name is in the tradable universe
    assertEquals(setOf("LIQUID"), report.trades.map { it.stockSymbol }.toSet())
    assertEquals(1, report.totalTrades)
  }

  @Test
  fun `applyLiquidityFilter false reproduces the unfiltered universe — both names trade`() {
    // Given: the same two names, one liquid and one thin
    val liquid = stockWith("LIQUID", dailyVolume = 200_000)
    val thin = stockWith("THIN", dailyVolume = 30_000)
    stubLoad(liquid, thin)

    // When: the liquidity filter is turned off (pre-#173 behaviour)
    val report =
      backtestService.backtest(
        entryStrategy = entry,
        exitStrategy = exit,
        symbols = listOf("LIQUID", "THIN"),
        after = entryDate,
        before = lastBar,
        costBps = 0.0,
        applyLiquidityFilter = false,
      )

    // Then: both names trade — the thin one is no longer gated out
    assertEquals(setOf("LIQUID", "THIN"), report.trades.map { it.stockSymbol }.toSet())
    assertEquals(2, report.totalTrades)
  }
}
