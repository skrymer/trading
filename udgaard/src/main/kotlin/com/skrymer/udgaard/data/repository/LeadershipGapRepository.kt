package com.skrymer.udgaard.data.repository

import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.EwReturnDaily
import com.skrymer.udgaard.jooq.tables.references.STOCKS
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Full-universe equal-weight return aggregate feeding the leadership-gap regime (issue #83).
 *
 * For every trading day in `[loadAfter, before]` over the point-in-time STOCK-or-null universe (the
 * same population as market breadth, ADR 0011), computes the equal-weight mean, cross-sectional
 * sample stdev, and contributing count of per-name trailing `lookbackBars`-bar simple returns. The
 * trailing return is `close / LAG(close, lookbackBars) - 1`, counted in trading bars (rows) per symbol;
 * a non-positive prior close is guarded out, and a name without `lookbackBars` of prior history simply
 * does not contribute on that day (delisted/young names drop in and out survivorship-honestly).
 */
@Repository
class LeadershipGapRepository(
  private val dsl: DSLContext,
) {
  fun ewReturnByDate(
    loadAfter: LocalDate,
    before: LocalDate,
    lookbackBars: Int = 20,
  ): Map<LocalDate, EwReturnDaily> {
    val priorClose =
      DSL
        .lag(STOCK_QUOTES.CLOSE_PRICE, lookbackBars)
        .over()
        .partitionBy(STOCK_QUOTES.STOCK_SYMBOL)
        .orderBy(STOCK_QUOTES.QUOTE_DATE)

    // Guard the division: a name only contributes once it has a positive close `lookbackBars` ago.
    val perNameReturn =
      DSL
        .`when`(priorClose.gt(BigDecimal.ZERO), STOCK_QUOTES.CLOSE_PRICE.div(priorClose).minus(BigDecimal.ONE))
        .otherwise(DSL.castNull(BigDecimal::class.java))

    val perName =
      dsl
        .select(STOCK_QUOTES.QUOTE_DATE.`as`("d"), perNameReturn.`as`("ret"))
        .from(STOCK_QUOTES)
        .join(STOCKS)
        .on(STOCK_QUOTES.STOCK_SYMBOL.eq(STOCKS.SYMBOL))
        // null asset_type (lookup failed at ingestion) defaults to STOCK, matching breadth + the
        // stocks-derived universe read path (StockJooqRepository.findAllSymbolRecords).
        .where(STOCKS.ASSET_TYPE.eq(AssetType.STOCK.name).or(STOCKS.ASSET_TYPE.isNull))
        .and(STOCK_QUOTES.QUOTE_DATE.le(before))
        // Bound the LAG scan below the requested window: floor a few bars below loadAfter so the
        // trailing return is still defined at loadAfter, but Postgres needn't sort each symbol's
        // entire pre-window history. Output over [loadAfter, before] is unchanged.
        .and(STOCK_QUOTES.QUOTE_DATE.ge(loadAfter.minusDays(lookbackBars.toLong() * 3)))
        .asTable("per_name")

    val date = perName.field("d", LocalDate::class.java)!!
    val ret = perName.field("ret", BigDecimal::class.java)!!
    val meanReturn = DSL.avg(ret)
    val crossSectionalStdev = DSL.stddevSamp(ret)
    val contributingN = DSL.count(ret)

    return dsl
      .select(date, meanReturn, crossSectionalStdev, contributingN)
      .from(perName)
      .where(date.ge(loadAfter))
      .groupBy(date)
      .having(contributingN.gt(0))
      .orderBy(date)
      .fetch { record ->
        EwReturnDaily(
          quoteDate = record[date]!!,
          meanReturn = record[meanReturn]?.toDouble() ?: 0.0,
          crossSectionalStdev = record[crossSectionalStdev]?.toDouble() ?: 0.0,
          contributingN = record[contributingN] ?: 0,
        )
      }.associateBy { it.quoteDate }
  }
}
