package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MinimumHistoryDaysConditionTest {
  @Test
  fun `should pass when stock has been listed longer than the threshold`() {
    // Given: a stock listed 200 days before the entry quote date
    val condition = MinimumHistoryDaysCondition(days = 180)
    val entryDate = LocalDate.of(2024, 7, 1)
    val stock = Stock(symbol = "OLDCO", listingDate = entryDate.minusDays(200))
    val quote = StockQuote(date = entryDate)

    // When / Then
    assertTrue(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `should fail when stock has been listed for fewer days than the threshold`() {
    // Given: a stock listed only 30 days before entry — well within IPO-recency window
    val condition = MinimumHistoryDaysCondition(days = 180)
    val entryDate = LocalDate.of(2024, 7, 1)
    val stock = Stock(symbol = "FRESH", listingDate = entryDate.minusDays(30))
    val quote = StockQuote(date = entryDate)

    // When / Then
    assertFalse(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `should pass at the exact boundary`() {
    // Given: a stock listed exactly 180 days before entry
    val condition = MinimumHistoryDaysCondition(days = 180)
    val entryDate = LocalDate.of(2024, 7, 1)
    val stock = Stock(symbol = "BOUND", listingDate = entryDate.minusDays(180))
    val quote = StockQuote(date = entryDate)

    // When / Then: inclusive boundary
    assertTrue(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `should fall back to first quote date when listingDate is unset`() {
    // Given: an older-style stock with no listingDate metadata, but quotes ranging back 250 days
    val condition = MinimumHistoryDaysCondition(days = 180)
    val entryDate = LocalDate.of(2024, 7, 1)
    val firstBar = StockQuote(date = entryDate.minusDays(250))
    val midBar = StockQuote(date = entryDate.minusDays(100))
    val stock = Stock(symbol = "LEGACY", listingDate = null, quotes = listOf(firstBar, midBar))

    // When
    val result = condition.evaluate(stock, StockQuote(date = entryDate), BacktestContext.EMPTY)

    // Then: 250 days of bars > 180 day threshold via fallback path
    assertTrue(result)
  }

  @Test
  fun `should fail when listingDate is unset and no quotes exist`() {
    // Given: no metadata at all (defensive — this shouldn't happen in practice but the condition should not NPE)
    val condition = MinimumHistoryDaysCondition(days = 180)
    val stock = Stock(symbol = "EMPTY", listingDate = null, quotes = emptyList())
    val quote = StockQuote(date = LocalDate.of(2024, 7, 1))

    // When / Then: defensive default to false rather than throw
    assertFalse(condition.evaluate(stock, quote, BacktestContext.EMPTY))
  }

  @Test
  fun `evaluateWithDetails reports the actual days-since-listing`() {
    // Given: 250 days since listing, threshold 180
    val condition = MinimumHistoryDaysCondition(days = 180)
    val entryDate = LocalDate.of(2024, 7, 1)
    val stock = Stock(symbol = "DET", listingDate = entryDate.minusDays(250))
    val quote = StockQuote(date = entryDate)

    // When
    val result = condition.evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

    // Then
    assertTrue(result.passed)
    assertEquals("250", result.actualValue)
    assertEquals("180", result.threshold)
  }
}
