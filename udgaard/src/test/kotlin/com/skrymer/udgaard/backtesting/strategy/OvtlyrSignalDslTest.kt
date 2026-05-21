package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.OvtlyrSignal
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OvtlyrSignalDslTest {
  @Test
  fun `entryStrategy ovtlyrBuySignal matches a stock with a standing BUY`() {
    // Given: a stock with a standing Ovtlyr BUY signal
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY)),
      )
    val quote = StockQuote(date = LocalDate.of(2026, 5, 12))

    // When: a strategy is built via the DSL builder method
    val strategy = entryStrategy { ovtlyrBuySignal() }

    // Then: the strategy matches the buy bar
    assertTrue(strategy.test(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `entryStrategy ovtlyrBuySignalFired matches a stock only on its BUY event bar`() {
    // Given: a stock whose Ovtlyr BUY signal fired on May 4
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY)),
      )

    // When: a strategy is built via the DSL builder method
    val strategy = entryStrategy { ovtlyrBuySignalFired() }

    // Then: it matches the call-day bar but not a later standing-BUY bar
    assertTrue(strategy.test(stock, StockQuote(date = LocalDate.of(2026, 5, 4)), BacktestContext.EMPTY))
    assertFalse(strategy.test(stock, StockQuote(date = LocalDate.of(2026, 5, 12)), BacktestContext.EMPTY))
  }

  @Test
  fun `exitStrategy ovtlyrSellSignal matches a stock with a standing SELL`() {
    // Given: a stock whose most recent Ovtlyr call is a SELL
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals =
          listOf(
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY),
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 20), OvtlyrSignalType.SELL),
          ),
      )
    val entryQuote = StockQuote(date = LocalDate.of(2026, 5, 6))
    val quote = StockQuote(date = LocalDate.of(2026, 5, 25))

    // When: an exit strategy is built via the DSL builder method
    val strategy = exitStrategy { ovtlyrSellSignal() }

    // Then: the strategy triggers an exit on the sell bar
    assertTrue(strategy.match(stock, entryQuote, quote))
  }
}
