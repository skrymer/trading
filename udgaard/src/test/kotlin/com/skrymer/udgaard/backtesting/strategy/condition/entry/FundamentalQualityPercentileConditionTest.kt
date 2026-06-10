package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FundamentalQualityPercentileConditionTest {
  private val today = LocalDate.of(2024, 1, 5)

  @Test
  fun `should pass when the stock is at or above the required quality percentile`() {
    // Given a stock ranked in the 85th quality percentile, threshold 80
    val condition = FundamentalQualityPercentileCondition(minPercentile = 80.0)
    val stock = Stock(quotes = mutableListOf(StockQuote(date = today, qualityPercentile = 85.0)))

    // When evaluated
    val result = condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY)

    // Then it passes
    assertTrue(result)
  }

  @Test
  fun `should pass at the boundary percentile`() {
    // Given a stock exactly at the threshold
    val condition = FundamentalQualityPercentileCondition(minPercentile = 80.0)
    val stock = Stock(quotes = mutableListOf(StockQuote(date = today, qualityPercentile = 80.0)))

    // When / Then the boundary passes
    assertTrue(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY))
  }

  @Test
  fun `should fail when the stock is below the required percentile`() {
    // Given a stock ranked below the threshold
    val condition = FundamentalQualityPercentileCondition(minPercentile = 80.0)
    val stock = Stock(quotes = mutableListOf(StockQuote(date = today, qualityPercentile = 79.9)))

    // When / Then it fails
    assertFalse(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY))
  }

  @Test
  fun `should fail closed when the quality percentile is unavailable`() {
    // Given a stock with no quality rank (insufficient filings or thin universe)
    val condition = FundamentalQualityPercentileCondition(minPercentile = 80.0)
    val stock = Stock(quotes = mutableListOf(StockQuote(date = today, qualityPercentile = null)))

    // When / Then a stock with no rank must not pass the gate
    assertFalse(condition.evaluate(stock, stock.quotes.last(), BacktestContext.EMPTY))
  }
}
