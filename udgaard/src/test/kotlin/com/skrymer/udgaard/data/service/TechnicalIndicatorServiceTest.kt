package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TechnicalIndicatorServiceTest {
  private val service = TechnicalIndicatorService()

  // ===================================================================
  // ATR Tests
  // ===================================================================

  @Test
  fun `calculateATR returns zeros when not enough data`() {
    val quotes = (1..10).map { createQuote(it, 100.0, 105.0, 95.0) }
    val result = service.calculateATR(quotes, 14)
    assertEquals(10, result.size)
    result.forEach { assertEquals(0.0, it) }
  }

  @Test
  fun `calculateATR calculates correct values with Wilder smoothing`() {
    // Use a known dataset with period=3 for easy manual verification
    // Day 0: O=100, H=105, L=95,  C=102  -> TR = N/A (no prev close)
    // Day 1: O=102, H=108, L=100, C=106  -> TR = max(8, 6, 2) = 8
    // Day 2: O=106, H=110, L=103, C=107  -> TR = max(7, 4, 3) = 7
    // Day 3: O=107, H=112, L=105, C=110  -> TR = max(7, 5, 2) = 7
    // First ATR (index 3) = SMA(8, 7, 7) = 22/3 = 7.3333...
    // Day 4: O=110, H=115, L=108, C=113  -> TR = max(7, 5, 2) = 7
    // ATR[4] = ((7.3333 * 2) + 7) / 3 = 21.6667 / 3 = 7.2222...

    val quotes = listOf(
      createQuote(0, 102.0, 105.0, 95.0, 100.0),
      createQuote(1, 106.0, 108.0, 100.0, 102.0),
      createQuote(2, 107.0, 110.0, 103.0, 106.0),
      createQuote(3, 110.0, 112.0, 105.0, 107.0),
      createQuote(4, 113.0, 115.0, 108.0, 110.0),
    )

    val result = service.calculateATR(quotes, 3)

    assertEquals(5, result.size)
    assertEquals(0.0, result[0], 0.0001)
    assertEquals(0.0, result[1], 0.0001)
    assertEquals(0.0, result[2], 0.0001)
    assertEquals(7.3333, result[3], 0.0001) // First ATR = SMA(8, 7, 7)
    assertEquals(7.2222, result[4], 0.0001) // Wilder smoothed
  }

  @Test
  fun `calculateATR handles gap down correctly`() {
    val quotes = listOf(
      createQuote(0, 150.0, 155.0, 145.0, 148.0),
      createQuote(1, 130.0, 135.0, 125.0, 128.0), // Gap down from 150
      createQuote(2, 128.0, 132.0, 126.0, 127.0),
      createQuote(3, 127.0, 130.0, 124.0, 125.0),
    )

    // Day 1: TR = max(10, |135-150|, |125-150|) = 25
    // Day 2: TR = max(6, |132-130|, |126-130|) = 6
    // Day 3: TR = max(6, |130-128|, |124-128|) = 6
    // First ATR = SMA(25, 6, 6) = 37/3 = 12.3333
    val result = service.calculateATR(quotes, 3)
    assertEquals(12.3333, result[3], 0.0001)
  }

  // ===================================================================
  // ADX Tests
  // ===================================================================

  @Test
  fun `calculateADX returns zeros when not enough data`() {
    val quotes = (0..20).map { createQuote(it, 100.0, 105.0, 95.0) }
    val result = service.calculateADX(quotes, 14) // Need 29 bars minimum
    assertEquals(21, result.size)
    result.forEach { assertEquals(0.0, it) }
  }

  @Test
  fun `calculateADX produces values at correct indices with period 3`() {
    // With period=3, first ADX at index 2*3-1 = 5, need at least 7 bars
    // Build a dataset with clear uptrend for predictable DM values
    val quotes = listOf(
      // Day 0: base
      createQuote(0, 100.0, 102.0, 98.0, 99.0),
      // Day 1: higher high, higher low -> +DM=3, -DM=0
      createQuote(1, 103.0, 105.0, 100.0, 101.0),
      // Day 2: higher high, higher low -> +DM=3, -DM=0
      createQuote(2, 106.0, 108.0, 103.0, 104.0),
      // Day 3: higher high, higher low -> +DM=3, -DM=0
      createQuote(3, 109.0, 111.0, 106.0, 107.0),
      // Day 4: higher high, higher low -> +DM=3, -DM=0
      createQuote(4, 112.0, 114.0, 109.0, 110.0),
      // Day 5: higher high, higher low -> +DM=3, -DM=0
      createQuote(5, 115.0, 117.0, 112.0, 113.0),
      // Day 6: higher high, higher low -> +DM=3, -DM=0
      createQuote(6, 118.0, 120.0, 115.0, 116.0),
      // Day 7: higher high, higher low -> +DM=3, -DM=0
      createQuote(7, 121.0, 123.0, 118.0, 119.0),
    )

    val result = service.calculateADX(quotes, 3)

    assertEquals(8, result.size)
    // Indices 0-4 should be 0.0 (not enough data)
    for (i in 0..4) {
      assertEquals(0.0, result[i], "Index $i should be 0.0")
    }
    // Index 5 (=2*3-1) should have first ADX value
    assertTrue(result[5] > 0.0, "First ADX at index 5 should be > 0")
    // In a perfect uptrend, +DI >> -DI, so DX and ADX should be high (near 100)
    assertTrue(result[5] > 50.0, "ADX in strong uptrend should be > 50, got ${result[5]}")
    // ADX should continue for subsequent bars
    assertTrue(result[6] > 0.0, "ADX at index 6 should be > 0")
    assertTrue(result[7] > 0.0, "ADX at index 7 should be > 0")
  }

  @Test
  fun `calculateADX shows low values in choppy market`() {
    // Alternating up and down days -> DX should be low -> ADX should be low
    val quotes = mutableListOf(createQuote(0, 100.0, 102.0, 98.0, 99.0))
    for (i in 1..30) {
      if (i % 2 == 1) {
        // Up day
        quotes.add(createQuote(i, 102.0, 104.0, 99.0, 100.0))
      } else {
        // Down day
        quotes.add(createQuote(i, 99.0, 103.0, 97.0, 101.0))
      }
    }

    val result = service.calculateADX(quotes, 14)

    // Find the last non-zero ADX value
    val lastADX = result.last()
    assertTrue(lastADX > 0.0, "ADX should be calculated")
    // In choppy market, ADX should be low (below 25 typically)
    assertTrue(lastADX < 30.0, "ADX in choppy market should be low, got $lastADX")
  }

  @Test
  fun `calculateADX shows high values in strong trend`() {
    // Consistent uptrend with higher highs and higher lows every day
    val quotes = mutableListOf<StockQuote>()
    for (i in 0..40) {
      val base = 100.0 + i * 2.0
      quotes.add(createQuote(i, base, base + 1.5, base - 0.5, base - 0.3))
    }

    val result = service.calculateADX(quotes, 14)

    val lastADX = result.last()
    assertTrue(lastADX > 0.0, "ADX should be calculated")
    // In strong consistent trend, ADX should be high (above 40)
    assertTrue(lastADX > 40.0, "ADX in strong trend should be high, got $lastADX")
  }

  // ===================================================================
  // enrichWithIndicators Tests
  // ===================================================================

  @Test
  fun `enrichWithIndicators sets ATR and ADX on quotes`() {
    // Build enough quotes for ATR (15) and ADX (29) to produce values
    // Using a consistent uptrend for predictable ADX
    val quotes = (0..35).map { i ->
      val base = 100.0 + i * 2.0
      createQuote(i, base, base + 1.5, base - 0.5, base - 0.3)
    }

    val result = service.enrichWithIndicators(quotes, "TEST")

    // ATR: first meaningful value at index 14 (period=14)
    assertEquals(0.0, result[0].atr, "ATR at index 0 should be 0.0")
    assertTrue(result[14].atr > 0.0, "ATR at index 14 should be > 0, got ${result[14].atr}")
    assertTrue(result[35].atr > 0.0, "ATR at last index should be > 0, got ${result[35].atr}")

    // ADX: first meaningful value at index 27 (2*14-1), null before that
    assertEquals(null, result[0].adx, "ADX at index 0 should be null")
    assertEquals(null, result[26].adx, "ADX at index 26 should be null (0.0 maps to null)")
    assertTrue(result[27].adx != null && result[27].adx!! > 0.0, "ADX at index 27 should be > 0")
    assertTrue(result[35].adx != null && result[35].adx!! > 0.0, "ADX at last index should be > 0")

    // EMAs should also be set
    assertTrue(result[35].closePriceEMA5 > 0.0, "EMA5 should be set")
    assertTrue(result[35].closePriceEMA20 > 0.0, "EMA20 should be set")
  }

  @Test
  fun `enrichWithIndicators returns empty list for empty input`() {
    val result = service.enrichWithIndicators(emptyList(), "TEST")
    assertTrue(result.isEmpty())
  }

  // ===================================================================
  // Helper
  // ===================================================================

  private fun createQuote(
    dayOffset: Int,
    close: Double,
    high: Double,
    low: Double,
    open: Double = close,
  ) = StockQuote(
    symbol = "TEST",
    date = LocalDate.of(2024, 1, 1).plusDays(dayOffset.toLong()),
    closePrice = close,
    openPrice = open,
    high = high,
    low = low,
  )
}
