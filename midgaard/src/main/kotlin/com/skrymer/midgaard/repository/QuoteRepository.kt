package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.records.QuotesRecord
import com.skrymer.midgaard.jooq.tables.references.QUOTES
import com.skrymer.midgaard.model.IndicatorSource
import com.skrymer.midgaard.model.Quote
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Freshness of a cross-sectional percentile (relative strength / quality) relative to the ingested
 * quotes — drives the ingestion-page status. [latestPopulated] is the most-recent date the percentile
 * is computed for; [latestQuote] the most-recent ingested bar. Stale means a re-ingest moved the quotes
 * ahead of the last recompute. A max-vs-max probe: `current` means the latest dates are aligned, not that
 * every interior bar is populated (dates failing the min-peer gate stay null and are not detected here).
 */
data class CrossSectionalStatus(
    val populatedRows: Int,
    val latestPopulated: LocalDate?,
    val latestQuote: LocalDate?,
) {
    val missing: Boolean get() = populatedRows == 0
    val stale: Boolean get() = !missing && latestPopulated != null && latestQuote != null && latestPopulated.isBefore(latestQuote)
    val current: Boolean get() = !missing && !stale
}

// A data-access class: the function count tracks the number of distinct quote queries (load, upsert,
// the cross-sectional recompute passes, freshness status), not class complexity.
@Suppress("TooManyFunctions")
@Repository
class QuoteRepository(
    private val dsl: DSLContext,
) {
    fun findBySymbol(
        symbol: String,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): List<Quote> {
        var condition = QUOTES.SYMBOL.eq(symbol)
        if (startDate != null) condition = condition.and(QUOTES.QUOTE_DATE.ge(startDate))
        if (endDate != null) condition = condition.and(QUOTES.QUOTE_DATE.le(endDate))
        return dsl
            .selectFrom(QUOTES)
            .where(condition)
            .orderBy(QUOTES.QUOTE_DATE.asc())
            .fetch()
            .map { it.toQuote() }
    }

    fun findBySymbols(
        symbols: List<String>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): Map<String, List<Quote>> {
        var condition = QUOTES.SYMBOL.`in`(symbols)
        if (startDate != null) condition = condition.and(QUOTES.QUOTE_DATE.ge(startDate))
        if (endDate != null) condition = condition.and(QUOTES.QUOTE_DATE.le(endDate))
        return dsl
            .selectFrom(QUOTES)
            .where(condition)
            .orderBy(QUOTES.SYMBOL.asc(), QUOTES.QUOTE_DATE.asc())
            .fetch()
            .map { it.toQuote() }
            .groupBy { it.symbol }
    }

    fun getLastNQuotes(
        symbol: String,
        n: Int,
    ): List<Quote> =
        dsl
            .selectFrom(QUOTES)
            .where(QUOTES.SYMBOL.eq(symbol))
            .orderBy(QUOTES.QUOTE_DATE.desc())
            .limit(n)
            .fetch()
            .map { it.toQuote() }
            .reversed()

    @Transactional
    fun upsertQuotes(quotes: List<Quote>) {
        if (quotes.isEmpty()) return
        dsl.batched { ctx ->
            for (quote in quotes) {
                upsertSingleQuote(ctx.dsl(), quote)
            }
        }
    }

    fun countBySymbol(symbol: String): Int = dsl.fetchCount(QUOTES, QUOTES.SYMBOL.eq(symbol))

    fun getLastBarDate(symbol: String): LocalDate? =
        dsl
            .select(DSL.max(QUOTES.QUOTE_DATE))
            .from(QUOTES)
            .where(QUOTES.SYMBOL.eq(symbol))
            .fetchOne()
            ?.value1()

    fun deleteBySymbol(symbol: String) {
        dsl
            .deleteFrom(QUOTES)
            .where(QUOTES.SYMBOL.eq(symbol))
            .execute()
    }

    // Atomic delete-then-insert. initialIngest uses this so re-ingest cleans out
    // rows that wouldn't be re-emitted under the current ingestion filters
    // (e.g. market-holiday bars that were stored before the holiday filter shipped).
    // Daily updates keep upsertQuotes semantics — they only fetch new bars and
    // must not delete history.
    @Transactional
    fun replaceForSymbol(
        symbol: String,
        quotes: List<Quote>,
    ) {
        require(quotes.all { it.symbol == symbol }) { "All quotes must belong to $symbol" }
        deleteBySymbol(symbol)
        upsertQuotes(quotes)
    }

    fun getTotalQuoteCount(): Int = dsl.fetchCount(QUOTES)

    /** Freshness of the relative-strength percentile vs the ingested quotes (drives the ingestion-page status). */
    fun relativeStrengthStatus(): CrossSectionalStatus = crossSectionalStatus(QUOTES.RELATIVE_STRENGTH_PERCENTILE)

    /** Freshness of the quality percentile vs the ingested quotes (drives the ingestion-page status). */
    fun qualityPercentileStatus(): CrossSectionalStatus = crossSectionalStatus(QUOTES.QUALITY_PERCENTILE)

    private fun crossSectionalStatus(field: Field<BigDecimal?>): CrossSectionalStatus {
        val record =
            dsl
                .select(
                    DSL.count(field),
                    DSL.max(QUOTES.QUOTE_DATE).filterWhere(field.isNotNull),
                    DSL.max(QUOTES.QUOTE_DATE),
                ).from(QUOTES)
                .fetchOne()
        return CrossSectionalStatus(
            populatedRows = record?.value1() ?: 0,
            latestPopulated = record?.value2(),
            latestQuote = record?.value3(),
        )
    }

    /**
     * Recomputes the market-relative strength percentile for every quote on or after [fromDate],
     * as a single cross-sectional SQL pass (ADR 0009) — the universe-wide sort, ranking and write
     * all happen in Postgres, so there is no app-side memory footprint or per-row update storm.
     *
     * Metric = `close / LAG(close, lookbackBars) − 1` (null, hence excluded, when there are fewer
     * than [lookbackBars] prior bars or the base close is ≤ 0). Percentile is the midpoint plotting
     * position `100·((rank−1) + ½·ties)/n` per date — `rank()` shares the minimum rank across ties,
     * so `rank−1` is the count strictly below and `ties` the run length. A date is ranked only when
     * its qualifying-peer count `n ≥ minPeers` and it is on/after [earliestDate].
     *
     * The cross-sectional sort spans the whole table and spills badly on the tiny default work_mem,
     * so it is raised transaction-locally first. Stale rows in range are nulled only where a value
     * actually exists, so after a fresh re-ingest (column already null) this is a no-op rather than
     * a full-table rewrite. History before [fromDate] still feeds each symbol's `LAG`.
     *
     * @return the number of (symbol, date) rows a percentile was written to.
     */
    @Transactional
    fun recomputeRelativeStrengthPercentiles(
        fromDate: LocalDate,
        lookbackBars: Int,
        minPeers: Int,
        earliestDate: LocalDate,
    ): Int {
        dsl.fetch("SELECT set_config('work_mem', ?, true)", RECOMPUTE_WORK_MEM)
        dsl.execute(
            "UPDATE quotes SET relative_strength_percentile = NULL " +
                "WHERE quote_date >= ? AND relative_strength_percentile IS NOT NULL",
            fromDate,
        )
        return dsl.execute(
            """
            WITH metric AS (
                SELECT symbol, quote_date,
                       close_price / NULLIF(LAG(close_price, ?) OVER (PARTITION BY symbol ORDER BY quote_date), 0) - 1 AS m
                FROM quotes
            ),
            ranked AS (
                SELECT symbol, quote_date,
                       rank() OVER (PARTITION BY quote_date ORDER BY m) AS rnk,
                       count(*) OVER (PARTITION BY quote_date) AS n,
                       count(*) OVER (PARTITION BY quote_date, m) AS ties
                FROM metric
                WHERE m IS NOT NULL
            )
            UPDATE quotes q
            SET relative_strength_percentile = 100.0 * ((r.rnk - 1) + 0.5 * r.ties) / r.n
            FROM ranked r
            WHERE q.symbol = r.symbol AND q.quote_date = r.quote_date
              AND r.n >= ? AND q.quote_date >= ? AND q.quote_date >= ?
            """.trimIndent(),
            lookbackBars,
            minPeers,
            earliestDate,
            fromDate,
        )
    }

    /**
     * Recomputes the gross-profitability quality percentile for every quote on or after [fromDate], as a
     * single cross-sectional SQL pass (ADR 0019 L2) — the as-of join, TTM sum, ranking and write all
     * happen in Postgres, so there is no app-side memory footprint (the same reason the metric lives here
     * and not in a backtest-context precompute).
     *
     * Metric `qualityRaw = grossProfit_TTM / totalAssets_asof` (CONTEXT *Gross-profitability quality
     * percentile*). For each symbol the `events` CTE walks its filings in `filing_date` order and, at each
     * filing, forms the trailing-twelve-month numerator (`SUM(gross_profit)` over the current + 3 prior
     * filings) and the point-in-time denominator (the current filing's own `total_assets`, never summed).
     * A filing is *defined* only when it has 4 prior filings (`filing_rank >= 4`), all 4 carry gross
     * profit (`ttm_count = 4`), and `total_assets > 0`; otherwise its `quality_raw` is null and the date
     * fails closed. Negative `grossProfit_TTM` is kept (ranks low). Each filing's quality holds from its
     * `filing_date` until the next filing's (forward-filled, gated on `filing_date` ≤ the trading date —
     * never `fiscal_date_ending`), joined to daily quotes by that half-open interval.
     *
     * Percentile is the midpoint plotting position `100·((rank−1) + ½·ties)/n` per date, ranked only when
     * the qualifying-peer count `n ≥ minPeers` and the date is on/after [earliestDate] (the survivorship
     * floor). Stale rows in range are nulled only where a value exists; work_mem is raised
     * transaction-locally for the universe-wide sort, exactly as the relative-strength pass does.
     *
     * @return the number of (symbol, date) rows a percentile was written to.
     */
    @Transactional
    fun recomputeQualityPercentiles(
        fromDate: LocalDate,
        minPeers: Int,
        earliestDate: LocalDate,
    ): Int {
        dsl.fetch("SELECT set_config('work_mem', ?, true)", RECOMPUTE_WORK_MEM)
        dsl.execute(
            "UPDATE quotes SET quality_percentile = NULL " +
                "WHERE quote_date >= ? AND quality_percentile IS NOT NULL",
            fromDate,
        )
        return dsl.execute(QUALITY_PERCENTILE_RECOMPUTE_SQL, minPeers, earliestDate, fromDate)
    }

    private fun upsertSingleQuote(
        dsl: DSLContext,
        quote: Quote,
    ) {
        // Same column→value bindings drive both the INSERT and the ON CONFLICT … DO UPDATE
        // branch, so they are built once into a record and reused for both.
        val record =
            dsl.newRecord(QUOTES).apply {
                symbol = quote.symbol
                quoteDate = quote.date
                openPrice = quote.open
                highPrice = quote.high
                lowPrice = quote.low
                closePrice = quote.close
                volume = quote.volume
                atr = quote.atr
                adx = quote.adx
                ema_5 = quote.ema5
                ema_10 = quote.ema10
                ema_20 = quote.ema20
                ema_50 = quote.ema50
                ema_100 = quote.ema100
                ema_200 = quote.ema200
                donchianUpper_5 = quote.donchianUpper5
                sma_50 = quote.sma50
                sma_150 = quote.sma150
                sma_200 = quote.sma200
                high_52Week = quote.high52Week
                low_52Week = quote.low52Week
                relativeStrengthPercentile = quote.relativeStrengthPercentile
                qualityPercentile = quote.qualityPercentile
                indicatorSource = quote.indicatorSource.name
            }
        dsl
            .insertInto(QUOTES)
            .set(record)
            .onConflict(QUOTES.SYMBOL, QUOTES.QUOTE_DATE)
            .doUpdate()
            .set(record)
            .execute()
    }

    private fun QuotesRecord.toQuote(): Quote =
        Quote(
            symbol = symbol,
            date = quoteDate,
            open = openPrice,
            high = highPrice,
            low = lowPrice,
            close = closePrice,
            volume = volume ?: 0L,
            atr = atr,
            adx = adx,
            ema5 = ema_5,
            ema10 = ema_10,
            ema20 = ema_20,
            ema50 = ema_50,
            ema100 = ema_100,
            ema200 = ema_200,
            donchianUpper5 = donchianUpper_5,
            sma50 = sma_50,
            sma150 = sma_150,
            sma200 = sma_200,
            high52Week = high_52Week,
            low52Week = low_52Week,
            relativeStrengthPercentile = relativeStrengthPercentile,
            qualityPercentile = qualityPercentile,
            indicatorSource =
                indicatorSource?.let { IndicatorSource.valueOf(it) }
                    ?: IndicatorSource.CALCULATED,
        )

    companion object {
        /**
         * Transaction-local work_mem for the relative-strength recompute. The whole-universe sort
         * spills ~1.4 GB on the 4 MB default; this keeps it in memory. Applied via
         * `set_config('work_mem', ?, is_local=true)` rather than `SET LOCAL` precisely so the value
         * can be a bind parameter; it resets on commit and never affects other sessions. The real
         * ceiling is a multiple of this (per parallel worker × per sort/hash node), so it relies on
         * ample container headroom and the @Synchronized guarantee that no two recomputes overlap.
         */
        private const val RECOMPUTE_WORK_MEM = "1GB"

        /**
         * The quality-percentile cross-sectional pass (ADR 0019 L2). `events` forms, per filing, the TTM
         * gross-profit numerator (current + 3 prior filings by filing_date) and the point-in-time total-assets
         * denominator; `quality` keeps only defined filings (4 priors present, all 4 carry gross profit, total
         * assets > 0) and the half-open `[filing_date, next_filing_date)` interval each value holds over;
         * `metric` as-of-joins those intervals to daily quotes; `ranked` is the midpoint cross-section per date.
         * Params: minPeers, earliestDate, fromDate.
         */
        private val QUALITY_PERCENTILE_RECOMPUTE_SQL =
            """
            WITH events AS (
                SELECT symbol, filing_date, total_assets,
                       sum(gross_profit) OVER w AS ttm_gross_profit,
                       count(gross_profit) OVER w AS ttm_count,
                       row_number() OVER ord AS filing_rank,
                       lead(filing_date) OVER ord AS next_filing_date
                FROM fundamentals
                WHERE filing_date IS NOT NULL
                WINDOW
                    ord AS (PARTITION BY symbol ORDER BY filing_date, fiscal_date_ending),
                    w AS (PARTITION BY symbol ORDER BY filing_date, fiscal_date_ending
                          ROWS BETWEEN 3 PRECEDING AND CURRENT ROW)
            ),
            quality AS (
                SELECT symbol, filing_date AS effective_from, next_filing_date AS effective_to,
                       ttm_gross_profit / total_assets AS quality_raw
                FROM events
                WHERE filing_rank >= 4 AND ttm_count = 4 AND total_assets > 0
            ),
            metric AS (
                SELECT q.symbol, q.quote_date, ql.quality_raw AS m
                FROM quotes q
                JOIN quality ql
                  ON ql.symbol = q.symbol
                 AND q.quote_date >= ql.effective_from
                 AND (ql.effective_to IS NULL OR q.quote_date < ql.effective_to)
            ),
            ranked AS (
                SELECT symbol, quote_date,
                       rank() OVER (PARTITION BY quote_date ORDER BY m) AS rnk,
                       count(*) OVER (PARTITION BY quote_date) AS n,
                       count(*) OVER (PARTITION BY quote_date, m) AS ties
                FROM metric
            )
            UPDATE quotes q
            SET quality_percentile = 100.0 * ((r.rnk - 1) + 0.5 * r.ties) / r.n
            FROM ranked r
            WHERE q.symbol = r.symbol AND q.quote_date = r.quote_date
              AND r.n >= ? AND q.quote_date >= ? AND q.quote_date >= ?
            """.trimIndent()
    }
}
