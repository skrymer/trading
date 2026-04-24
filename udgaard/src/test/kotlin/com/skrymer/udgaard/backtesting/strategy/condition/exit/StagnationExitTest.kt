package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StagnationExitTest {
  private fun createStockWithQuotes(
    entryDate: LocalDate,
    totalDays: Int,
    priceAtEntry: Double = 100.0,
    priceGenerator: (Int) -> Double = { priceAtEntry },
  ): Pair<Stock, StockQuote> {
    val quotes =
      (0 until totalDays).map { dayOffset ->
        StockQuote(
          date = entryDate.plusDays(dayOffset.toLong()),
          closePrice = priceGenerator(dayOffset),
        )
      }
    val stock = Stock(quotes = quotes)
    val entryQuote = quotes.first()
    return stock to entryQuote
  }

  @Test
  fun `should exit when price has not progressed after window days`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { 100.0 }, // flat price
    )
    val dayQuote = stock.quotes[15] // exactly at window

    assertTrue(
      condition.shouldExit(stock, entryQuote, dayQuote),
      "Should exit when price hasn't gained 3% after 15 days",
    )
  }

  @Test
  fun `should not exit before window elapses`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { 100.0 },
    )
    val dayQuote = stock.quotes[10] // before window

    assertFalse(
      condition.shouldExit(stock, entryQuote, dayQuote),
      "Should not exit before window elapses",
    )
  }

  @Test
  fun `should not exit after window has passed`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { 100.0 },
    )
    val dayQuote = stock.quotes[18] // after window

    assertFalse(
      condition.shouldExit(stock, entryQuote, dayQuote),
      "Should only fire exactly at the window, not after",
    )
  }

  @Test
  fun `should not exit when price has gained enough`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { if (it >= 15) 104.0 else 100.0 }, // jumps to +4% at window
    )
    val dayQuote = stock.quotes[15]

    assertFalse(
      condition.shouldExit(stock, entryQuote, dayQuote),
      "Should not exit when price has gained above threshold",
    )
  }

  @Test
  fun `should exit when gain is exactly at threshold`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { if (it >= 15) 102.99 else 100.0 }, // just below 3%
    )
    val dayQuote = stock.quotes[15]

    assertTrue(
      condition.shouldExit(stock, entryQuote, dayQuote),
      "Should exit when gain is below threshold",
    )
  }

  @Test
  fun `should not exit when gain equals threshold exactly`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { if (it >= 15) 103.0 else 100.0 }, // exactly 3%
    )
    val dayQuote = stock.quotes[15]

    assertFalse(
      condition.shouldExit(stock, entryQuote, dayQuote),
      "Should not exit when gain equals threshold exactly",
    )
  }

  @Test
  fun `should exit when price has dropped below entry`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { 98.0 }, // down 2%
    )
    val dayQuote = stock.quotes[15]

    assertTrue(
      condition.shouldExit(stock, entryQuote, dayQuote),
      "Should exit when price is negative at window",
    )
  }

  @Test
  fun `should not exit when entryQuote is null`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val stock = Stock()
    val quote = StockQuote(date = LocalDate.of(2024, 1, 16), closePrice = 100.0)

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when entryQuote is null",
    )
  }

  @Test
  fun `should work with different parameters`() {
    val condition = StagnationExit(thresholdPercent = 5.0, windowDays = 20)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 25,
      priceAtEntry = 100.0,
      priceGenerator = { 104.0 }, // +4%, below 5% threshold
    )
    val dayQuote = stock.quotes[20]

    assertTrue(
      condition.shouldExit(stock, entryQuote, dayQuote),
      "Should exit with custom parameters when below threshold",
    )
  }

  @Test
  fun `should provide correct exit reason`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    assertEquals(
      "Price stagnation (less than 3.0% gain after 15 trading days)",
      condition.exitReason(),
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    assertEquals("Stagnation (3.0% / 15d)", condition.description())
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = StagnationExit()
    val metadata = condition.getMetadata()
    assertEquals("stagnation", metadata.type)
    assertEquals("Price Stagnation", metadata.displayName)
    assertEquals("CapitalEfficiency", metadata.category)
    assertEquals(2, metadata.parameters.size)
    assertEquals("thresholdPercent", metadata.parameters[0].name)
    assertEquals("windowDays", metadata.parameters[1].name)
  }

  @Test
  fun `should provide detailed evaluation when window not reached`() {
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
    )
    val result = condition.evaluateWithDetails(stock, entryQuote, stock.quotes[5])
    assertFalse(result.passed)
    assertTrue(result.message?.contains("Window not reached") == true)
  }

  // ===== proximity =====

  @Test
  fun `proximity is 1_0 on the trigger day with zero gain`() {
    // Given: flat price through the entire 15-day window. On day 15 stagnation fires.
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
    )

    // When: evaluating on the day exactly at the window boundary.
    val proximity = condition.proximity(stock, entryQuote, stock.quotes[15])

    // Then: invariant — whenever shouldExit returns true, proximity must be >= 1.0.
    assertNotNull(proximity)
    assertEquals(1.0, proximity!!.proximity, 1e-9)
    assertEquals("stagnation", proximity.conditionType)
  }

  @Test
  fun `proximity is about 0_93 on day 14 of a 15-day window with zero gain`() {
    // Given: one day shy of the window, still no gain. Bar factor 14/15, gain factor 1.
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
    )

    // When
    val proximity = condition.proximity(stock, entryQuote, stock.quotes[14])

    // Then
    assertNotNull(proximity)
    assertEquals(14.0 / 15.0, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity is 0_0 on the trigger day when gain already exceeds threshold`() {
    // Given: on day 15, but price is up 5% — well above the 3% threshold. Gain factor 0.
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { day -> if (day == 15) 105.0 else 100.0 },
    )

    // When
    val proximity = condition.proximity(stock, entryQuote, stock.quotes[15])

    // Then: bar factor is 1.0, but gain-shortfall is 0 — product is 0.
    assertNotNull(proximity)
    assertEquals(0.0, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity is 0_93 on day 14 when gain is below threshold regardless of magnitude`() {
    // Given: day 14, gain +1.5% (below the 3% threshold). The gain side is a binary
    // gate — any below-threshold gain produces full "would fire tomorrow" proximity.
    // This matches the actual stagnation trigger semantics (discrete below/above, not
    // a gradient) and surfaces every near-imminent stagnation exit to the user.
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
      priceGenerator = { day -> if (day == 14) 101.5 else 100.0 },
    )

    // When
    val proximity = condition.proximity(stock, entryQuote, stock.quotes[14])

    // Then
    assertNotNull(proximity)
    assertEquals(14.0 / 15.0, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity is 0_0 on day 0 of the window`() {
    // Given: the entry bar itself — barsSinceEntry is 0, so the window factor is 0.
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
    )

    // When
    val proximity = condition.proximity(stock, entryQuote, stock.quotes[0])

    // Then
    assertNotNull(proximity)
    assertEquals(0.0, proximity!!.proximity, 1e-9)
  }

  @Test
  fun `proximity returns null when entryQuote is null`() {
    // Given: no entry reference — gain and days-since-entry can't be computed.
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 15)
    val (stock, _) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
    )

    // When / Then
    assertNull(condition.proximity(stock, null, stock.quotes[5]))
  }

  @Test
  fun `proximity returns null when windowDays is zero`() {
    // Given: invalid config — window factor would divide by zero.
    val condition = StagnationExit(thresholdPercent = 3.0, windowDays = 0)
    val (stock, entryQuote) = createStockWithQuotes(
      entryDate = LocalDate.of(2024, 1, 1),
      totalDays = 20,
      priceAtEntry = 100.0,
    )

    // When / Then
    assertNull(condition.proximity(stock, entryQuote, stock.quotes[5]))
  }
}
