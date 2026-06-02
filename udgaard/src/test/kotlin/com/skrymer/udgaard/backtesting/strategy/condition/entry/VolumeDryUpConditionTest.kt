package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VolumeDryUpConditionTest {
  private fun stockOf(volumes: List<Long>): Stock {
    val start = LocalDate.of(2024, 1, 1)
    val quotes =
      volumes.mapIndexed { i, v ->
        StockQuote(date = start.plusDays(i.toLong()), volume = v)
      }
    return Stock(quotes = quotes.toMutableList())
  }

  @Test
  fun `passes when recent average volume is below ratio times base average`() {
    // Given a base window whose recent bars have dried up well below the base average
    val condition = VolumeDryUpCondition(dryupWindow = 2, baseWindow = 4, dryupRatio = 0.7)
    val stock = stockOf(volumes = listOf(100L, 100L, 30L, 30L))
    val quote = stock.quotes.last()

    // When the condition evaluates the current bar
    val result = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then recent avg 30 < 0.7 * base avg 65 (45.5) → dry-up confirmed
    assertTrue(result, "recent avg 30 should be below 0.7 * base avg 65 = 45.5")
  }

  @Test
  fun `fails when recent volume has not contracted below the ratio`() {
    // Given recent bars still trading at roughly the base-window level
    val condition = VolumeDryUpCondition(dryupWindow = 2, baseWindow = 4, dryupRatio = 0.7)
    val stock = stockOf(volumes = listOf(100L, 100L, 90L, 90L))
    val quote = stock.quotes.last()

    // When the condition evaluates the current bar
    val result = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then recent avg 90 is not below 0.7 * base avg 95 (66.5) → no dry-up
    assertFalse(result, "recent avg 90 should NOT be below 0.7 * base avg 95 = 66.5")
  }

  @Test
  fun `fails when fewer than baseWindow bars of history are available`() {
    // Given only 3 bars but a base window of 4
    val condition = VolumeDryUpCondition(dryupWindow = 2, baseWindow = 4, dryupRatio = 0.7)
    val stock = stockOf(volumes = listOf(100L, 30L, 30L))
    val quote = stock.quotes.last()

    // When the condition evaluates the current bar
    val result = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then it fails closed on insufficient history (3 < 4)
    assertFalse(result, "should fail closed when bars (3) < baseWindow (4)")
  }

  @Test
  fun `future-dated bars after the current bar are excluded from the windows`() {
    // Given dry-up present at an interior bar, followed by future high-volume bars that would
    // flip the verdict if they leaked into the trailing windows
    val condition = VolumeDryUpCondition(dryupWindow = 2, baseWindow = 4, dryupRatio = 0.7)
    val stock = stockOf(volumes = listOf(100L, 100L, 30L, 30L, 9999L, 9999L))
    val interiorQuote = stock.quotes[3]

    // When evaluating the interior bar (not the last quote)
    val result = condition.evaluate(stock, interiorQuote, BacktestContext.EMPTY)

    // Then only bars up to and including the interior bar count → dry-up still confirmed
    assertTrue(result, "future 9999-volume bars must not enter the windows when evaluating an earlier bar")
  }

  @Test
  fun `fails when base-window average volume is zero`() {
    // Given a base window of entirely zero volume (no participation to compare against)
    val condition = VolumeDryUpCondition(dryupWindow = 2, baseWindow = 4, dryupRatio = 0.7)
    val stock = stockOf(volumes = listOf(0L, 0L, 0L, 0L))
    val quote = stock.quotes.last()

    // When the condition evaluates the current bar
    val result = condition.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then it fails closed rather than treating 0 < 0 as a dry-up
    assertFalse(result, "should fail closed when base-window average volume is zero")
  }

  @Test
  fun `parseConfig applies configured windows so the parsed condition evaluates with them`() {
    // Given the default condition (baseWindow 50) which would fail-closed on a 4-bar fixture
    val parsed =
      VolumeDryUpCondition().parseConfig(
        mapOf("dryupWindow" to 2, "baseWindow" to 4, "dryupRatio" to 0.7),
      )
    val stock = stockOf(volumes = listOf(100L, 100L, 30L, 30L))
    val quote = stock.quotes.last()

    // When the parsed condition evaluates the 4-bar fixture
    val result = parsed.evaluate(stock, quote, BacktestContext.EMPTY)

    // Then the configured baseWindow=4 takes effect and dry-up is confirmed
    assertTrue(result, "parseConfig should apply baseWindow=4 so the 4-bar dry-up passes")
  }

  @Test
  fun `metadata exposes the routing type and all three configurable parameters`() {
    // Given the default condition
    val metadata = VolumeDryUpCondition().getMetadata()

    // Then the registry routing type and parameter defaults are exposed
    assertEquals("volumeDryUp", metadata.type)
    assertEquals("Volume", metadata.category)
    assertEquals(3, metadata.parameters.size)
    assertEquals(10, metadata.parameters.find { it.name == "dryupWindow" }?.defaultValue)
    assertEquals(50, metadata.parameters.find { it.name == "baseWindow" }?.defaultValue)
    assertEquals(0.7, metadata.parameters.find { it.name == "dryupRatio" }?.defaultValue)
    assertNotNull(metadata.parameters.find { it.name == "dryupRatio" })
  }

  @Test
  fun `evaluateWithDetails verdict mirrors evaluate for pass and fail`() {
    // Given a dry-up fixture and a non-dry-up fixture
    val condition = VolumeDryUpCondition(dryupWindow = 2, baseWindow = 4, dryupRatio = 0.7)
    val dryUp = stockOf(volumes = listOf(100L, 100L, 30L, 30L))
    val notDry = stockOf(volumes = listOf(100L, 100L, 90L, 90L))

    // When evaluating both with the detailed surface
    val passDetails = condition.evaluateWithDetails(dryUp, dryUp.quotes.last(), BacktestContext.EMPTY)
    val failDetails = condition.evaluateWithDetails(notDry, notDry.quotes.last(), BacktestContext.EMPTY)

    // Then the detailed verdict matches evaluate and carries a human-readable message
    assertTrue(passDetails.passed)
    assertFalse(failDetails.passed)
    assertEquals("VolumeDryUpCondition", passDetails.conditionType)
    assertTrue(passDetails.message!!.contains("✓"))
    assertTrue(failDetails.message!!.contains("✗"))
  }

  @Test
  fun `construction rejects a dry-up window wider than the base window`() {
    // Given / When / Then a dry-up window larger than the base window is rejected at construction
    assertThrows(IllegalArgumentException::class.java) {
      VolumeDryUpCondition(dryupWindow = 60, baseWindow = 50)
    }
  }
}
