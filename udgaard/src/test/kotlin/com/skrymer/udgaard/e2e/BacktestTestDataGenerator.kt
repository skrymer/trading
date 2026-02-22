package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.jooq.tables.references.EARNINGS
import com.skrymer.udgaard.jooq.tables.references.MARKET_BREADTH_DAILY
import com.skrymer.udgaard.jooq.tables.references.ORDER_BLOCKS
import com.skrymer.udgaard.jooq.tables.references.SECTOR_BREADTH_DAILY
import com.skrymer.udgaard.jooq.tables.references.STOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import com.skrymer.udgaard.jooq.tables.references.SYMBOLS
import org.jooq.BatchBindStep
import org.jooq.DSLContext
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Generates realistic test data for backtesting E2E tests.
 *
 * Creates 50 stocks across 11 sectors with ~60 trading days of quote data,
 * plus matching market breadth and sector breadth data.
 */
@Suppress("LongMethod")
object BacktestTestDataGenerator {
  private val random = Random(42) // Fixed seed for reproducibility

  private val SECTOR_STOCKS = mapOf(
    "XLK" to listOf("AAPL", "MSFT", "NVDA", "AVGO", "CRM"),
    "XLF" to listOf("JPM", "BAC", "WFC", "GS", "MS"),
    "XLV" to listOf("UNH", "JNJ", "PFE", "ABBV", "MRK"),
    "XLI" to listOf("CAT", "DE", "UNP", "HON", "GE"),
    "XLY" to listOf("AMZN", "TSLA", "HD", "NKE", "SBUX"),
    "XLE" to listOf("XOM", "CVX", "COP", "SLB", "EOG"),
    "XLP" to listOf("PG", "KO", "PEP", "COST", "WMT"),
    "XLC" to listOf("META", "GOOG", "NFLX", "DIS", "CMCSA"),
    "XLB" to listOf("LIN", "APD", "SHW", "FCX", "NEM"),
    "XLRE" to listOf("AMT", "PLD", "CCI", "EQIX", "SPG"),
    "XLU" to listOf("NEE", "DUK", "SO", "D", "AEP"),
  )

  val ALL_SECTORS = SECTOR_STOCKS.keys.toList()
  val ALL_SYMBOLS = SECTOR_STOCKS.values.flatten()

  fun populate(dsl: DSLContext) {
    val tradingDays = generateTradingDays(
      LocalDate.of(2024, 1, 2),
      LocalDate.of(2024, 3, 29),
    )

    insertSymbols(dsl)
    insertStocksAndQuotes(dsl, tradingDays)
    insertEarnings(dsl, tradingDays)
    insertOrderBlocks(dsl, tradingDays)
    insertMarketBreadth(dsl, tradingDays)
    insertSectorBreadth(dsl, tradingDays)
  }

  private fun generateTradingDays(start: LocalDate, end: LocalDate): List<LocalDate> =
    generateSequence(start) { it.plusDays(1) }
      .takeWhile { !it.isAfter(end) }
      .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
      .toList()

  private fun insertSymbols(dsl: DSLContext) {
    // Symbols may already exist from V2 migration - use ON CONFLICT DO NOTHING
    (ALL_SYMBOLS + "SPY").forEach { symbol ->
      val assetType = if (symbol == "SPY") "INDEX" else "STOCK"
      dsl
        .insertInto(SYMBOLS)
        .set(SYMBOLS.SYMBOL, symbol)
        .set(SYMBOLS.ASSET_TYPE, assetType)
        .onConflict(SYMBOLS.SYMBOL)
        .doNothing()
        .execute()
    }
  }

  private fun insertStocksAndQuotes(dsl: DSLContext, tradingDays: List<LocalDate>) {
    insertStock(dsl, "SPY", null, tradingDays, basePrice = 470.0, volatility = 0.008)
    SECTOR_STOCKS.forEach { (sector, symbols) ->
      symbols.forEach { symbol ->
        val basePrice = 50.0 + random.nextDouble() * 300.0
        val volatility = 0.005 + random.nextDouble() * 0.015
        insertStock(dsl, symbol, sector, tradingDays, basePrice, volatility)
      }
    }
  }

  private fun insertStock(
    dsl: DSLContext,
    symbol: String,
    sectorSymbol: String?,
    tradingDays: List<LocalDate>,
    basePrice: Double,
    volatility: Double,
  ) {
    dsl
      .insertInto(STOCKS)
      .set(STOCKS.SYMBOL, symbol)
      .set(STOCKS.SECTOR_SYMBOL, sectorSymbol)
      .execute()

    val quotes = generateQuotes(symbol, tradingDays, basePrice, volatility)
    val batchInsert = createQuoteBatchInsert(dsl)
    quotes.forEach { bindQuote(batchInsert, it) }
    batchInsert.execute()
  }

  private fun createQuoteBatchInsert(dsl: DSLContext): BatchBindStep = dsl.batch(
    dsl
      .insertInto(STOCK_QUOTES)
      .columns(
        STOCK_QUOTES.STOCK_SYMBOL,
        STOCK_QUOTES.QUOTE_DATE,
        STOCK_QUOTES.OPEN_PRICE,
        STOCK_QUOTES.CLOSE_PRICE,
        STOCK_QUOTES.HIGH_PRICE,
        STOCK_QUOTES.LOW_PRICE,
        STOCK_QUOTES.VOLUME,
        STOCK_QUOTES.ATR,
        STOCK_QUOTES.ADX,
        STOCK_QUOTES.DONCHIAN_HIGH,
        STOCK_QUOTES.DONCHIAN_MID,
        STOCK_QUOTES.DONCHIAN_LOW,
        STOCK_QUOTES.DONCHIAN_UPPER_BAND,
        STOCK_QUOTES.DONCHIAN_CHANNEL_SCORE,
        STOCK_QUOTES.IN_UPTREND,
        STOCK_QUOTES.BUY_SIGNAL,
        STOCK_QUOTES.SELL_SIGNAL,
        STOCK_QUOTES.CLOSE_PRICE_EMA5,
        STOCK_QUOTES.CLOSE_PRICE_EMA10,
        STOCK_QUOTES.CLOSE_PRICE_EMA20,
        STOCK_QUOTES.CLOSE_PRICE_EMA50,
        STOCK_QUOTES.CLOSE_PRICE_EMA100,
        STOCK_QUOTES.CLOSE_PRICE_EMA200,
        STOCK_QUOTES.TREND,
      ).values(
        null as String?,
        null as LocalDate?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as Long?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as Int?,
        null as Boolean?,
        null as Boolean?,
        null as Boolean?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as String?,
      ),
  )

  private fun bindQuote(batch: BatchBindStep, q: GeneratedQuote) {
    batch.bind(
      q.symbol,
      q.date,
      q.open.toBd(),
      q.close.toBd(),
      q.high.toBd(),
      q.low.toBd(),
      q.volume,
      q.atr.toBd(),
      q.adx.toBd(),
      q.donchianHigh.toBd(),
      q.donchianMid.toBd(),
      q.donchianLow.toBd(),
      q.donchianUpperBand.toBd(),
      q.donchianChannelScore,
      q.inUptrend,
      false,
      false,
      q.ema5.toBd(),
      q.ema10.toBd(),
      q.ema20.toBd(),
      q.ema50.toBd(),
      q.ema100.toBd(),
      q.ema200.toBd(),
      q.trend,
    )
  }

  private fun Double.toBd(): BigDecimal =
    BigDecimal.valueOf(this).setScale(4, java.math.RoundingMode.HALF_UP)

  private data class GeneratedQuote(
    val symbol: String,
    val date: LocalDate,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Long,
    val atr: Double,
    val adx: Double,
    val ema5: Double,
    val ema10: Double,
    val ema20: Double,
    val ema50: Double,
    val ema100: Double,
    val ema200: Double,
    val trend: String?,
    val inUptrend: Boolean,
    val donchianHigh: Double,
    val donchianMid: Double,
    val donchianLow: Double,
    val donchianUpperBand: Double,
    val donchianChannelScore: Int,
  )

  private class PriceTracker {
    val closePrices = mutableListOf<Double>()
    val highs = mutableListOf<Double>()
    val lows = mutableListOf<Double>()
    val trueRanges = mutableListOf<Double>()

    fun addDay(close: Double, high: Double, low: Double, index: Int) {
      val prevClose = closePrices.lastOrNull()
      closePrices.add(close)
      highs.add(high)
      lows.add(low)

      val tr = if (index == 0 || prevClose == null) {
        high - low
      } else {
        maxOf(high - low, abs(high - prevClose), abs(low - prevClose))
      }
      trueRanges.add(tr)
    }

    fun atr(): Double = if (trueRanges.size >= 14) {
      trueRanges.takeLast(14).average()
    } else {
      trueRanges.average()
    }

    fun donchian(): Triple<Double, Double, Double> {
      val lookback = min(20, highs.size)
      val high = highs.takeLast(lookback).max()
      val low = lows.takeLast(lookback).min()
      return Triple(high, (high + low) / 2, low)
    }
  }

  private fun generateQuotes(
    symbol: String,
    tradingDays: List<LocalDate>,
    basePrice: Double,
    volatility: Double,
  ): List<GeneratedQuote> {
    val tracker = PriceTracker()
    var price = basePrice

    return tradingDays.mapIndexed { index, day ->
      val dailyReturn = (random.nextGaussian() * volatility) + 0.0003
      val close = price * (1 + dailyReturn)
      val open = price
      val high = max(open, close) * (1 + abs(random.nextGaussian() * volatility * 0.5))
      val low = min(open, close) * (1 - abs(random.nextGaussian() * volatility * 0.5))

      tracker.addDay(close, high, low, index)
      val quote = buildQuote(symbol, day, open, close, high, low, tracker)
      price = close
      quote
    }
  }

  private fun buildQuote(
    symbol: String,
    day: LocalDate,
    open: Double,
    close: Double,
    high: Double,
    low: Double,
    tracker: PriceTracker,
  ): GeneratedQuote {
    val ema5 = calculateEma(tracker.closePrices, 5)
    val ema10 = calculateEma(tracker.closePrices, 10)
    val ema20 = calculateEma(tracker.closePrices, 20)
    val ema50 = calculateEma(tracker.closePrices, 50)
    val inUptrend = ema5 > ema10 && ema10 > ema20 && close > ema50
    val (donchianHigh, donchianMid, donchianLow) = tracker.donchian()
    val channelRange = donchianHigh - donchianLow
    val channelScore = if (channelRange > 0) {
      ((close - donchianLow) / channelRange * 4).toInt().coerceIn(0, 4)
    } else {
      2
    }

    return GeneratedQuote(
      symbol = symbol,
      date = day,
      open = open,
      close = close,
      high = high,
      low = low,
      volume = 1_000_000L + abs(random.nextLong()) % 5_000_000L,
      atr = tracker.atr(),
      adx = 15.0 + random.nextDouble() * 35.0,
      ema5 = ema5,
      ema10 = ema10,
      ema20 = ema20,
      ema50 = ema50,
      ema100 = calculateEma(tracker.closePrices, 100),
      ema200 = calculateEma(tracker.closePrices, 200),
      trend = if (inUptrend) "Uptrend" else null,
      inUptrend = inUptrend,
      donchianHigh = donchianHigh,
      donchianMid = donchianMid,
      donchianLow = donchianLow,
      donchianUpperBand = donchianHigh,
      donchianChannelScore = channelScore,
    )
  }

  private fun calculateEma(prices: List<Double>, period: Int): Double {
    if (prices.isEmpty()) return 0.0
    if (prices.size <= period) return prices.average()
    val multiplier = 2.0 / (period + 1)
    var ema = prices.take(period).average()
    for (i in period until prices.size) {
      ema = (prices[i] - ema) * multiplier + ema
    }
    return ema
  }

  private fun insertEarnings(dsl: DSLContext, tradingDays: List<LocalDate>) {
    // Place earnings for ~30% of stocks: some within the date range (to test
    // noEarningsWithinDays / exitBeforeEarnings) and some outside.
    val stocksWithEarnings = ALL_SYMBOLS.filterIndexed { i, _ -> i % 3 == 0 }
    for (symbol in stocksWithEarnings) {
      // Past earnings (well before the test range) — should not block entry
      dsl
        .insertInto(EARNINGS)
        .set(EARNINGS.STOCK_SYMBOL, symbol)
        .set(EARNINGS.SYMBOL, symbol)
        .set(EARNINGS.FISCAL_DATE_ENDING, LocalDate.of(2023, 10, 15))
        .set(EARNINGS.REPORTED_DATE, LocalDate.of(2023, 10, 20))
        .set(EARNINGS.REPORTEDEPS, 1.25.toBd())
        .set(EARNINGS.ESTIMATEDEPS, 1.20.toBd())
        .set(EARNINGS.SURPRISE, 0.05.toBd())
        .set(EARNINGS.SURPRISE_PERCENTAGE, 4.17.toBd())
        .set(EARNINGS.REPORT_TIME, "after-hours")
        .execute()

      // Future earnings during test range — triggers exitBeforeEarnings
      val earningsDate = tradingDays[tradingDays.size * 2 / 3] // ~day 40 of 60
      dsl
        .insertInto(EARNINGS)
        .set(EARNINGS.STOCK_SYMBOL, symbol)
        .set(EARNINGS.SYMBOL, symbol)
        .set(EARNINGS.FISCAL_DATE_ENDING, earningsDate)
        .set(EARNINGS.REPORTED_DATE, earningsDate)
        .set(EARNINGS.REPORTEDEPS, 1.50.toBd())
        .set(EARNINGS.ESTIMATEDEPS, 1.45.toBd())
        .set(EARNINGS.SURPRISE, 0.05.toBd())
        .set(EARNINGS.SURPRISE_PERCENTAGE, 3.45.toBd())
        .set(EARNINGS.REPORT_TIME, "before-market")
        .execute()
    }
  }

  private fun insertOrderBlocks(dsl: DSLContext, tradingDays: List<LocalDate>) {
    // Place bearish and bullish order blocks for ~40% of stocks at various price levels
    val stocksWithBlocks = ALL_SYMBOLS.filterIndexed { i, _ -> i % 5 < 2 }
    for (symbol in stocksWithBlocks) {
      val basePrice = 50.0 + abs(symbol.hashCode() % 300)
      // Bearish order block above current price range — tests bearishOrderBlock exit
      dsl
        .insertInto(ORDER_BLOCKS)
        .set(ORDER_BLOCKS.STOCK_SYMBOL, symbol)
        .set(ORDER_BLOCKS.TYPE, "BEARISH")
        .set(ORDER_BLOCKS.SENSITIVITY, "NORMAL")
        .set(ORDER_BLOCKS.START_DATE, tradingDays[5])
        .set(ORDER_BLOCKS.END_DATE, tradingDays[8])
        .set(ORDER_BLOCKS.START_PRICE, (basePrice * 1.15).toBd())
        .set(ORDER_BLOCKS.END_PRICE, (basePrice * 1.20).toBd())
        .set(ORDER_BLOCKS.LOW_PRICE, (basePrice * 1.14).toBd())
        .set(ORDER_BLOCKS.HIGH_PRICE, (basePrice * 1.21).toBd())
        .set(ORDER_BLOCKS.VOLUME, 2_500_000L)
        .set(ORDER_BLOCKS.VOLUME_STRENGTH, 1.5.toBd())
        .set(ORDER_BLOCKS.RATE_OF_CHANGE, (-3.5).toBd())
        .set(ORDER_BLOCKS.IS_ACTIVE, true)
        .execute()

      // Bullish order block below current price — tests notInOrderBlock entry filter
      dsl
        .insertInto(ORDER_BLOCKS)
        .set(ORDER_BLOCKS.STOCK_SYMBOL, symbol)
        .set(ORDER_BLOCKS.TYPE, "BULLISH")
        .set(ORDER_BLOCKS.SENSITIVITY, "NORMAL")
        .set(ORDER_BLOCKS.START_DATE, tradingDays[2])
        .set(ORDER_BLOCKS.END_DATE, tradingDays[4])
        .set(ORDER_BLOCKS.START_PRICE, (basePrice * 0.85).toBd())
        .set(ORDER_BLOCKS.END_PRICE, (basePrice * 0.90).toBd())
        .set(ORDER_BLOCKS.LOW_PRICE, (basePrice * 0.84).toBd())
        .set(ORDER_BLOCKS.HIGH_PRICE, (basePrice * 0.91).toBd())
        .set(ORDER_BLOCKS.VOLUME, 3_000_000L)
        .set(ORDER_BLOCKS.VOLUME_STRENGTH, 1.8.toBd())
        .set(ORDER_BLOCKS.RATE_OF_CHANGE, 4.2.toBd())
        .set(ORDER_BLOCKS.IS_ACTIVE, true)
        .execute()
    }
  }

  private fun insertMarketBreadth(dsl: DSLContext, tradingDays: List<LocalDate>) {
    val breadthValues = mutableListOf<Double>()
    val batchInsert = createMarketBreadthBatch(dsl)

    var breadth = 65.0
    for (day in tradingDays) {
      breadth = (breadth + random.nextGaussian() * 3.0).coerceIn(40.0, 90.0)
      breadthValues.add(breadth)
      bindBreadthRow(batchInsert, day, breadth, breadthValues)
    }
    batchInsert.execute()
  }

  private fun createMarketBreadthBatch(dsl: DSLContext): BatchBindStep = dsl.batch(
    dsl
      .insertInto(MARKET_BREADTH_DAILY)
      .columns(
        MARKET_BREADTH_DAILY.QUOTE_DATE,
        MARKET_BREADTH_DAILY.BREADTH_PERCENT,
        MARKET_BREADTH_DAILY.EMA_5,
        MARKET_BREADTH_DAILY.EMA_10,
        MARKET_BREADTH_DAILY.EMA_20,
        MARKET_BREADTH_DAILY.EMA_50,
        MARKET_BREADTH_DAILY.DONCHIAN_UPPER_BAND,
        MARKET_BREADTH_DAILY.DONCHIAN_LOWER_BAND,
      ).values(
        null as LocalDate?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
      ),
  )

  private fun bindBreadthRow(
    batch: BatchBindStep,
    day: LocalDate,
    breadth: Double,
    history: List<Double>,
  ) {
    val lookback = min(20, history.size)
    batch.bind(
      day,
      breadth.toBd(),
      calculateEma(history, 5).toBd(),
      calculateEma(history, 10).toBd(),
      calculateEma(history, 20).toBd(),
      calculateEma(history, 50).toBd(),
      history.takeLast(lookback).max().toBd(),
      history.takeLast(lookback).min().toBd(),
    )
  }

  private fun insertSectorBreadth(dsl: DSLContext, tradingDays: List<LocalDate>) {
    val batchInsert = createSectorBreadthBatch(dsl)
    ALL_SECTORS.forEach { sector ->
      generateSectorBreadthData(batchInsert, sector, tradingDays)
    }
    batchInsert.execute()
  }

  private fun createSectorBreadthBatch(dsl: DSLContext): BatchBindStep = dsl.batch(
    dsl
      .insertInto(SECTOR_BREADTH_DAILY)
      .columns(
        SECTOR_BREADTH_DAILY.SECTOR_SYMBOL,
        SECTOR_BREADTH_DAILY.QUOTE_DATE,
        SECTOR_BREADTH_DAILY.STOCKS_IN_UPTREND,
        SECTOR_BREADTH_DAILY.STOCKS_IN_DOWNTREND,
        SECTOR_BREADTH_DAILY.TOTAL_STOCKS,
        SECTOR_BREADTH_DAILY.BULL_PERCENTAGE,
        SECTOR_BREADTH_DAILY.EMA_5,
        SECTOR_BREADTH_DAILY.EMA_10,
        SECTOR_BREADTH_DAILY.EMA_20,
        SECTOR_BREADTH_DAILY.EMA_50,
        SECTOR_BREADTH_DAILY.DONCHIAN_UPPER_BAND,
        SECTOR_BREADTH_DAILY.DONCHIAN_LOWER_BAND,
      ).values(
        null as String?,
        null as LocalDate?,
        null as Int?,
        null as Int?,
        null as Int?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
        null as BigDecimal?,
      ),
  )

  private fun generateSectorBreadthData(
    batch: BatchBindStep,
    sector: String,
    tradingDays: List<LocalDate>,
  ) {
    val breadthValues = mutableListOf<Double>()
    val totalStocks = 30
    var bullPct = 55.0 + random.nextDouble() * 20.0

    for (day in tradingDays) {
      bullPct = (bullPct + random.nextGaussian() * 4.0).coerceIn(20.0, 95.0)
      breadthValues.add(bullPct)
      val stocksUp = (totalStocks * bullPct / 100.0).toInt()
      val lookback = min(20, breadthValues.size)

      batch.bind(
        sector,
        day,
        stocksUp,
        totalStocks - stocksUp,
        totalStocks,
        bullPct.toBd(),
        calculateEma(breadthValues, 5).toBd(),
        calculateEma(breadthValues, 10).toBd(),
        calculateEma(breadthValues, 20).toBd(),
        calculateEma(breadthValues, 50).toBd(),
        breadthValues.takeLast(lookback).max().toBd(),
        breadthValues.takeLast(lookback).min().toBd(),
      )
    }
  }
}
