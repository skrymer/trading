package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class Ovtlyr9EntryStrategyTest {

  @Test
  fun `should pass Ovtlyr9 entry strategy when all criteria are mett`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given a stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    
    // then quote matches the strategy
    assertTrue(ovtlyr9EntryStrategy.test(stockQuote))
  }

  //    @Test
  fun `should pass Ovtlyr 9 entry strategy when last buy signal was on a Friday and its Monday`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    
    // then quote matches the strategy
    assertTrue(ovtlyr9EntryStrategy.test(stockQuote))
  }

  @Test
  fun `should not match entry strategy when stock is in a downTrend`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    // and stock in a downtrend
    stockQuote.trend = "Downtrend"

    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }

  @Test
  fun `should not m atch entry strategy when spy is in a downtrend`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    // and spy is in a downtrend
    stockQuote.spyInUptrend = false

    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }

  @Test
  fun `should not match strategy when spy has no buy signal`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    // and SPY has no Buy signal
    stockQuote.spySignal = "Sell"
    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }

  @Test
  fun `should not match entry strategy when sector is in a downtrend`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    // and sector is in a downtrend
    stockQuote.sectorIsInUptrend = false
    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }

  @Test
  fun `should not match strategy when sector heatmap is getting more fearful`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    // and sector heatmap value is decreasing
    stockQuote.sectorHeatmap = 2.9
    stockQuote.previousSectorHeatmap = 3.0
    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }
  
  // What if there is a buy signal on a Friday and the quote is from the following Monday?
  @Test
  fun `should not match strategy when stock has no buy signal from within the last 2 days`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    // and Last buy signal is more recent than last sell but older than 2 days
    stockQuote.date = LocalDate.of(2025, 6, 16)
    stockQuote.lastBuySignal = LocalDate.of(2025, 6, 14)
    stockQuote.lastSellSignal = LocalDate.of(2025, 6, 13)
    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }

  @Test
  fun `should not match entry strategy when current sell signal is more recent than the buy signal`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    stockQuote.date = LocalDate.of(2025, 6, 16)
    // and last buy signal was the day before the quote date
    stockQuote.lastBuySignal = LocalDate.of(2025, 6, 15)
    // and Last sell signal was same day as quote date
    stockQuote.lastSellSignal = LocalDate.of(2025, 6, 16)
    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }

  @Test
  fun `should not match entry strategy when stock heatmap is getting more fearful`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    // and stock heatmap value is decreasing
    stockQuote.heatmap = 2.9
    stockQuote.previousHeatmap = 3.0
    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }

  @Test
  fun `should not match entry strategy when close price is below the 10EMA`() {
    val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

    // given stock quote that matches the ovtlyr 9 entry strategy
    val stockQuote = ovtlyr9MatchingQuote()
    // and close price is lower than 10 EMA
    stockQuote.closePrice = 100.0
    stockQuote.closePriceEMA10 = 101.0
    // then quote does not match entry strategy
    assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
  }

  /**
   *
   */
  private fun ovtlyr9MatchingQuote() =
    StockQuote(
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
      high = 115.9,
      low = 55.4
    )
}