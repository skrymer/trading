package com.skrymer.udgaard.data.repository

import com.skrymer.udgaard.data.mapper.BreadthMapper
import com.skrymer.udgaard.data.model.Breadth
import com.skrymer.udgaard.jooq.tables.pojos.BreadthQuotes
import com.skrymer.udgaard.jooq.tables.references.BREADTH
import com.skrymer.udgaard.jooq.tables.references.BREADTH_QUOTES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import com.skrymer.udgaard.jooq.tables.pojos.Breadth as BreadthPojo

/**
 * jOOQ-based repository for Breadth operations
 * Replaces the Hibernate BreadthRepository
 */
@Repository
class BreadthJooqRepository(
  private val dsl: DSLContext,
  private val mapper: BreadthMapper,
) {
  /**
   * Find breadth by symbol value (e.g., "FULLSTOCK", "XLK")
   */
  fun findBySymbol(symbolValue: String): Breadth? {
    // Load breadth
    val breadth =
      dsl
        .selectFrom(BREADTH)
        .where(BREADTH.SYMBOL_VALUE.eq(symbolValue))
        .fetchOneInto(BreadthPojo::class.java) ?: return null

    // Load quotes
    val quotes =
      dsl
        .selectFrom(BREADTH_QUOTES)
        .where(BREADTH_QUOTES.SYMBOL_TYPE.eq(breadth.symbolType))
        .and(BREADTH_QUOTES.SYMBOL_VALUE.eq(symbolValue))
        .orderBy(BREADTH_QUOTES.QUOTE_DATE.asc())
        .fetchInto(BreadthQuotes::class.java)

    return mapper.toDomain(breadth, quotes)
  }

  /**
   * Find all breadth symbols
   */
  fun findAllSymbols(): List<String> =
    dsl
      .select(BREADTH.SYMBOL_VALUE)
      .from(BREADTH)
      .fetch(BREADTH.SYMBOL_VALUE)
      .filterNotNull()

  /**
   * Find all breadth data
   */
  fun findAll(): List<Breadth> {
    val symbols = findAllSymbols()
    return symbols.mapNotNull { findBySymbol(it) }
  }

  /**
   * Save breadth with all related quotes
   * Performs an upsert (insert or update)
   */
  fun save(breadth: Breadth): Breadth = dsl.transactionResult { configuration ->
    val ctx = configuration.dsl()

    // 1. Upsert Breadth
    ctx
      .insertInto(BREADTH)
      .set(BREADTH.SYMBOL_TYPE, breadth.symbolType)
      .set(BREADTH.SYMBOL_VALUE, breadth.symbolValue)
      .onDuplicateKeyUpdate()
      .set(BREADTH.SYMBOL_TYPE, breadth.symbolType)
      .execute()

    // 2. Delete existing quotes
    ctx
      .deleteFrom(BREADTH_QUOTES)
      .where(BREADTH_QUOTES.SYMBOL_TYPE.eq(breadth.symbolType))
      .and(BREADTH_QUOTES.SYMBOL_VALUE.eq(breadth.symbolValue))
      .execute()

    // 3. Insert quotes
    if (breadth.quotes.isNotEmpty()) {
      val quoteBatch = ctx.batch(
        breadth.quotes.map { quote ->
          val pojo = mapper.toPojo(quote)
          ctx
            .insertInto(BREADTH_QUOTES)
            .set(BREADTH_QUOTES.SYMBOL_TYPE, breadth.symbolType)
            .set(BREADTH_QUOTES.SYMBOL_VALUE, breadth.symbolValue)
            .set(BREADTH_QUOTES.SYMBOL, breadth.symbolValue)
            .set(BREADTH_QUOTES.QUOTE_DATE, pojo.quoteDate)
            .set(BREADTH_QUOTES.STOCKS_WITH_BUY_SIGNAL, pojo.stocksWithBuySignal)
            .set(BREADTH_QUOTES.STOCKS_WITH_SELL_SIGNAL, pojo.stocksWithSellSignal)
            .set(BREADTH_QUOTES.STOCKS_IN_UPTREND, pojo.stocksInUptrend)
            .set(BREADTH_QUOTES.STOCKS_IN_NEUTRAL, pojo.stocksInNeutral)
            .set(BREADTH_QUOTES.STOCKS_IN_DOWNTREND, pojo.stocksInDowntrend)
            .set(BREADTH_QUOTES.BULL_STOCKS_PERCENTAGE, pojo.bullStocksPercentage)
            .set(BREADTH_QUOTES.EMA_5, pojo.ema_5)
            .set(BREADTH_QUOTES.EMA_10, pojo.ema_10)
            .set(BREADTH_QUOTES.EMA_20, pojo.ema_20)
            .set(BREADTH_QUOTES.EMA_50, pojo.ema_50)
            .set(BREADTH_QUOTES.HEATMAP, pojo.heatmap)
            .set(BREADTH_QUOTES.PREVIOUS_HEATMAP, pojo.previousHeatmap)
            .set(BREADTH_QUOTES.DONCHIAN_UPPER_BAND, pojo.donchianUpperBand)
            .set(BREADTH_QUOTES.PREVIOUS_DONCHIAN_UPPER_BAND, pojo.previousDonchianUpperBand)
            .set(BREADTH_QUOTES.DONCHIAN_LOWER_BAND, pojo.donchianLowerBand)
            .set(BREADTH_QUOTES.PREVIOUS_DONCHIAN_LOWER_BAND, pojo.previousDonchianLowerBand)
            .set(BREADTH_QUOTES.DONKEY_CHANNEL_SCORE, pojo.donkeyChannelScore)
        },
      )
      quoteBatch.execute()
    }

    breadth
  }

  /**
   * Delete breadth by symbol value
   */
  fun delete(symbolValue: String) {
    dsl.transaction { configuration ->
      val ctx = configuration.dsl()
      // First get the breadth to get symbolType
      val breadth =
        ctx
          .selectFrom(BREADTH)
          .where(BREADTH.SYMBOL_VALUE.eq(symbolValue))
          .fetchOneInto(BreadthPojo::class.java)

      breadth?.let {
        ctx
          .deleteFrom(BREADTH_QUOTES)
          .where(BREADTH_QUOTES.SYMBOL_TYPE.eq(it.symbolType))
          .and(BREADTH_QUOTES.SYMBOL_VALUE.eq(symbolValue))
          .execute()
        ctx.deleteFrom(BREADTH).where(BREADTH.SYMBOL_VALUE.eq(symbolValue)).execute()
      }
    }
  }

  /**
   * Check if breadth exists
   */
  fun exists(symbolValue: String): Boolean =
    (
      dsl
        .selectCount()
        .from(BREADTH)
        .where(BREADTH.SYMBOL_VALUE.eq(symbolValue))
        .fetchOne(0, Int::class.java) ?: 0
    ) > 0

  /**
   * Count total number of breadth records
   */
  fun count(): Long =
    dsl
      .selectCount()
      .from(BREADTH)
      .fetchOne(0, Long::class.java) ?: 0L
}
