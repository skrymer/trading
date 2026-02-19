package com.skrymer.udgaard.data.repository

import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.jooq.tables.references.MARKET_BREADTH_DAILY
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import com.skrymer.udgaard.jooq.tables.references.SYMBOLS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class MarketBreadthRepository(
  private val dsl: DSLContext,
) {
  private val logger = LoggerFactory.getLogger(MarketBreadthRepository::class.java)

  fun refreshMarketBreadthDaily(rows: List<MarketBreadthDaily>) {
    val start = System.currentTimeMillis()

    dsl.transaction { config ->
      val ctx = config.dsl()
      ctx.deleteFrom(MARKET_BREADTH_DAILY).execute()

      if (rows.isNotEmpty()) {
        val batchSize = 1000
        rows.chunked(batchSize).forEach { chunk ->
          var insert = ctx.insertInto(
            MARKET_BREADTH_DAILY,
            MARKET_BREADTH_DAILY.QUOTE_DATE,
            MARKET_BREADTH_DAILY.BREADTH_PERCENT,
            MARKET_BREADTH_DAILY.EMA_5,
            MARKET_BREADTH_DAILY.EMA_10,
            MARKET_BREADTH_DAILY.EMA_20,
            MARKET_BREADTH_DAILY.EMA_50,
            MARKET_BREADTH_DAILY.DONCHIAN_UPPER_BAND,
            MARKET_BREADTH_DAILY.DONCHIAN_LOWER_BAND,
          )

          chunk.forEach { row ->
            insert = insert.values(
              row.quoteDate,
              BigDecimal.valueOf(row.breadthPercent),
              BigDecimal.valueOf(row.ema5),
              BigDecimal.valueOf(row.ema10),
              BigDecimal.valueOf(row.ema20),
              BigDecimal.valueOf(row.ema50),
              BigDecimal.valueOf(row.donchianUpperBand),
              BigDecimal.valueOf(row.donchianLowerBand),
            )
          }

          insert.execute()
        }
      }
    }

    logger.info("Refreshed market breadth daily (${rows.size} rows) in ${System.currentTimeMillis() - start}ms")
  }

  fun findAllAsMap(): Map<LocalDate, MarketBreadthDaily> =
    dsl
      .selectFrom(MARKET_BREADTH_DAILY)
      .orderBy(MARKET_BREADTH_DAILY.QUOTE_DATE)
      .fetch { record ->
        MarketBreadthDaily(
          quoteDate = record[MARKET_BREADTH_DAILY.QUOTE_DATE]!!,
          breadthPercent = record[MARKET_BREADTH_DAILY.BREADTH_PERCENT]?.toDouble() ?: 0.0,
          ema5 = record[MARKET_BREADTH_DAILY.EMA_5]?.toDouble() ?: 0.0,
          ema10 = record[MARKET_BREADTH_DAILY.EMA_10]?.toDouble() ?: 0.0,
          ema20 = record[MARKET_BREADTH_DAILY.EMA_20]?.toDouble() ?: 0.0,
          ema50 = record[MARKET_BREADTH_DAILY.EMA_50]?.toDouble() ?: 0.0,
          donchianUpperBand = record[MARKET_BREADTH_DAILY.DONCHIAN_UPPER_BAND]?.toDouble() ?: 0.0,
          donchianLowerBand = record[MARKET_BREADTH_DAILY.DONCHIAN_LOWER_BAND]?.toDouble() ?: 0.0,
        )
      }.associateBy { it.quoteDate }

  fun findAllRaw(): List<MarketBreadthDaily> =
    dsl
      .selectFrom(MARKET_BREADTH_DAILY)
      .orderBy(MARKET_BREADTH_DAILY.QUOTE_DATE)
      .fetch { record ->
        MarketBreadthDaily(
          quoteDate = record[MARKET_BREADTH_DAILY.QUOTE_DATE]!!,
          breadthPercent = record[MARKET_BREADTH_DAILY.BREADTH_PERCENT]?.toDouble() ?: 0.0,
        )
      }

  /**
   * Read pre-calculated market breadth from the market_breadth_daily table.
   * Returns a map of date to breadth percentage (0-100).
   */
  fun calculateBreadthByDate(): Map<LocalDate, Double> =
    dsl
      .select(MARKET_BREADTH_DAILY.QUOTE_DATE, MARKET_BREADTH_DAILY.BREADTH_PERCENT)
      .from(MARKET_BREADTH_DAILY)
      .fetch { record ->
        val date = record[MARKET_BREADTH_DAILY.QUOTE_DATE]!!
        val breadth = record[MARKET_BREADTH_DAILY.BREADTH_PERCENT]?.toDouble() ?: 0.0
        date to breadth
      }.toMap()

  /**
   * Recalculate market_breadth_daily from stock_quotes.
   * Called after stock data refresh to keep breadth values current.
   */
  fun refreshBreadthDaily() {
    val start = System.currentTimeMillis()

    dsl.transaction { config ->
      val ctx = config.dsl()
      ctx.deleteFrom(MARKET_BREADTH_DAILY).execute()
      ctx
        .insertInto(
          MARKET_BREADTH_DAILY,
          MARKET_BREADTH_DAILY.QUOTE_DATE,
          MARKET_BREADTH_DAILY.BREADTH_PERCENT,
        ).select(
          dsl
            .select(
              STOCK_QUOTES.QUOTE_DATE,
              DSL
                .count(DSL.`when`(STOCK_QUOTES.TREND.eq("Uptrend"), 1))
                .cast(BigDecimal::class.java)
                .mul(BigDecimal("100.0"))
                .div(DSL.count().cast(BigDecimal::class.java)),
            ).from(STOCK_QUOTES)
            .join(SYMBOLS)
            .on(STOCK_QUOTES.STOCK_SYMBOL.eq(SYMBOLS.SYMBOL))
            .where(SYMBOLS.ASSET_TYPE.eq(AssetType.STOCK.name))
            .groupBy(STOCK_QUOTES.QUOTE_DATE),
        ).execute()
    }

    logger.info("Refreshed market breadth daily in ${System.currentTimeMillis() - start}ms")
  }
}
