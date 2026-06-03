package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.records.QuotesRecord
import com.skrymer.midgaard.jooq.tables.references.QUOTES
import com.skrymer.midgaard.model.IndicatorSource
import com.skrymer.midgaard.model.Quote
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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
    }
}
