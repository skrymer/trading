package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class ScriptPredicateCompilerTest {
  private val compiler = ScriptPredicateCompiler()

  private fun quoteAt(close: Double) =
    StockQuote(symbol = "TEST", date = LocalDate.of(2020, 1, 2), closePrice = close)

  @Test
  fun `compiled entry script evaluates a quote-field expression against the live bar`() {
    // Given: an entry script referencing the current bar — also proves the scripting compiler
    // resolves app classes (StockQuote) on its compilation classpath, the riskiest unknown.
    val predicate = compiler.compileEntry("quote.closePrice > 100.0")

    // When / Then: the compiled predicate reflects the script's logic on each bar
    assertTrue(predicate(Stock(symbol = "TEST"), quoteAt(150.0), BacktestContext.EMPTY))
    assertFalse(predicate(Stock(symbol = "TEST"), quoteAt(50.0), BacktestContext.EMPTY))
  }

  @Test
  fun `compiled exit script evaluates against entry and current bars`() {
    // Given: an exit script comparing the current bar to the entry bar
    val predicate = compiler.compileExit("entryQuote != null && quote.closePrice < entryQuote.closePrice")

    // When / Then
    val entry = quoteAt(100.0)
    assertTrue(predicate(Stock(symbol = "TEST"), entry, quoteAt(90.0), BacktestContext.EMPTY))
    assertFalse(predicate(Stock(symbol = "TEST"), entry, quoteAt(110.0), BacktestContext.EMPTY))
  }

  @Test
  fun `compiled exit script can read the backtest context`() {
    // Given: an exit script that reads market breadth from the context — context-dependent
    // exits (e.g. "leave when breadth rolls over") are only expressible with this binding
    val predicate = compiler.compileExit(
      "(context.getMarketBreadth(quote.date)?.breadthPercent ?: 0.0) > 50.0",
    )
    val bar = quoteAt(100.0)
    val withBreadth =
      BacktestContext(
        sectorBreadthMap = emptyMap(),
        marketBreadthMap = mapOf(
          bar.date to MarketBreadthDaily(quoteDate = bar.date, breadthPercent = 60.0, ema10 = 50.0),
        ),
      )

    // When / Then: the exit script sees context-supplied breadth, and EMPTY context too
    assertTrue(predicate(Stock(symbol = "TEST"), bar, bar, withBreadth))
    assertFalse(predicate(Stock(symbol = "TEST"), bar, bar, BacktestContext.EMPTY))
  }

  @Test
  fun `a script that does not compile fails loudly with the compiler diagnostics`() {
    // Given: a script that is not valid Kotlin
    // When / Then: compilation throws — the failure surfaces here, not silently per bar
    val ex = assertThrows<IllegalArgumentException> { compiler.compileEntry("quote.closePrice >") }
    assertTrue(ex.message!!.contains("failed to compile"), "expected a compile-failure message")
  }
}
