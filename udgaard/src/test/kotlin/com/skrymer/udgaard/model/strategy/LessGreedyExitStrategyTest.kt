package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LessGreedyExitStrategyTest {

  @Test
  fun `stock is getting less greedy`(){
    val lessGreedyExitStrategy = LessGreedyExitStrategy()

    // given stock is getting more fearful
    val stockQuote = validStockQuote()
    stockQuote.previousHeatmap = 10.0
    stockQuote.heatmap = 9.9

    // then exit signal is true
    assertTrue(lessGreedyExitStrategy.match(null, stockQuote))
  }

  @Test
  fun `stock is getting greedier`(){
    val lessGreedyExitStrategy = LessGreedyExitStrategy()

    // given stock is getting greedier
    val stockQuote = validStockQuote()
    stockQuote.previousHeatmap = 10.0
    stockQuote.heatmap = 10.1

    // then exit signal is false
    assertFalse(lessGreedyExitStrategy.match(null, stockQuote))
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