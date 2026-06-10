package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FundamentalQualityDeteriorationConditionTest {
  private val today = LocalDate.of(2024, 1, 5)

  private fun stockAt(percentile: Double?) =
    Stock(quotes = mutableListOf(StockQuote(date = today, qualityPercentile = percentile)))

  @Test
  fun `should exit when the quality percentile falls below the floor`() {
    // Given a holding whose quality has decayed below 60
    val condition = FundamentalQualityDeteriorationCondition(exitBelowPercentile = 60.0)
    val stock = stockAt(59.9)

    // When / Then it exits
    assertTrue(condition.shouldExit(stock, stock.quotes.last(), stock.quotes.last()))
  }

  @Test
  fun `should hold while the quality percentile is at or above the floor`() {
    // Given a holding still ranked at the floor — hysteresis vs the 80 entry keeps it held
    val condition = FundamentalQualityDeteriorationCondition(exitBelowPercentile = 60.0)
    val stock = stockAt(60.0)

    // When / Then it holds
    assertFalse(condition.shouldExit(stock, stock.quotes.last(), stock.quotes.last()))
  }

  @Test
  fun `should exit when the quality percentile is unavailable`() {
    // Given the quality rank dropped out (null) — we can no longer prove it's a quality holding
    val condition = FundamentalQualityDeteriorationCondition(exitBelowPercentile = 60.0)
    val stock = stockAt(null)

    // When / Then a null reading is treated as deterioration and exits
    assertTrue(condition.shouldExit(stock, stock.quotes.last(), stock.quotes.last()))
  }
}
