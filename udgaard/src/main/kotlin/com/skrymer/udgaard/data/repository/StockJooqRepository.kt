package com.skrymer.udgaard.data.repository

import com.skrymer.udgaard.data.dto.SimpleStockInfo
import com.skrymer.udgaard.data.mapper.StockMapper
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.jooq.tables.pojos.Earnings
import com.skrymer.udgaard.jooq.tables.pojos.OrderBlocks
import com.skrymer.udgaard.jooq.tables.pojos.StockQuotes
import com.skrymer.udgaard.jooq.tables.pojos.Stocks
import com.skrymer.udgaard.jooq.tables.references.EARNINGS
import com.skrymer.udgaard.jooq.tables.references.ORDER_BLOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDate

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
   * @param symbol the stock symbol
   * @param quotesAfter if set, only load quotes on or after this date
   */
  fun findBySymbol(
    symbol: String,
    quotesAfter: LocalDate? = null,
  ): Stock? {
    // Load stock (sector is now on stocks table)
    val stock =
      dsl
        .selectFrom(STOCKS)
        .where(STOCKS.SYMBOL.eq(symbol))
        .fetchOneInto(Stocks::class.java) ?: return null

    // Load quotes (with optional date filtering)
    val quotesQuery =
      dsl
        .selectFrom(STOCK_QUOTES)
        .where(STOCK_QUOTES.STOCK_SYMBOL.eq(symbol))
    if (quotesAfter != null) {
      quotesQuery.and(STOCK_QUOTES.QUOTE_DATE.ge(quotesAfter))
    }
    val quotes =
      quotesQuery
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
   * Get simple stock info for all stocks using a lightweight aggregate query.
   * Joins symbols table to get sector data. No quote/order block/earnings data is loaded into memory.
   */
  fun findAllSimpleInfo(): List<SimpleStockInfo> {
    val quoteCount =
      DSL
        .selectCount()
        .from(STOCK_QUOTES)
        .where(STOCK_QUOTES.STOCK_SYMBOL.eq(STOCKS.SYMBOL))
        .asField<Int>("quote_count")

    val lastQuoteDate =
      DSL
        .select(DSL.max(STOCK_QUOTES.QUOTE_DATE))
        .from(STOCK_QUOTES)
        .where(STOCK_QUOTES.STOCK_SYMBOL.eq(STOCKS.SYMBOL))
        .asField<LocalDate>("last_quote_date")

    val obCount =
      DSL
        .selectCount()
        .from(ORDER_BLOCKS)
        .where(ORDER_BLOCKS.STOCK_SYMBOL.eq(STOCKS.SYMBOL))
        .asField<Int>("order_block_count")

    return dsl
      .select(STOCKS.SYMBOL, STOCKS.SECTOR, quoteCount, lastQuoteDate, obCount)
      .from(STOCKS)
      .orderBy(STOCKS.SYMBOL)
      .fetch { record ->
        val qc = record.get(quoteCount) ?: 0
        SimpleStockInfo(
          symbol = record[STOCKS.SYMBOL]!!,
          sector = record[STOCKS.SECTOR] ?: "UNKNOWN",
          quoteCount = qc,
          orderBlockCount = record.get(obCount) ?: 0,
          lastQuoteDate = record.get(lastQuoteDate),
          hasData = qc > 0,
        )
      }
  }

  /**
   * Find stocks by list of symbols
   * @param symbols the stock symbols to load
   * @param quotesAfter if set, only load quotes on or after this date
   */
  fun findBySymbols(
    symbols: List<String>,
    quotesAfter: LocalDate? = null,
  ): List<Stock> {
    if (symbols.isEmpty()) return emptyList()

    val logger = LoggerFactory.getLogger("StockLoader")
    val startTime = System.currentTimeMillis()
    logger.info("Loading ${symbols.size} stocks from database...")

    // Load all stocks
    val stocks =
      dsl
        .selectFrom(STOCKS)
        .where(STOCKS.SYMBOL.`in`(symbols))
        .fetchInto(Stocks::class.java)

    if (stocks.isEmpty()) return emptyList()

    // Load all quotes for these stocks in one query (with optional date filtering)
    val quotesQuery =
      dsl
        .selectFrom(STOCK_QUOTES)
        .where(STOCK_QUOTES.STOCK_SYMBOL.`in`(symbols))
    if (quotesAfter != null) {
      quotesQuery.and(STOCK_QUOTES.QUOTE_DATE.ge(quotesAfter))
    }
    val quotes =
      quotesQuery
        .orderBy(STOCK_QUOTES.STOCK_SYMBOL, STOCK_QUOTES.QUOTE_DATE.asc())
        .fetchInto(StockQuotes::class.java)
    logger.info("Loaded ${quotes.size} quotes in ${System.currentTimeMillis() - startTime}ms")

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
    logger.info(
      "Loaded ${stocks.size} stocks, ${orderBlocks.size} order blocks, " +
        "${earnings.size} earnings in ${System.currentTimeMillis() - startTime}ms",
    )

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
   * @param quotesAfter if set, only load quotes on or after this date
   */
  fun findAll(quotesAfter: LocalDate? = null): List<Stock> {
    // Get all stock symbols first
    val symbols = findAllSymbols()
    // Then fetch all data for those symbols
    return findBySymbols(symbols, quotesAfter)
  }

  /**
   * Save stock with all related data
   * Performs an upsert (insert or update)
   */
  fun save(stock: Stock): Stock {
    // Validate: Don't save stocks without quote data
    require(stock.quotes.isNotEmpty()) {
      "Cannot save stock ${stock.symbol} - no quote data provided. Stocks must have at least one quote."
    }

    // Start transaction
    return dsl.transactionResult { configuration ->
      val ctx = configuration.dsl()

      // 1. Upsert Stock (including sector)
      ctx
        .insertInto(STOCKS)
        .set(STOCKS.SYMBOL, stock.symbol)
        .set(STOCKS.SECTOR, stock.sectorSymbol)
        .onConflict(STOCKS.SYMBOL)
        .doUpdate()
        .set(STOCKS.SECTOR, stock.sectorSymbol)
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
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA10, pojo.closePriceEma10)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA20, pojo.closePriceEma20)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA5, pojo.closePriceEma5)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA50, pojo.closePriceEma50)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA100, pojo.closePriceEma100)
              .set(STOCK_QUOTES.CLOSE_PRICE_EMA200, pojo.closePriceEma200)
              .set(STOCK_QUOTES.TREND, pojo.trend)
              .set(STOCK_QUOTES.ATR, pojo.atr)
              .set(STOCK_QUOTES.ADX, pojo.adx)
              .set(STOCK_QUOTES.VOLUME, pojo.volume)
              .set(STOCK_QUOTES.DONCHIAN_UPPER_BAND, pojo.donchianUpperBand)
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

  fun countDistinctStocksWithQuotes(): Int =
    dsl
      .select(DSL.countDistinct(STOCK_QUOTES.STOCK_SYMBOL))
      .from(STOCK_QUOTES)
      .fetchOne(0, Int::class.java) ?: 0

  /**
   * Delete multiple stocks by symbols in a single transaction
   * Cascades to quotes, order blocks, and earnings
   */
  fun batchDelete(symbols: List<String>) {
    if (symbols.isEmpty()) return

    dsl.transaction { configuration ->
      val ctx = configuration.dsl()
      // Delete in correct order (children first due to foreign keys)
      ctx.deleteFrom(STOCK_QUOTES).where(STOCK_QUOTES.STOCK_SYMBOL.`in`(symbols)).execute()
      ctx.deleteFrom(ORDER_BLOCKS).where(ORDER_BLOCKS.STOCK_SYMBOL.`in`(symbols)).execute()
      ctx.deleteFrom(EARNINGS).where(EARNINGS.STOCK_SYMBOL.`in`(symbols)).execute()
      ctx.deleteFrom(STOCKS).where(STOCKS.SYMBOL.`in`(symbols)).execute()
    }
  }

  /**
   * Save multiple stocks with all related data in a single transaction
   * Performs upsert operations optimized for batch processing
   */
  fun batchSave(stocks: List<Stock>): List<Stock> {
    if (stocks.isEmpty()) return emptyList()

    // Validate: Don't save stocks without quote data
    val emptyStocks = stocks.filter { it.quotes.isEmpty() }
    require(emptyStocks.isEmpty()) {
      "Cannot save ${emptyStocks.size} stock(s) without quote data: ${emptyStocks.map { it.symbol }.joinToString()}"
    }

    return dsl.transactionResult { configuration ->
      val ctx = configuration.dsl()

      // 1. Batch upsert stocks (including sector)
      val stockBatch =
        ctx.batch(
          stocks.map { stock ->
            ctx
              .insertInto(STOCKS)
              .set(STOCKS.SYMBOL, stock.symbol)
              .set(STOCKS.SECTOR, stock.sectorSymbol)
              .onConflict(STOCKS.SYMBOL)
              .doUpdate()
              .set(STOCKS.SECTOR, stock.sectorSymbol)
          },
        )
      stockBatch.execute()

      // 2. Delete existing child records for all stocks
      val symbols = stocks.map { it.symbol }
      ctx.deleteFrom(STOCK_QUOTES).where(STOCK_QUOTES.STOCK_SYMBOL.`in`(symbols)).execute()
      ctx.deleteFrom(ORDER_BLOCKS).where(ORDER_BLOCKS.STOCK_SYMBOL.`in`(symbols)).execute()
      ctx.deleteFrom(EARNINGS).where(EARNINGS.STOCK_SYMBOL.`in`(symbols)).execute()

      // 3. Insert all child records
      batchInsertChildRecords(ctx, stocks)

      // Return the saved stocks
      stocks
    }
  }

  /**
   * Insert stocks and all child records using bind-step batching.
   * Caller is responsible for deleting existing records first.
   * Uses JDBC prepared-statement batching for optimal performance.
   */
  fun batchInsert(stocks: List<Stock>) {
    if (stocks.isEmpty()) return

    val emptyStocks = stocks.filter { it.quotes.isEmpty() }
    require(emptyStocks.isEmpty()) {
      "Cannot save ${emptyStocks.size} stock(s) without quote data: ${emptyStocks.map { it.symbol }.joinToString()}"
    }

    dsl.transaction { configuration ->
      val ctx = configuration.dsl()

      // 1. Insert stocks
      val stockBatch = ctx.batch(
        ctx
          .insertInto(STOCKS, STOCKS.SYMBOL, STOCKS.SECTOR)
          .values(null as String?, null as String?),
      )
      stocks.forEach { stock -> stockBatch.bind(stock.symbol, stock.sectorSymbol) }
      stockBatch.execute()

      // 2. Insert all child records
      batchInsertChildRecords(ctx, stocks)
    }
  }

  private fun batchInsertChildRecords(ctx: DSLContext, stocks: List<Stock>) {
    batchInsertQuotes(ctx, stocks)
    batchInsertOrderBlocks(ctx, stocks)
    batchInsertEarnings(ctx, stocks)
  }

  private fun batchInsertQuotes(ctx: DSLContext, stocks: List<Stock>) {
    val allQuotes = stocks.flatMap { stock -> stock.quotes.map { stock.symbol to it } }
    if (allQuotes.isEmpty()) return

    val batch = ctx.batch(
      ctx
        .insertInto(
          STOCK_QUOTES,
          STOCK_QUOTES.STOCK_SYMBOL,
          STOCK_QUOTES.QUOTE_DATE,
          STOCK_QUOTES.CLOSE_PRICE,
          STOCK_QUOTES.OPEN_PRICE,
          STOCK_QUOTES.HIGH_PRICE,
          STOCK_QUOTES.LOW_PRICE,
          STOCK_QUOTES.CLOSE_PRICE_EMA10,
          STOCK_QUOTES.CLOSE_PRICE_EMA20,
          STOCK_QUOTES.CLOSE_PRICE_EMA5,
          STOCK_QUOTES.CLOSE_PRICE_EMA50,
          STOCK_QUOTES.CLOSE_PRICE_EMA100,
          STOCK_QUOTES.CLOSE_PRICE_EMA200,
          STOCK_QUOTES.TREND,
          STOCK_QUOTES.ATR,
          STOCK_QUOTES.ADX,
          STOCK_QUOTES.VOLUME,
          STOCK_QUOTES.DONCHIAN_UPPER_BAND,
        ).values(
          null as String?,
          null as LocalDate?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as String?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as Long?,
          null as java.math.BigDecimal?,
        ),
    )
    allQuotes.forEach { (symbol, quote) ->
      val pojo = mapper.toPojo(quote)
      batch.bind(
        symbol,
        pojo.quoteDate,
        pojo.closePrice,
        pojo.openPrice,
        pojo.highPrice,
        pojo.lowPrice,
        pojo.closePriceEma10,
        pojo.closePriceEma20,
        pojo.closePriceEma5,
        pojo.closePriceEma50,
        pojo.closePriceEma100,
        pojo.closePriceEma200,
        pojo.trend,
        pojo.atr,
        pojo.adx,
        pojo.volume,
        pojo.donchianUpperBand,
      )
    }
    batch.execute()
  }

  private fun batchInsertOrderBlocks(ctx: DSLContext, stocks: List<Stock>) {
    val allOrderBlocks = stocks.flatMap { stock -> stock.orderBlocks.map { stock.symbol to it } }
    if (allOrderBlocks.isEmpty()) return

    val batch = ctx.batch(
      ctx
        .insertInto(
          ORDER_BLOCKS,
          ORDER_BLOCKS.STOCK_SYMBOL,
          ORDER_BLOCKS.TYPE,
          ORDER_BLOCKS.SENSITIVITY,
          ORDER_BLOCKS.START_DATE,
          ORDER_BLOCKS.END_DATE,
          ORDER_BLOCKS.START_PRICE,
          ORDER_BLOCKS.END_PRICE,
          ORDER_BLOCKS.LOW_PRICE,
          ORDER_BLOCKS.HIGH_PRICE,
          ORDER_BLOCKS.VOLUME,
          ORDER_BLOCKS.VOLUME_STRENGTH,
          ORDER_BLOCKS.RATE_OF_CHANGE,
          ORDER_BLOCKS.IS_ACTIVE,
        ).values(
          null as String?,
          null as String?,
          null as String?,
          null as LocalDate?,
          null as LocalDate?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as Long?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as Boolean?,
        ),
    )
    allOrderBlocks.forEach { (symbol, orderBlock) ->
      val pojo = mapper.toPojo(orderBlock)
      batch.bind(
        symbol,
        pojo.type,
        pojo.sensitivity,
        pojo.startDate,
        pojo.endDate,
        pojo.startPrice,
        pojo.endPrice,
        pojo.lowPrice,
        pojo.highPrice,
        pojo.volume,
        pojo.volumeStrength,
        pojo.rateOfChange,
        pojo.isActive,
      )
    }
    batch.execute()
  }

  private fun batchInsertEarnings(ctx: DSLContext, stocks: List<Stock>) {
    val allEarnings = stocks.flatMap { stock -> stock.earnings.map { stock.symbol to it } }
    if (allEarnings.isEmpty()) return

    val batch = ctx.batch(
      ctx
        .insertInto(
          EARNINGS,
          EARNINGS.STOCK_SYMBOL,
          EARNINGS.SYMBOL,
          EARNINGS.FISCAL_DATE_ENDING,
          EARNINGS.REPORTED_DATE,
          EARNINGS.REPORTEDEPS,
          EARNINGS.ESTIMATEDEPS,
          EARNINGS.SURPRISE,
          EARNINGS.SURPRISE_PERCENTAGE,
          EARNINGS.REPORT_TIME,
        ).values(
          null as String?,
          null as String?,
          null as LocalDate?,
          null as LocalDate?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as java.math.BigDecimal?,
          null as String?,
        ),
    )
    allEarnings.forEach { (symbol, earning) ->
      val pojo = mapper.toPojo(earning)
      batch.bind(
        symbol,
        pojo.symbol,
        pojo.fiscalDateEnding,
        pojo.reportedDate,
        pojo.reportedeps,
        pojo.estimatedeps,
        pojo.surprise,
        pojo.surprisePercentage,
        pojo.reportTime,
      )
    }
    batch.execute()
  }
}
