package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PriceAboveSmaConditionTest {
  private val today = LocalDate.of(2024, 1, 5)

  @Test
  fun `should pass when the close is above SMA200`() {
    // Given a close above its 200-day simple moving average
    val condition = PriceAboveSmaCondition(period = 200)
    val stock = Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 105.0, sma200 = 100.0)))

    // When / Then it passes
    assertTrue(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY))
  }

  @Test
  fun `should fail when the close is at or below SMA200`() {
    // Given a close at the SMA
    val condition = PriceAboveSmaCondition(period = 200)
    val stock = Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 100.0, sma200 = 100.0)))

    // When / Then it fails (strict >)
    assertFalse(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY))
  }

  @Test
  fun `should fail closed when the SMA is unavailable`() {
    // Given insufficient history for the 200-day SMA (null)
    val condition = PriceAboveSmaCondition(period = 200)
    val stock = Stock(quotes = mutableListOf(StockQuote(date = today, closePrice = 105.0, sma200 = null)))

    // When / Then it fails rather than passing on missing data
    assertFalse(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY))
  }

  @Test
  fun `should reject an unsupported SMA period at construction`() {
    // Given a period with no persisted SMA window
    // When / Then construction fails loudly rather than silently never matching
    assertThrows(IllegalArgumentException::class.java) { PriceAboveSmaCondition(period = 100) }
  }
}
