package com.skrymer.udgaard.repository.jooq

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.jooq.tables.pojos.Earnings
import com.skrymer.udgaard.jooq.tables.pojos.OrderBlocks
import com.skrymer.udgaard.jooq.tables.pojos.StockQuotes
import com.skrymer.udgaard.jooq.tables.pojos.Stocks
import com.skrymer.udgaard.jooq.tables.references.EARNINGS
import com.skrymer.udgaard.jooq.tables.references.ORDER_BLOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import com.skrymer.udgaard.mapper.StockMapper
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/**
 * jOOQ-based repository for Stock operations
 * Replaces the Hibernate StockRepository
 */
@Repository
class StockJooqRepository(
  private val dsl: DSLContext,
  private val mapper: StockMapper,
) {
  /**
   * Find stock by symbol with all related data
   */
  fun findBySymbol(symbol: String): StockDomain? {
    // Load stock
    val stock =
      dsl
        .selectFrom(STOCKS)
        .where(STOCKS.SYMBOL.eq(symbol))
        .fetchOneInto(Stocks::class.java) ?: return null

    // Load quotes
    val quotes =
      dsl
        .selectFrom(STOCK_QUOTES)
        .where(STOCK_QUOTES.STOCK_SYMBOL.eq(symbol))
        .orderBy(STOCK_QUOTES.QUOTE_DATE.asc())
        .fetchInto(StockQuotes::class.java)

    // Load order blocks
    val orderBlocks =
      dsl
        .selectFrom(ORDER_BLOCKS)
        .where(ORDER_BLOCKS.STOCK_SYMBOL.eq(symbol))
        .orderBy(ORDER_BLOCKS.START_DATE.asc())
        .fetchInto(OrderBlocks::class.java)

    // Load earnings
    val earnings =
      dsl
        .selectFrom(EARNINGS)
        .where(EARNINGS.STOCK_SYMBOL.eq(symbol))
        .orderBy(EARNINGS.FISCAL_DATE_ENDING.asc())
        .fetchInto(Earnings::class.java)

    return mapper.toDomain(stock, quotes, orderBlocks, earnings)
  }

  /**
   * Find all stocks with their symbols only (lightweight query)
   */
  fun findAllSymbols(): List<String> =
    dsl
      .select(STOCKS.SYMBOL)
      .from(STOCKS)
      .fetch(STOCKS.SYMBOL)
      .filterNotNull()

  /**
   * Find stocks by list of symbols
   */
  fun findBySymbols(symbols: List<String>): List<StockDomain> {
    if (symbols.isEmpty()) return emptyList()

    // Load all stocks
    val stocks =
      dsl
        .selectFrom(STOCKS)
        .where(STOCKS.SYMBOL.`in`(symbols))
        .fetchInto(Stocks::class.java)

    if (stocks.isEmpty()) return emptyList()

    // Load all quotes for these stocks in one query
    val quotes =
      dsl
        .selectFrom(STOCK_QUOTES)
        .where(STOCK_QUOTES.STOCK_SYMBOL.`in`(symbols))
        .orderBy(STOCK_QUOTES.STOCK_SYMBOL, STOCK_QUOTES.QUOTE_DATE.asc())
        .fetchInto(StockQuotes::class.java)

    // Load all order blocks for these stocks
    val orderBlocks =
      dsl
        .selectFrom(ORDER_BLOCKS)
        .where(ORDER_BLOCKS.STOCK_SYMBOL.`in`(symbols))
        .orderBy(ORDER_BLOCKS.STOCK_SYMBOL, ORDER_BLOCKS.START_DATE.asc())
        .fetchInto(OrderBlocks::class.java)

    // Load all earnings for these stocks
    val earnings =
      dsl
        .selectFrom(EARNINGS)
        .where(EARNINGS.STOCK_SYMBOL.`in`(symbols))
        .orderBy(EARNINGS.STOCK_SYMBOL, EARNINGS.FISCAL_DATE_ENDING.asc())
        .fetchInto(Earnings::class.java)

    // Group by symbol
    val quotesBySymbol = quotes.groupBy { it.stockSymbol }
    val orderBlocksBySymbol = orderBlocks.groupBy { it.stockSymbol }
    val earningsBySymbol = earnings.groupBy { it.stockSymbol }

    // Map to domain models
    return stocks.map { stock ->
      mapper.toDomain(
        stock = stock,
        quotes = quotesBySymbol[stock.symbol] ?: emptyList(),
        orderBlocks = orderBlocksBySymbol[stock.symbol] ?: emptyList(),
        earnings = earningsBySymbol[stock.symbol] ?: emptyList(),
      )
    }
  }

  /**
   * Find all stocks with all related data
   */
  fun findAll(): List<StockDomain> {
    // Get all stock symbols first
    val symbols = findAllSymbols()
    // Then fetch all data for those symbols
    return findBySymbols(symbols)
  }

  /**
   * Save stock with all related data
   * Performs an upsert (insert or update)
   */
  fun save(stock: StockDomain): StockDomain {
    // Start transaction
    return dsl.transactionResult { configuration ->
      val ctx = configuration.dsl()

      // 1. Upsert Stock
      ctx
        .insertInto(STOCKS)
        .set(STOCKS.SYMBOL, stock.symbol)
        .set(STOCKS.SECTOR_SYMBOL, stock.sectorSymbol)
        .onDuplicateKeyUpdate()
        .set(STOCKS.SECTOR_SYMBOL, stock.sectorSymbol)
        .execute()

      // 2. Delete existing quotes, order blocks, and earnings (cascade delete)
      ctx.deleteFrom(STOCK_QUOTES).where(STOCK_QUOTES.STOCK_SYMBOL.eq(stock.symbol)).execute()
      ctx.deleteFrom(ORDER_BLOCKS).where(ORDER_BLOCKS.STOCK_SYMBOL.eq(stock.symbol)).execute()
      ctx.deleteFrom(EARNINGS).where(EARNINGS.STOCK_SYMBOL.eq(stock.symbol)).execute()

      // 3. Insert quotes
      if (stock.quotes.isNotEmpty()) {
        val quoteBatch = ctx.batch(
          stock.quotes.map { quote ->
            val pojo = mapper.toPojo(quote)
            ctx
              .insertInto(STOCK_QUOTES)
              .set(STOCK_QUOTES.STOCK_SYMBOL, stock.symbol)
              .set(STOCK_QUOTES.QUOTE_DATE, pojo.quoteDate)
              .set(STOCK_QUOTES.CLOSE_PRICE, pojo.closePrice)
              .set(STOCK_QUOTES.OPEN_PRICE, pojo.openPrice)
              .set(STOCK_QUOTES.HIGH_PRICE, pojo.highPrice)
              .set(STOCK_QUOTES.LOW_PRICE, pojo.lowPrice)
              .set(STOCK_QUOTES.HEATMAP, pojo.heatmap)
              .set(STOCK_QUOTES.PREVIOUS_HEATMAP, pojo.previousHeatmap)
              .set(STOCK_QUOTES.SECTOR_HEATMAP, pojo.sectorHeatmap)
              .set(STOCK_QUOTES.PREVIOUS_SECTOR_HEATMAP, pojo.previousSectorHeatmap)
              .set(STOCK_QUOTES.SECTOR_IS_IN_UPTREND, pojo.sectorIsInUptrend)
              .set(STOCK_QUOTES.SECTOR_DONKEY_CHANNEL_SCORE, pojo.sectorDonkeyChannelScore)
              .set(STOCK_QUOTES.SIGNAL, pojo.signal)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA10, pojo.closePriceEma10)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA20, pojo.closePriceEma20)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA5, pojo.closePriceEma5)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA50, pojo.closePriceEma50)
              .set(STOCK_QUOTES.TREND, pojo.trend)
              .set(STOCK_QUOTES.LAST_BUY_SIGNAL, pojo.lastBuySignal)
              .set(STOCK_QUOTES.LAST_SELL_SIGNAL, pojo.lastSellSignal)
              .set(STOCK_QUOTES.SPY_SIGNAL, pojo.spySignal)
              .set(STOCK_QUOTES.SPY_IN_UPTREND, pojo.spyInUptrend)
              .set(STOCK_QUOTES.SPY_HEATMAP, pojo.spyHeatmap)
              .set(STOCK_QUOTES.SPY_PREVIOUS_HEATMAP, pojo.spyPreviousHeatmap)
              .set(STOCK_QUOTES.SPY_EMA200, pojo.spyEma200)
              .set(STOCK_QUOTES.SPY_SMA200, pojo.spySma200)
              .set(STOCK_QUOTES.SPY_EMA50, pojo.spyEma50)
              .set(STOCK_QUOTES.SPY_DAYS_ABOVE_200SMA, pojo.spyDaysAbove_200sma)
              .set(STOCK_QUOTES.MARKET_ADVANCING_PERCENT, pojo.marketAdvancingPercent)
              .set(STOCK_QUOTES.MARKET_IS_IN_UPTREND, pojo.marketIsInUptrend)
              .set(STOCK_QUOTES.MARKET_DONKEY_CHANNEL_SCORE, pojo.marketDonkeyChannelScore)
              .set(STOCK_QUOTES.PREVIOUS_QUOTE_DATE, pojo.previousQuoteDate)
              .set(STOCK_QUOTES.SECTOR_BREADTH, pojo.sectorBreadth)
              .set(STOCK_QUOTES.SECTOR_STOCKS_IN_DOWNTREND, pojo.sectorStocksInDowntrend)
              .set(STOCK_QUOTES.SECTOR_STOCKS_IN_UPTREND, pojo.sectorStocksInUptrend)
              .set(STOCK_QUOTES.SECTOR_BULL_PERCENTAGE, pojo.sectorBullPercentage)
              .set(STOCK_QUOTES.ATR, pojo.atr)
              .set(STOCK_QUOTES.ADX, pojo.adx)
              .set(STOCK_QUOTES.VOLUME, pojo.volume)
              .set(STOCK_QUOTES.DONCHIAN_UPPER_BAND, pojo.donchianUpperBand)
              .set(STOCK_QUOTES.DONCHIAN_UPPER_BAND_MARKET, pojo.donchianUpperBandMarket)
              .set(STOCK_QUOTES.DONCHIAN_UPPER_BAND_SECTOR, pojo.donchianUpperBandSector)
              .set(STOCK_QUOTES.DONCHIAN_LOWER_BAND_MARKET, pojo.donchianLowerBandMarket)
              .set(STOCK_QUOTES.DONCHIAN_LOWER_BAND_SECTOR, pojo.donchianLowerBandSector)
          },
        )
        quoteBatch.execute()
      }

      // 4. Insert order blocks
      if (stock.orderBlocks.isNotEmpty()) {
        val orderBlockBatch = ctx.batch(
          stock.orderBlocks.map { orderBlock ->
            val pojo = mapper.toPojo(orderBlock)
            ctx
              .insertInto(ORDER_BLOCKS)
              .set(ORDER_BLOCKS.STOCK_SYMBOL, stock.symbol)
              .set(ORDER_BLOCKS.TYPE, pojo.type)
              .set(ORDER_BLOCKS.SENSITIVITY, pojo.sensitivity)
              .set(ORDER_BLOCKS.START_DATE, pojo.startDate)
              .set(ORDER_BLOCKS.END_DATE, pojo.endDate)
              .set(ORDER_BLOCKS.START_PRICE, pojo.startPrice)
              .set(ORDER_BLOCKS.END_PRICE, pojo.endPrice)
              .set(ORDER_BLOCKS.LOW_PRICE, pojo.lowPrice)
              .set(ORDER_BLOCKS.HIGH_PRICE, pojo.highPrice)
              .set(ORDER_BLOCKS.VOLUME, pojo.volume)
              .set(ORDER_BLOCKS.VOLUME_STRENGTH, pojo.volumeStrength)
              .set(ORDER_BLOCKS.RATE_OF_CHANGE, pojo.rateOfChange)
              .set(ORDER_BLOCKS.IS_ACTIVE, pojo.isActive)
          },
        )
        orderBlockBatch.execute()
      }

      // 5. Insert earnings
      if (stock.earnings.isNotEmpty()) {
        val earningsBatch = ctx.batch(
          stock.earnings.map { earning ->
            val pojo = mapper.toPojo(earning)
            ctx
              .insertInto(EARNINGS)
              .set(EARNINGS.STOCK_SYMBOL, stock.symbol)
              .set(EARNINGS.SYMBOL, pojo.symbol)
              .set(EARNINGS.FISCAL_DATE_ENDING, pojo.fiscalDateEnding)
              .set(EARNINGS.REPORTED_DATE, pojo.reportedDate)
              .set(EARNINGS.REPORTEDEPS, pojo.reportedeps)
              .set(EARNINGS.ESTIMATEDEPS, pojo.estimatedeps)
              .set(EARNINGS.SURPRISE, pojo.surprise)
              .set(EARNINGS.SURPRISE_PERCENTAGE, pojo.surprisePercentage)
              .set(EARNINGS.REPORT_TIME, pojo.reportTime)
          },
        )
        earningsBatch.execute()
      }

      // Return the saved stock
      stock
    }
  }

  /**
   * Delete stock by symbol (cascades to quotes, order blocks, earnings)
   */
  fun delete(symbol: String) {
    dsl.transaction { configuration ->
      val ctx = configuration.dsl()
      // Delete in correct order (children first due to foreign keys)
      ctx.deleteFrom(STOCK_QUOTES).where(STOCK_QUOTES.STOCK_SYMBOL.eq(symbol)).execute()
      ctx.deleteFrom(ORDER_BLOCKS).where(ORDER_BLOCKS.STOCK_SYMBOL.eq(symbol)).execute()
      ctx.deleteFrom(EARNINGS).where(EARNINGS.STOCK_SYMBOL.eq(symbol)).execute()
      ctx.deleteFrom(STOCKS).where(STOCKS.SYMBOL.eq(symbol)).execute()
    }
  }

  /**
   * Check if stock exists
   */
  fun exists(symbol: String): Boolean =
    (
      dsl
        .selectCount()
        .from(STOCKS)
        .where(STOCKS.SYMBOL.eq(symbol))
        .fetchOne(0, Int::class.java) ?: 0
    ) > 0

  /**
   * Count total number of stocks
   */
  fun count(): Long =
    dsl
      .selectCount()
      .from(STOCKS)
      .fetchOne(0, Long::class.java) ?: 0L
}
