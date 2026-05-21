package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.OvtlyrSignal
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OvtlyrBuySignalFiredConditionTest {
  private val condition = OvtlyrBuySignalFiredCondition()

  @Test
  fun `evaluate is true on the exact bar an Ovtlyr BUY signal fired`() {
    // Given: a BUY signal fired on May 4
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY)),
      )
    val quote = StockQuote(date = LocalDate.of(2026, 5, 4))

    // When / Then: the call day itself is an entry
    assertTrue(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `evaluate is false on a no-row bar between a BUY call and the next SELL`() {
    // Given: a standing BUY (May 4) with no row on May 12
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals =
          listOf(
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY),
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 20), OvtlyrSignalType.SELL),
          ),
      )
    val quote = StockQuote(date = LocalDate.of(2026, 5, 12))

    // When / Then: no call fired on May 12 — not an entry, despite the standing BUY
    assertFalse(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `evaluate is false on a bar whose Ovtlyr event is a SELL`() {
    // Given: a SELL fired on May 20
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 20), OvtlyrSignalType.SELL)),
      )
    val quote = StockQuote(date = LocalDate.of(2026, 5, 20))

    // When / Then: a SELL event is not a buy entry
    assertFalse(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }
}
