package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.ScriptPredicateCompiler
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ScriptEntryConditionTest {
  private val compiler = ScriptPredicateCompiler()
  private val defaultBearer = ScriptEntryCondition(compiler)

  private fun quoteAt(close: Double) =
    StockQuote(symbol = "TEST", date = LocalDate.of(2020, 1, 2), closePrice = close)

  @Test
  fun `condition built from a wire config evaluates the supplied script per bar`() {
    // Given: a script entry condition built from a `{script: ...}` wire config
    val condition = defaultBearer.parseConfig(mapOf("script" to "quote.closePrice > 100.0"))

    // When / Then: evaluate reflects the script's logic on each bar
    assertTrue(condition.evaluate(Stock(symbol = "TEST"), quoteAt(150.0), BacktestContext.EMPTY))
    assertFalse(condition.evaluate(Stock(symbol = "TEST"), quoteAt(50.0), BacktestContext.EMPTY))
  }

  @Test
  fun `distinct script conditions in the same strategy evaluate independently`() {
    // Given: two script entry conditions from different wire configs — as a custom strategy
    // carrying multiple {type: script} conditions produces
    val above100 = defaultBearer.parseConfig(mapOf("script" to "quote.closePrice > 100.0"))
    val below200 = defaultBearer.parseConfig(mapOf("script" to "quote.closePrice < 200.0"))

    // When / Then: each instance carries its own script — parseConfig is a real factory,
    // no shared state
    val stock = Stock(symbol = "TEST")
    assertTrue(above100.evaluate(stock, quoteAt(150.0), BacktestContext.EMPTY))
    assertTrue(below200.evaluate(stock, quoteAt(150.0), BacktestContext.EMPTY))
    assertFalse(above100.evaluate(stock, quoteAt(50.0), BacktestContext.EMPTY))
    assertFalse(below200.evaluate(stock, quoteAt(250.0), BacktestContext.EMPTY))
  }
}
