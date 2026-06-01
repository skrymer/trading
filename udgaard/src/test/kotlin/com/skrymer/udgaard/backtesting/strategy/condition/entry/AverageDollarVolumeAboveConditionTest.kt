package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AverageDollarVolumeAboveConditionTest {
  @Test
  fun `evaluate returns true when trailing average dollar volume is above the threshold`() {
    // Given a stock whose 10 trailing bars each trade $100M/day, and a $50M threshold
    val condition = AverageDollarVolumeAboveCondition(thresholdUsd = 50_000_000.0, lookbackDays = 10)
    val stock =
      Stock(
        quotes =
          (1..10)
            .map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 100.0, volume = 1_000_000) }
            .toMutableList(),
      )
    val entry = StockQuote(date = LocalDate.of(2024, 1, 11), closePrice = 100.0, volume = 1_000_000)

    // When evaluated on the entry bar
    val result = condition.evaluate(stock, entry, BacktestContext.EMPTY)

    // Then it passes — trailing average $100M clears the $50M threshold
    assertTrue(result, "trailing average dollar volume of \$100M should clear the \$50M threshold")
  }

  @Test
  fun `evaluate returns false when trailing average dollar volume is below the threshold`() {
    // Given a stock whose 10 trailing bars each trade only $10M/day, and a $50M threshold
    val condition = AverageDollarVolumeAboveCondition(thresholdUsd = 50_000_000.0, lookbackDays = 10)
    val stock =
      Stock(
        quotes =
          (1..10)
            .map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 10.0, volume = 1_000_000) }
            .toMutableList(),
      )
    val entry = StockQuote(date = LocalDate.of(2024, 1, 11), closePrice = 10.0, volume = 1_000_000)

    // When evaluated on the entry bar
    val result = condition.evaluate(stock, entry, BacktestContext.EMPTY)

    // Then it fails — trailing average $10M is below the $50M threshold
    assertFalse(result, "trailing average dollar volume of \$10M should not clear the \$50M threshold")
  }

  @Test
  fun `evaluate returns true when trailing average dollar volume exactly equals the threshold`() {
    // Given trailing bars at exactly $50M/day and a $50M threshold
    val condition = AverageDollarVolumeAboveCondition(thresholdUsd = 50_000_000.0, lookbackDays = 10)
    val stock =
      Stock(
        quotes =
          (1..10)
            .map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 50.0, volume = 1_000_000) }
            .toMutableList(),
      )
    val entry = StockQuote(date = LocalDate.of(2024, 1, 11), closePrice = 50.0, volume = 1_000_000)

    // When evaluated
    val result = condition.evaluate(stock, entry, BacktestContext.EMPTY)

    // Then it passes — the threshold is inclusive (>=)
    assertTrue(result, "trailing average exactly at the threshold should pass (inclusive)")
  }

  @Test
  fun `evaluate excludes the entry bar's own dollar volume (no lookahead)`() {
    // Given 10 trailing bars at $10M/day, and an entry bar (present in stock.quotes) at $10B
    val condition = AverageDollarVolumeAboveCondition(thresholdUsd = 50_000_000.0, lookbackDays = 10)
    val trailing =
      (1..10).map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 10.0, volume = 1_000_000) }
    val entry = StockQuote(date = LocalDate.of(2024, 1, 11), closePrice = 1000.0, volume = 10_000_000)
    val stock = Stock(quotes = (trailing + entry).toMutableList())

    // When evaluated on the entry bar
    val result = condition.evaluate(stock, entry, BacktestContext.EMPTY)

    // Then it fails — the entry bar's $10B is excluded; only the trailing $10M window counts
    assertFalse(result, "entry bar dollar volume must be excluded — trailing \$10M window should decide")
  }

  @Test
  fun `evaluate ignores bars older than the lookback window`() {
    // Given 3 old bars at $1B/day, then 5 recent bars at $10M/day, lookback 5
    val condition = AverageDollarVolumeAboveCondition(thresholdUsd = 50_000_000.0, lookbackDays = 5)
    val old = (1..3).map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 1000.0, volume = 1_000_000) }
    val recent = (11..15).map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 10.0, volume = 1_000_000) }
    val stock = Stock(quotes = (old + recent).toMutableList())
    val entry = StockQuote(date = LocalDate.of(2024, 1, 16), closePrice = 10.0, volume = 1_000_000)

    // When evaluated
    val result = condition.evaluate(stock, entry, BacktestContext.EMPTY)

    // Then it fails — only the recent 5-bar $10M window counts, not the older $1B bars
    assertFalse(result, "older high-liquidity bars outside the lookback must be excluded")
  }

  @Test
  fun `evaluate returns false when there is insufficient history`() {
    // Given a 20-day lookback but only 5 trailing bars — each liquid enough to pass on value
    val condition = AverageDollarVolumeAboveCondition(thresholdUsd = 50_000_000.0, lookbackDays = 20)
    val stock =
      Stock(
        quotes =
          (1..5)
            .map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 100.0, volume = 1_000_000) }
            .toMutableList(),
      )
    val entry = StockQuote(date = LocalDate.of(2024, 1, 6), closePrice = 100.0, volume = 1_000_000)

    // When evaluated
    val result = condition.evaluate(stock, entry, BacktestContext.EMPTY)

    // Then the gate stays closed — fewer than lookbackDays/2 bars, even though they are liquid
    assertFalse(result, "insufficient history should close the gate despite liquid bars")
  }

  @Test
  fun `description renders the threshold and lookback window`() {
    // Given default and custom configurations
    // When describing them
    // Then the threshold (formatted) and window are shown
    assertEquals("Avg \$ volume ≥ \$50.0M (20 days)", AverageDollarVolumeAboveCondition().description())
    assertEquals(
      "Avg \$ volume ≥ \$100.0M (50 days)",
      AverageDollarVolumeAboveCondition(thresholdUsd = 100_000_000.0, lookbackDays = 50).description(),
    )
  }

  @Test
  fun `metadata exposes type, category, and tunable parameters`() {
    // When reading the metadata of a default-configured condition
    val metadata = AverageDollarVolumeAboveCondition().getMetadata()

    // Then the wire type, category, and both tunables (with bounds and defaults) are exposed
    assertEquals("averageDollarVolumeAbove", metadata.type)
    assertEquals("Volume", metadata.category)
    assertEquals(2, metadata.parameters.size)

    val thresholdParam = metadata.parameters.find { it.name == "thresholdUsd" }
    assertNotNull(thresholdParam)
    assertEquals(50_000_000.0, thresholdParam?.defaultValue)
    assertEquals(1_000_000, thresholdParam?.min)
    assertEquals(1_000_000_000, thresholdParam?.max)

    val lookbackParam = metadata.parameters.find { it.name == "lookbackDays" }
    assertNotNull(lookbackParam)
    assertEquals(20, lookbackParam?.defaultValue)
    assertEquals(5, lookbackParam?.min)
    assertEquals(100, lookbackParam?.max)
  }

  @Test
  fun `evaluateWithDetails reports a passing result with the measured and threshold values`() {
    // Given trailing bars at $100M/day and a $50M threshold
    val condition = AverageDollarVolumeAboveCondition(thresholdUsd = 50_000_000.0, lookbackDays = 10)
    val stock =
      Stock(
        quotes =
          (1..10)
            .map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 100.0, volume = 1_000_000) }
            .toMutableList(),
      )
    val entry = StockQuote(date = LocalDate.of(2024, 1, 11), closePrice = 100.0, volume = 1_000_000)

    // When evaluating with details
    val result = condition.evaluateWithDetails(stock, entry, BacktestContext.EMPTY)

    // Then it reports a pass with the measured average and the threshold
    assertTrue(result.passed)
    assertEquals("AverageDollarVolumeAboveCondition", result.conditionType)
    assertEquals("\$100.0M", result.actualValue)
    assertEquals("≥ \$50.0M", result.threshold)
    assertTrue(result.message!!.contains("✓"))
  }

  @Test
  fun `evaluateWithDetails reports a failing result with the measured and threshold values`() {
    // Given trailing bars at $10M/day and a $50M threshold
    val condition = AverageDollarVolumeAboveCondition(thresholdUsd = 50_000_000.0, lookbackDays = 10)
    val stock =
      Stock(
        quotes =
          (1..10)
            .map { StockQuote(date = LocalDate.of(2024, 1, it), closePrice = 10.0, volume = 1_000_000) }
            .toMutableList(),
      )
    val entry = StockQuote(date = LocalDate.of(2024, 1, 11), closePrice = 10.0, volume = 1_000_000)

    // When evaluating with details
    val result = condition.evaluateWithDetails(stock, entry, BacktestContext.EMPTY)

    // Then it reports a fail with the measured average and what was needed
    assertFalse(result.passed)
    assertEquals("\$10.0M", result.actualValue)
    assertTrue(result.message!!.contains("needs"))
    assertTrue(result.message.contains("✗"))
  }

  @Test
  fun `parseConfig applies provided parameters`() {
    // Given a wire config overriding both tunables
    val configured =
      AverageDollarVolumeAboveCondition()
        .parseConfig(mapOf("thresholdUsd" to 100_000_000.0, "lookbackDays" to 50))

    // Then the configured condition reflects them (observable via its description)
    assertEquals("Avg \$ volume ≥ \$100.0M (50 days)", configured.description())
  }

  @Test
  fun `parseConfig falls back to defaults for missing parameters`() {
    // Given an empty wire config
    val configured = AverageDollarVolumeAboveCondition().parseConfig(emptyMap())

    // Then the defaults hold
    assertEquals("Avg \$ volume ≥ \$50.0M (20 days)", configured.description())
  }
}
