package com.skrymer.udgaard.data.repository

import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.jooq.tables.references.SECTOR_BREADTH_DAILY
import com.skrymer.udgaard.jooq.tables.references.STOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class SectorBreadthRepository(
  private val dsl: DSLContext,
) {
  private val logger = LoggerFactory.getLogger(SectorBreadthRepository::class.java)

  fun calculateRawSectorBreadth(): List<SectorBreadthDaily> =
    dsl
      .select(
        STOCKS.SECTOR,
        STOCK_QUOTES.QUOTE_DATE,
        DSL.count(DSL.`when`(STOCK_QUOTES.TREND.eq("Uptrend"), 1)).`as`("stocks_in_uptrend"),
        DSL.count(DSL.`when`(STOCK_QUOTES.TREND.ne("Uptrend"), 1)).`as`("stocks_in_downtrend"),
        DSL.count().`as`("total_stocks"),
        DSL
          .count(DSL.`when`(STOCK_QUOTES.TREND.eq("Uptrend"), 1))
          .cast(BigDecimal::class.java)
          .mul(BigDecimal("100.0"))
          .div(DSL.count().cast(BigDecimal::class.java))
          .`as`("bull_percentage"),
      ).from(STOCK_QUOTES)
      .join(STOCKS)
      .on(STOCK_QUOTES.STOCK_SYMBOL.eq(STOCKS.SYMBOL))
      .where(STOCKS.SECTOR.isNotNull)
      .groupBy(STOCKS.SECTOR, STOCK_QUOTES.QUOTE_DATE)
      .orderBy(STOCKS.SECTOR, STOCK_QUOTES.QUOTE_DATE)
      .fetch { record ->
        SectorBreadthDaily(
          sectorSymbol = record[STOCKS.SECTOR]!!,
          quoteDate = record[STOCK_QUOTES.QUOTE_DATE]!!,
          stocksInUptrend = record.get("stocks_in_uptrend", Int::class.java),
          stocksInDowntrend = record.get("stocks_in_downtrend", Int::class.java),
          totalStocks = record.get("total_stocks", Int::class.java),
          bullPercentage = record.get("bull_percentage", BigDecimal::class.java).toDouble(),
        )
      }

  fun refreshSectorBreadthDaily(rows: List<SectorBreadthDaily>) {
    val start = System.currentTimeMillis()

    dsl.transaction { config ->
      val ctx = config.dsl()
      ctx.deleteFrom(SECTOR_BREADTH_DAILY).execute()

      if (rows.isNotEmpty()) {
        val batchSize = 1000
        rows.chunked(batchSize).forEach { chunk ->
          var insert = ctx.insertInto(
            SECTOR_BREADTH_DAILY,
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
          )

          chunk.forEach { row ->
            insert = insert.values(
              row.sectorSymbol,
              row.quoteDate,
              row.stocksInUptrend,
              row.stocksInDowntrend,
              row.totalStocks,
              BigDecimal.valueOf(row.bullPercentage),
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

    logger.info("Refreshed sector breadth daily (${rows.size} rows) in ${System.currentTimeMillis() - start}ms")
  }

  fun getLatestSectorCounts(): Map<String, Int> {
    val latestDate = dsl
      .select(DSL.max(SECTOR_BREADTH_DAILY.QUOTE_DATE))
      .from(SECTOR_BREADTH_DAILY)
      .fetchOne(0, java.time.LocalDate::class.java) ?: return emptyMap()

    return dsl
      .select(SECTOR_BREADTH_DAILY.SECTOR_SYMBOL, SECTOR_BREADTH_DAILY.TOTAL_STOCKS)
      .from(SECTOR_BREADTH_DAILY)
      .where(SECTOR_BREADTH_DAILY.QUOTE_DATE.eq(latestDate))
      .orderBy(SECTOR_BREADTH_DAILY.SECTOR_SYMBOL)
      .fetch { record ->
        record[SECTOR_BREADTH_DAILY.SECTOR_SYMBOL]!! to record[SECTOR_BREADTH_DAILY.TOTAL_STOCKS]!!
      }.toMap()
  }

  fun findBySector(sectorSymbol: String): List<SectorBreadthDaily> =
    dsl
      .selectFrom(SECTOR_BREADTH_DAILY)
      .where(SECTOR_BREADTH_DAILY.SECTOR_SYMBOL.eq(sectorSymbol))
      .orderBy(SECTOR_BREADTH_DAILY.QUOTE_DATE.asc())
      .fetch { record ->
        mapSectorBreadthRecord(record)
      }

  fun findAllAsMap(): Map<String, Map<java.time.LocalDate, SectorBreadthDaily>> =
    dsl
      .selectFrom(SECTOR_BREADTH_DAILY)
      .orderBy(SECTOR_BREADTH_DAILY.SECTOR_SYMBOL, SECTOR_BREADTH_DAILY.QUOTE_DATE)
      .fetch { record ->
        mapSectorBreadthRecord(record)
      }.groupBy { it.sectorSymbol }
      .mapValues { (_, rows) -> rows.associateBy { it.quoteDate } }

  private fun mapSectorBreadthRecord(record: org.jooq.Record): SectorBreadthDaily =
    SectorBreadthDaily(
      sectorSymbol = record[SECTOR_BREADTH_DAILY.SECTOR_SYMBOL]!!,
      quoteDate = record[SECTOR_BREADTH_DAILY.QUOTE_DATE]!!,
      stocksInUptrend = record[SECTOR_BREADTH_DAILY.STOCKS_IN_UPTREND]!!,
      stocksInDowntrend = record[SECTOR_BREADTH_DAILY.STOCKS_IN_DOWNTREND]!!,
      totalStocks = record[SECTOR_BREADTH_DAILY.TOTAL_STOCKS]!!,
      bullPercentage = record[SECTOR_BREADTH_DAILY.BULL_PERCENTAGE]?.toDouble() ?: 0.0,
      ema5 = record[SECTOR_BREADTH_DAILY.EMA_5]?.toDouble() ?: 0.0,
      ema10 = record[SECTOR_BREADTH_DAILY.EMA_10]?.toDouble() ?: 0.0,
      ema20 = record[SECTOR_BREADTH_DAILY.EMA_20]?.toDouble() ?: 0.0,
      ema50 = record[SECTOR_BREADTH_DAILY.EMA_50]?.toDouble() ?: 0.0,
      donchianUpperBand = record[SECTOR_BREADTH_DAILY.DONCHIAN_UPPER_BAND]?.toDouble() ?: 0.0,
      donchianLowerBand = record[SECTOR_BREADTH_DAILY.DONCHIAN_LOWER_BAND]?.toDouble() ?: 0.0,
    )
}
