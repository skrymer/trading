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

class OvtlyrBuySignalConditionTest {
  private val condition = OvtlyrBuySignalCondition()

  @Test
  fun `evaluate is true on a bar with a standing BUY signal`() {
    // Given: a BUY signal fired on May 4, no SELL since
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY)),
      )
    val quote = StockQuote(date = LocalDate.of(2026, 5, 12))

    // When / Then: a later bar still reads as a buy
    assertTrue(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `evaluate is false once a SELL signal supersedes the BUY`() {
    // Given: a BUY then a later SELL
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals =
          listOf(
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY),
            OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 20), OvtlyrSignalType.SELL),
          ),
      )
    val quote = StockQuote(date = LocalDate.of(2026, 5, 25))

    // When / Then: the standing state is SELL, so this is not a buy
    assertFalse(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `evaluate is false when the stock has no Ovtlyr signals`() {
    // Given: a stock with no Ovtlyr coverage
    val stock = Stock(symbol = "AAPL")
    val quote = StockQuote(date = LocalDate.of(2026, 5, 12))

    // When / Then: no standing signal means no buy
    assertFalse(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }
}
