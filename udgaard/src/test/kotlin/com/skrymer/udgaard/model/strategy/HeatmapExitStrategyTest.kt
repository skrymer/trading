package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class HeatmapExitStrategyTest {

  /**
   * Test that exit signal is true
   *  when entry heatmap value is below 50
   *  and current heatmap is above 63
   */
  @Test
  fun `entry heatmap value is less than 50, and current value is above 63`() {
    val heatmapExitStrategy = HeatmapExitStrategy()

    // given entry heatmap value is below 50
    val entryQuote = validStockQuote()
    entryQuote.heatmap = 49.9

    // and exit quote heatmap value is above 63
    val exitQuote = validStockQuote()
    exitQuote.heatmap = 63.1

    // then exit signal is true
    assertTrue(heatmapExitStrategy.match(entryQuote, exitQuote))
  }

  /**
   * Test that exit signal is false
   *  when entry heatmap value is below 50
   *  and current heatmap is not above 63
   */
  @Test
  fun `entry heatmap value is less than 50, and current value is not above 63`() {
    val heatmapExitStrategy = HeatmapExitStrategy()

    // given entry heatmap value is below 50
    val entryQuote = validStockQuote()
    entryQuote.heatmap = 49.9

    // and exit quote heatmap value is below 63
    val exitQuote = validStockQuote()
    exitQuote.heatmap = 62.9

    // then exit signal is false
    assertFalse(heatmapExitStrategy.match(entryQuote, exitQuote))
  }

  /**
   * Test that exit signal is true
   *  when entry heatmap value is between 50 and 75
   *  and current heatmap is 10 pt higher
   */
  @ParameterizedTest
  @CsvSource("50.0, 60.0", "60, 70", "75, 85")
  fun `entry heatmap value is between 50 and 75, and current value is 10pt higher`(entryHeatmap: Double, exitHeatmap: Double) {
    val heatmapExitStrategy = HeatmapExitStrategy()

    // given entry heatmap value is 50
    val entryQuote = validStockQuote()
    entryQuote.heatmap = entryHeatmap

    // and exit quote heatmap value is 10pt higher than entry quote heatmap value
    val exitQuote = validStockQuote()
    exitQuote.heatmap = exitHeatmap

    // then exit signal is true
    assertTrue(heatmapExitStrategy.match(entryQuote, exitQuote))
  }

  /**
   * Test that exit signal is false
   *  when entry heatmap value is between 50 and 75
   *  and current heatmap is not 10 pt higher
   */
  @ParameterizedTest
  @CsvSource("50.0, 59.9", "60, 69.9", "75, 84.9")
  fun `entry heatmap value is between 50 and 75, and current value is not 10pt higher`(entryHeatmap: Double, exitHeatmap: Double) {
    val heatmapExitStrategy = HeatmapExitStrategy()

    // given entry heatmap value is 50
    val entryQuote = validStockQuote()
    entryQuote.heatmap = entryHeatmap

    // and exit quote heatmap value is 10pt higher than entry quote heatmap value
    val exitQuote = validStockQuote()
    exitQuote.heatmap = exitHeatmap

    // then exit signal is false
    assertFalse(heatmapExitStrategy.match(entryQuote, exitQuote))
  }

  /**
   * Test that exit signal is true
   *  when entry heatmap value is between > 75
   *  and current heatmap is 5 pt higher
   */
  @ParameterizedTest
  @CsvSource("75.1, 80.1", "75.1, 82.0", "76.0, 81.0")
  fun `entry heatmap value is larger than 75, and current value is 5pt higher`(entryHeatmap: Double, exitHeatmap: Double) {
    val heatmapExitStrategy = HeatmapExitStrategy()

    // given entry heatmap value is > 75
    val entryQuote = validStockQuote()
    entryQuote.heatmap = entryHeatmap

    // and exit quote heatmap value is 5pt higher than entry quote heatmap value
    val exitQuote = validStockQuote()
    exitQuote.heatmap = exitHeatmap

    // then exit signal is true
    assertTrue(heatmapExitStrategy.match(entryQuote, exitQuote))
  }

  /**
   * Test that exit signal is false
   *  when entry heatmap value is between > 75
   *  and current heatmap is not 5 pt higher
   */
  @ParameterizedTest
  @CsvSource("75.1, 80.0", "76.0, 80.0")
  fun `entry heatmap value is larger than 75, and current value is not 5pt higher`(entryHeatmap: Double, exitHeatmap: Double) {
    val heatmapExitStrategy = HeatmapExitStrategy()

    // given entry heatmap value is > 75
    val entryQuote = validStockQuote()
    entryQuote.heatmap = entryHeatmap

    // and exit quote heatmap value is not 5pt higher than entry quote heatmap value
    val exitQuote = validStockQuote()
    exitQuote.heatmap = exitHeatmap

    // then exit signal is false
    assertFalse(heatmapExitStrategy.match(entryQuote, exitQuote))
  }

  fun validStockQuote() = StockQuote(
    symbol = "TEST",
    date = LocalDate.of(2025, 6, 16),
    openPrice = 110.0,
    // Heatmap value is going up
    heatmap = 2.0,
    previousHeatmap = 1.0,
    // Sector heatmap value is going up
    sectorHeatmap = 2.0,
    previousSectorHeatmap = 1.0,
    // Sector is in an uptrend
    sectorIsInUptrend = true,
    signal = "Buy",
    // Close price is higher than 10 EMA
    closePrice = 100.0,
    closePriceEMA10 = 99.0,
    closePriceEMA5 = 10.0,
    closePriceEMA20 = 10.0,
    closePriceEMA50 = 99.0,
    // Stock is in an uptrend
    trend = "Uptrend",
    // Last buy signal is more recent than last sell signal
    lastBuySignal = LocalDate.of(2025, 6, 16),
    lastSellSignal = LocalDate.of(2025, 6, 15),
    // SPY has a buy signal
    spySignal = "Buy",
    // Spy is in an uptrend
    spyIsInUptrend = true,
    // Market is in an uptrend
    marketIsInUptrend = true,
    previousQuoteDate = LocalDate.now(),
    atr = 1.0,
    sectorStocksInUptrend = 10,
    sectorStocksInDowntrend = 5,
    sectorBullPercentage = 75.0,
    high = 115.0,
    low = 55.6
  )

}