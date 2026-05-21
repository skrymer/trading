package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.data.model.OvtlyrSignal
import com.skrymer.udgaard.data.model.OvtlyrSignalType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OvtlyrSellSignalConditionTest {
  private val condition = OvtlyrSellSignalCondition()

  @Test
  fun `shouldExit is true on a bar with a standing SELL signal`() {
    // Given: a BUY then a SELL — the SELL is the most recent call
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

    // When / Then: the standing SELL state triggers an exit
    assertTrue(condition.shouldExit(stock, entryQuote, quote))
  }

  @Test
  fun `shouldExit is false while a BUY signal still stands`() {
    // Given: a BUY with no SELL since
    val stock =
      Stock(
        symbol = "AAPL",
        ovtlyrSignals = listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 4), OvtlyrSignalType.BUY)),
      )
    val entryQuote = StockQuote(date = LocalDate.of(2026, 5, 6))
    val quote = StockQuote(date = LocalDate.of(2026, 5, 12))

    // When / Then: the standing state is BUY — no exit
    assertFalse(condition.shouldExit(stock, entryQuote, quote))
  }

  @Test
  fun `shouldExit is false when the stock has no Ovtlyr signals`() {
    // Given: a stock with no Ovtlyr coverage
    val stock = Stock(symbol = "AAPL")
    val entryQuote = StockQuote(date = LocalDate.of(2026, 5, 6))
    val quote = StockQuote(date = LocalDate.of(2026, 5, 12))

    // When / Then: no standing signal means no exit
    assertFalse(condition.shouldExit(stock, entryQuote, quote))
  }
}
