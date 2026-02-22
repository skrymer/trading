package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BreadthEntryConditionsTest {
  private val today = LocalDate.of(2024, 6, 5)
  private val yesterday = LocalDate.of(2024, 6, 4)
  private val stock = Stock("AAPL", sectorSymbol = "XLK")
  private val quote = StockQuote(date = today)

  private fun marketBreadth(
    date: LocalDate,
    breadth: Double,
    ema5: Double = 0.0,
    ema10: Double = 0.0,
    ema20: Double = 0.0,
    donchianUpper: Double = 0.0,
    donchianLower: Double = 0.0,
  ) = MarketBreadthDaily(
    quoteDate = date,
    breadthPercent = breadth,
    ema5 = ema5,
    ema10 = ema10,
    ema20 = ema20,
    donchianUpperBand = donchianUpper,
    donchianLowerBand = donchianLower,
  )

  private fun sectorBreadth(
    date: LocalDate,
    bullPct: Double,
    ema5: Double = 0.0,
    ema10: Double = 0.0,
    ema20: Double = 0.0,
  ) = SectorBreadthDaily(
    sectorSymbol = "XLK",
    quoteDate = date,
    stocksInUptrend = 0,
    stocksInDowntrend = 0,
    totalStocks = 100,
    bullPercentage = bullPct,
    ema5 = ema5,
    ema10 = ema10,
    ema20 = ema20,
  )

  // --- MarketBreadthEmaAlignmentCondition ---

  @Test
  fun `market breadth ema alignment passes when ema5 gt ema10 gt ema20`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(today to marketBreadth(today, 60.0, ema5 = 58.0, ema10 = 55.0, ema20 = 50.0)),
    )
    assertTrue(MarketBreadthEmaAlignmentCondition().evaluate(stock, quote, context))
  }

  @Test
  fun `market breadth ema alignment fails when not aligned`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(today to marketBreadth(today, 60.0, ema5 = 50.0, ema10 = 55.0, ema20 = 52.0)),
    )
    assertFalse(MarketBreadthEmaAlignmentCondition().evaluate(stock, quote, context))
  }

  // --- MarketBreadthRecoveringCondition ---

  @Test
  fun `market breadth recovering passes on crossover`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 52.0, ema10 = 50.0),
        yesterday to marketBreadth(yesterday, 48.0, ema10 = 50.0),
      ),
    )
    assertTrue(MarketBreadthRecoveringCondition().evaluate(stock, quote, context))
  }

  @Test
  fun `market breadth recovering fails when already above`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 55.0, ema10 = 50.0),
        yesterday to marketBreadth(yesterday, 52.0, ema10 = 50.0),
      ),
    )
    assertFalse(MarketBreadthRecoveringCondition().evaluate(stock, quote, context))
  }

  @Test
  fun `market breadth recovering fails when still below`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 48.0, ema10 = 50.0),
        yesterday to marketBreadth(yesterday, 45.0, ema10 = 50.0),
      ),
    )
    assertFalse(MarketBreadthRecoveringCondition().evaluate(stock, quote, context))
  }

  // --- MarketBreadthNearDonchianLowCondition ---

  @Test
  fun `near donchian low passes when breadth in bottom 10 pct of range`() {
    // Range: 30-80, 10% threshold = 30 + 50*0.10 = 35
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 33.0, donchianUpper = 80.0, donchianLower = 30.0),
      ),
    )
    assertTrue(MarketBreadthNearDonchianLowCondition(0.10).evaluate(stock, quote, context))
  }

  @Test
  fun `near donchian low fails when breadth above threshold`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 50.0, donchianUpper = 80.0, donchianLower = 30.0),
      ),
    )
    assertFalse(MarketBreadthNearDonchianLowCondition(0.10).evaluate(stock, quote, context))
  }

  @Test
  fun `near donchian low fails when range is zero`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 50.0, donchianUpper = 50.0, donchianLower = 50.0),
      ),
    )
    assertFalse(MarketBreadthNearDonchianLowCondition(0.10).evaluate(stock, quote, context))
  }

  // --- SectorBreadthAboveCondition ---

  @Test
  fun `sector breadth above passes when above threshold`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf("XLK" to mapOf(today to sectorBreadth(today, 60.0))),
      marketBreadthMap = emptyMap(),
    )
    assertTrue(SectorBreadthAboveCondition(50.0).evaluate(stock, quote, context))
  }

  @Test
  fun `sector breadth above fails when below threshold`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf("XLK" to mapOf(today to sectorBreadth(today, 40.0))),
      marketBreadthMap = emptyMap(),
    )
    assertFalse(SectorBreadthAboveCondition(50.0).evaluate(stock, quote, context))
  }

  // --- SectorBreadthEmaAlignmentCondition ---

  @Test
  fun `sector breadth ema alignment passes when aligned`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf("XLK" to mapOf(today to sectorBreadth(today, 60.0, ema5 = 58.0, ema10 = 55.0, ema20 = 50.0))),
      marketBreadthMap = emptyMap(),
    )
    assertTrue(SectorBreadthEmaAlignmentCondition().evaluate(stock, quote, context))
  }

  @Test
  fun `sector breadth ema alignment fails when not aligned`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf("XLK" to mapOf(today to sectorBreadth(today, 60.0, ema5 = 48.0, ema10 = 55.0, ema20 = 50.0))),
      marketBreadthMap = emptyMap(),
    )
    assertFalse(SectorBreadthEmaAlignmentCondition().evaluate(stock, quote, context))
  }

  // --- SectorBreadthAcceleratingCondition ---

  @Test
  fun `sector breadth accelerating passes when spread exceeds threshold`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf("XLK" to mapOf(today to sectorBreadth(today, 60.0, ema5 = 60.0, ema20 = 50.0))),
      marketBreadthMap = emptyMap(),
    )
    assertTrue(SectorBreadthAcceleratingCondition(5.0).evaluate(stock, quote, context))
  }

  @Test
  fun `sector breadth accelerating fails when spread too small`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf("XLK" to mapOf(today to sectorBreadth(today, 60.0, ema5 = 53.0, ema20 = 50.0))),
      marketBreadthMap = emptyMap(),
    )
    assertFalse(SectorBreadthAcceleratingCondition(5.0).evaluate(stock, quote, context))
  }

  @Test
  fun `sector breadth accelerating fails when ema5 below ema20`() {
    val context = BacktestContext(
      sectorBreadthMap = mapOf("XLK" to mapOf(today to sectorBreadth(today, 60.0, ema5 = 45.0, ema20 = 50.0))),
      marketBreadthMap = emptyMap(),
    )
    assertFalse(SectorBreadthAcceleratingCondition(5.0).evaluate(stock, quote, context))
  }

  // --- MarketBreadthTrendingCondition ---

  @Test
  fun `market breadth trending passes when donchian wide and breadth above ema10`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 55.0, ema10 = 50.0, donchianUpper = 75.0, donchianLower = 30.0),
      ),
    )
    assertTrue(MarketBreadthTrendingCondition(20.0).evaluate(stock, quote, context))
  }

  @Test
  fun `market breadth trending fails when donchian wide but breadth below ema10`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 45.0, ema10 = 50.0, donchianUpper = 75.0, donchianLower = 30.0),
      ),
    )
    assertFalse(MarketBreadthTrendingCondition(20.0).evaluate(stock, quote, context))
  }

  @Test
  fun `market breadth trending fails when donchian narrow`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = mapOf(
        today to marketBreadth(today, 55.0, ema10 = 50.0, donchianUpper = 60.0, donchianLower = 50.0),
      ),
    )
    assertFalse(MarketBreadthTrendingCondition(20.0).evaluate(stock, quote, context))
  }

  @Test
  fun `market breadth trending fails when no breadth data`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = emptyMap(),
    )
    assertFalse(MarketBreadthTrendingCondition(20.0).evaluate(stock, quote, context))
  }

  // --- SpyPriceUptrendCondition ---

  @Test
  fun `spy price uptrend passes when SPY is in uptrend`() {
    val spyQuote = StockQuote(symbol = "SPY", date = today, closePrice = 500.0, trend = "Uptrend")
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = emptyMap(),
      spyQuoteMap = mapOf(today to spyQuote),
    )
    assertTrue(SpyPriceUptrendCondition().evaluate(stock, quote, context))
  }

  @Test
  fun `spy price uptrend fails when SPY is in downtrend`() {
    val spyQuote = StockQuote(symbol = "SPY", date = today, closePrice = 450.0, trend = "Downtrend")
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = emptyMap(),
      spyQuoteMap = mapOf(today to spyQuote),
    )
    assertFalse(SpyPriceUptrendCondition().evaluate(stock, quote, context))
  }

  @Test
  fun `spy price uptrend fails when no SPY data`() {
    val context = BacktestContext(
      sectorBreadthMap = emptyMap(),
      marketBreadthMap = emptyMap(),
      spyQuoteMap = emptyMap(),
    )
    assertFalse(SpyPriceUptrendCondition().evaluate(stock, quote, context))
  }

  // --- All conditions return false with empty context ---

  @Test
  fun `all breadth conditions return false with empty context`() {
    val emptyContext = BacktestContext.EMPTY
    assertFalse(MarketBreadthEmaAlignmentCondition().evaluate(stock, quote, emptyContext))
    assertFalse(MarketBreadthRecoveringCondition().evaluate(stock, quote, emptyContext))
    assertFalse(MarketBreadthNearDonchianLowCondition().evaluate(stock, quote, emptyContext))
    assertFalse(MarketBreadthTrendingCondition().evaluate(stock, quote, emptyContext))
    assertFalse(SectorBreadthAboveCondition().evaluate(stock, quote, emptyContext))
    assertFalse(SectorBreadthEmaAlignmentCondition().evaluate(stock, quote, emptyContext))
    assertFalse(SectorBreadthAcceleratingCondition().evaluate(stock, quote, emptyContext))
    assertFalse(SpyPriceUptrendCondition().evaluate(stock, quote, emptyContext))
  }
}
