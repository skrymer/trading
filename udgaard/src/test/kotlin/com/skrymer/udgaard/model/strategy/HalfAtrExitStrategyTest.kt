package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test entry close price minus 1/2 ATR exit strategy.
 */
class HalfAtrExitStrategyTest {

  @Test
  fun `close price is below entry close price minus half ATR`(){
    val halfAtrExitStrategy = HalfAtrExitStrategy()

    // given close price is below 1/2 ATR
    val entryStockQuote = validStockQuote()
    entryStockQuote.atr = 2.0
    entryStockQuote.closePrice = 100.0

    val exitStockQuote = validStockQuote()
    exitStockQuote.closePrice = 98.9

    // then exit signal is true
    assertTrue(halfAtrExitStrategy.test(entryStockQuote, exitStockQuote))
  }

  @Test
  fun `close-price is equal to entry close-price minus half an ATR`(){
    val halfAtrExitStrategy = HalfAtrExitStrategy()

    // given close price is below 1/2 ATR
    val entryStockQuote = validStockQuote()
    entryStockQuote.atr = 2.0
    entryStockQuote.closePrice = 100.0

    val exitStockQuote = validStockQuote()
    exitStockQuote.closePrice = 99.0

    // then exit signal is false
    assertFalse(halfAtrExitStrategy.test(entryStockQuote, exitStockQuote))
  }

  @Test
  fun `close-price is above entry close-price minus half an ATR`(){
    val halfAtrExitStrategy = HalfAtrExitStrategy()

    // given an entry-quote
    val entryStockQuote = validStockQuote()
    entryStockQuote.atr = 2.0
    entryStockQuote.closePrice = 100.0

    // and an exit-quote where close-price is above entry close-price minus 1/2 ATR
    val exitStockQuote = validStockQuote()
    exitStockQuote.closePrice = 99.1

    // then exit signal is false
    assertFalse(halfAtrExitStrategy.test(entryStockQuote, exitStockQuote))
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
    high = 110.0,
    low = 5.0
  )
}

