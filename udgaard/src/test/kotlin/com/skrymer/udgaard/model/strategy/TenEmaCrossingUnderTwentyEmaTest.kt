package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TenEmaCrossingUnderTwentyEmaTest {

  @Test
  fun `10ema has crossed under the 20ema at close`(){
    val tenEmaCrossingUnderTwentyEma = TenEmaCrossingUnderTwentyEma()
    val exitQuote = validStockQuote()

    // given 10ema is under the 20ema at close
    exitQuote.closePriceEMA10 = 9.9
    exitQuote.closePriceEMA20 = 10.0

    // then exit signal is true
    assertTrue(tenEmaCrossingUnderTwentyEma.test(null, exitQuote))
  }

  @Test
  fun `10ema is over the 20ema at close`(){
    val tenEmaCrossingUnderTwentyEma = TenEmaCrossingUnderTwentyEma()
    val exitQuote = validStockQuote()

    // given 10ema is under the 20ema at close
    exitQuote.closePriceEMA10 = 10.0
    exitQuote.closePriceEMA20 = 9.9

    // then exit signal is true
    assertFalse(tenEmaCrossingUnderTwentyEma.test(null, exitQuote))
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
    atr = 1.0
  )
}