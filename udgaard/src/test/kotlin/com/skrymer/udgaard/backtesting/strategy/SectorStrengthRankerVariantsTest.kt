package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SectorStrengthRankerVariantsTest {
  private val today = LocalDate.of(2024, 6, 5)
  private val quote = StockQuote(date = today)
  private val stock = Stock("AAPL", sectorSymbol = "XLK")

  private fun sectorBreadth(date: LocalDate, bullPct: Double) = SectorBreadthDaily(
    sectorSymbol = "XLK",
    quoteDate = date,
    stocksInUptrend = 0,
    stocksInDowntrend = 0,
    totalStocks = 100,
    bullPercentage = bullPct,
  )

  private fun contextWith(readings: Map<LocalDate, Double>) = BacktestContext(
    sectorBreadthMap = mapOf("XLK" to readings.mapValues { (d, v) -> sectorBreadth(d, v) }),
    marketBreadthMap = emptyMap(),
  )

  // --- RollingSectorStrengthRanker ---

  @Test
  fun `rolling sector strength averages last N readings`() {
    // 5 readings: values 40, 50, 60, 70, 80 → avg of last 3 = 70
    val readings = (0..4).associate { i ->
      today.minusDays((4 - i).toLong()) to (40.0 + i * 10)
    }
    val score = RollingSectorStrengthRanker(windowDays = 3).score(stock, quote, contextWith(readings))
    assertEquals(70.0, score, 1e-9)
  }

  @Test
  fun `rolling sector strength uses available readings when history is short`() {
    // Only 2 readings, window 5 → avg of both = 45
    val readings = mapOf(
      today.minusDays(1) to 40.0,
      today to 50.0,
    )
    val score = RollingSectorStrengthRanker(windowDays = 5).score(stock, quote, contextWith(readings))
    assertEquals(45.0, score, 1e-9)
  }

  @Test
  fun `rolling sector strength handles weekend gaps`() {
    // Fri/Mon/Tue/Wed — non-consecutive dates, last 3 readings by date order
    val fri = LocalDate.of(2024, 5, 31)
    val mon = LocalDate.of(2024, 6, 3)
    val tue = LocalDate.of(2024, 6, 4)
    val wed = LocalDate.of(2024, 6, 5)
    val readings = mapOf(fri to 40.0, mon to 50.0, tue to 60.0, wed to 70.0)
    val score = RollingSectorStrengthRanker(windowDays = 3).score(stock, StockQuote(date = wed), contextWith(readings))
    // Last 3 readings: mon=50, tue=60, wed=70 → avg = 60
    assertEquals(60.0, score, 1e-9)
  }

  @Test
  fun `rolling sector strength ignores future readings`() {
    val readings = mapOf(
      today.minusDays(2) to 40.0,
      today.minusDays(1) to 50.0,
      today to 60.0,
      today.plusDays(1) to 100.0, // future — must be ignored
    )
    val score = RollingSectorStrengthRanker(windowDays = 3).score(stock, quote, contextWith(readings))
    assertEquals(50.0, score, 1e-9)
  }

  @Test
  fun `rolling sector strength returns 0 with empty context`() {
    assertEquals(0.0, RollingSectorStrengthRanker().score(stock, quote, BacktestContext.EMPTY), 1e-9)
  }

  @Test
  fun `rolling sector strength returns 0 when sector not in map`() {
    val other = Stock("XOM", sectorSymbol = "XLE")
    val readings = mapOf(today to 60.0)
    assertEquals(0.0, RollingSectorStrengthRanker().score(other, quote, contextWith(readings)), 1e-9)
  }

  // --- SectorStrengthMomentumRanker ---

  @Test
  fun `sector momentum returns delta between today and N-ago reading`() {
    // 11 readings (today + 10 prior). today=60, 10-ago=40 → momentum=20
    val readings = (0..10).associate { i ->
      today.minusDays((10 - i).toLong()) to (40.0 + i * 2)
    }
    val score = SectorStrengthMomentumRanker(windowDays = 10).score(stock, quote, contextWith(readings))
    assertEquals(20.0, score, 1e-9)
  }

  @Test
  fun `sector momentum is negative when sector weakened`() {
    val readings = (0..10).associate { i ->
      today.minusDays((10 - i).toLong()) to (70.0 - i * 3)
    }
    // today = 70 - 10*3 = 40, 10-ago = 70 → momentum = 40 - 70 = -30
    val score = SectorStrengthMomentumRanker(windowDays = 10).score(stock, quote, contextWith(readings))
    assertEquals(-30.0, score, 1e-9)
  }

  @Test
  fun `sector momentum handles weekend gaps by counting trading-day readings`() {
    val fri = LocalDate.of(2024, 5, 31)
    val mon = LocalDate.of(2024, 6, 3)
    val tue = LocalDate.of(2024, 6, 4)
    val wed = LocalDate.of(2024, 6, 5)
    // 4 readings, window=3 → today=wed=80, 3-ago=fri=50 → momentum=30
    val readings = mapOf(fri to 50.0, mon to 60.0, tue to 70.0, wed to 80.0)
    val score = SectorStrengthMomentumRanker(windowDays = 3).score(stock, StockQuote(date = wed), contextWith(readings))
    assertEquals(30.0, score, 1e-9)
  }

  @Test
  fun `sector momentum returns 0 when insufficient history`() {
    // Only 5 readings, window=10 → not enough history → 0
    val readings = (0..4).associate { i ->
      today.minusDays((4 - i).toLong()) to (40.0 + i * 2)
    }
    val score = SectorStrengthMomentumRanker(windowDays = 10).score(stock, quote, contextWith(readings))
    assertEquals(0.0, score, 1e-9)
  }

  @Test
  fun `sector momentum returns 0 with empty context`() {
    assertEquals(0.0, SectorStrengthMomentumRanker().score(stock, quote, BacktestContext.EMPTY), 1e-9)
  }

  // --- Factory wiring ---

  @Test
  fun `factory wires new rankers by name`() {
    assertEquals(
      RollingSectorStrengthRanker::class,
      RankerFactory.create("RollingSectorStrength")?.let { it::class },
    )
    assertEquals(
      SectorStrengthMomentumRanker::class,
      RankerFactory.create("SectorStrengthMomentum")?.let { it::class },
    )
  }

  @Test
  fun `factory listing includes both new rankers`() {
    val names = RankerFactory.availableRankers()
    assert(names.contains("RollingSectorStrength"))
    assert(names.contains("SectorStrengthMomentum"))
  }
}
