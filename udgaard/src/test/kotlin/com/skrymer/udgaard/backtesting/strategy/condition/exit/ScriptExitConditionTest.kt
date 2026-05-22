package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.service.ScriptPredicateCompiler
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ScriptExitConditionTest {
  private val compiler = ScriptPredicateCompiler()
  private val defaultBearer = ScriptExitCondition(compiler)

  private fun quoteAt(close: Double) =
    StockQuote(symbol = "TEST", date = LocalDate.of(2020, 1, 2), closePrice = close)

  @Test
  fun `condition built from a wire config triggers per the supplied script`() {
    // Given: an exit script that exits once price falls below the entry bar
    val condition =
      defaultBearer.parseConfig(
        mapOf("script" to "entryQuote != null && quote.closePrice < entryQuote.closePrice"),
      )

    // When / Then
    assertTrue(condition.shouldExit(Stock(symbol = "TEST"), quoteAt(100.0), quoteAt(90.0)))
    assertFalse(condition.shouldExit(Stock(symbol = "TEST"), quoteAt(100.0), quoteAt(110.0)))
  }

  @Test
  fun `script receives a null entry quote and handles it without failing`() {
    // Given: a bar with no recorded entry quote — the nullable entryQuote is the script's job
    val condition = defaultBearer.parseConfig(mapOf("script" to "entryQuote != null"))

    // When / Then: no exception, the script's null handling decides the result
    assertFalse(condition.shouldExit(Stock(symbol = "TEST"), null, quoteAt(90.0)))
  }
}
