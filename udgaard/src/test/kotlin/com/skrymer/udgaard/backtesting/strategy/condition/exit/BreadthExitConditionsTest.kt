package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BreadthExitConditionsTest {
  private val today = LocalDate.of(2024, 6, 5)
  private val stock = Stock("AAPL", sectorSymbol = "XLK")
  private val quote = StockQuote(date = today)
  private val entryQuote = StockQuote(date = LocalDate.of(2024, 6, 1))

  // --- MarketBreadthDeterioratingExit ---

  @Test
  fun `market breadth deteriorating triggers when emas inverted`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to MarketBreadthDaily(
          quoteDate = today,
          breadthPercent = 45.0,
          ema5 = 42.0,
          ema10 = 48.0,
          ema20 = 52.0,
        ),
      ),
    )
    assertTrue(MarketBreadthDeterioratingExit().shouldExit(stock, entryQuote, quote, context))
  }

  @Test
  fun `market breadth deteriorating does not trigger when emas aligned`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to MarketBreadthDaily(
          quoteDate = today,
          breadthPercent = 55.0,
          ema5 = 56.0,
          ema10 = 53.0,
          ema20 = 50.0,
        ),
      ),
    )
    assertFalse(MarketBreadthDeterioratingExit().shouldExit(stock, entryQuote, quote, context))
  }

  @Test
  fun `market breadth deteriorating does not trigger with partial inversion`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to MarketBreadthDaily(
          quoteDate = today,
          breadthPercent = 45.0,
          ema5 = 42.0,
          ema10 = 40.0, // ema10 < ema5, not fully inverted
          ema20 = 52.0,
        ),
      ),
    )
    assertFalse(MarketBreadthDeterioratingExit().shouldExit(stock, entryQuote, quote, context))
  }

  // --- SectorBreadthBelowExit ---

  @Test
  fun `sector breadth below triggers when under threshold`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf(
        "XLK" to mapOf(
          today to SectorBreadthDaily(
            sectorSymbol = "XLK",
            quoteDate = today,
            stocksInUptrend = 25,
            stocksInDowntrend = 75,
            totalStocks = 100,
            bullPercentage = 25.0,
          ),
        ),
      ),
      marketBreadthMap = emptyMap(),
    )
    assertTrue(SectorBreadthBelowExit(30.0).shouldExit(stock, entryQuote, quote, context))
  }

  @Test
  fun `sector breadth below does not trigger when above threshold`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf(
        "XLK" to mapOf(
          today to SectorBreadthDaily(
            sectorSymbol = "XLK",
            quoteDate = today,
            stocksInUptrend = 60,
            stocksInDowntrend = 40,
            totalStocks = 100,
            bullPercentage = 60.0,
          ),
        ),
      ),
      marketBreadthMap = emptyMap(),
    )
    assertFalse(SectorBreadthBelowExit(30.0).shouldExit(stock, entryQuote, quote, context))
  }

  // --- Empty context ---

  @Test
  fun `exit conditions return false with empty context`() {
    val emptyContext = BacktestContext.EMPTY
    assertFalse(MarketBreadthDeterioratingExit().shouldExit(stock, entryQuote, quote, emptyContext))
    assertFalse(SectorBreadthBelowExit().shouldExit(stock, entryQuote, quote, emptyContext))
  }
}
