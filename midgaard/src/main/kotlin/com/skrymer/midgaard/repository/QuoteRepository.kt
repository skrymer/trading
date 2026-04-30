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

    private fun upsertSingleQuote(
        dsl: DSLContext,
        quote: Quote,
    ) {
        dsl
            .insertInto(QUOTES)
            .set(QUOTES.SYMBOL, quote.symbol)
            .set(QUOTES.QUOTE_DATE, quote.date)
            .set(QUOTES.OPEN_PRICE, quote.open)
            .set(QUOTES.HIGH_PRICE, quote.high)
            .set(QUOTES.LOW_PRICE, quote.low)
            .set(QUOTES.CLOSE_PRICE, quote.close)
            .set(QUOTES.VOLUME, quote.volume)
            .set(QUOTES.ATR, quote.atr)
            .set(QUOTES.ADX, quote.adx)
            .set(QUOTES.EMA_5, quote.ema5)
            .set(QUOTES.EMA_10, quote.ema10)
            .set(QUOTES.EMA_20, quote.ema20)
            .set(QUOTES.EMA_50, quote.ema50)
            .set(QUOTES.EMA_100, quote.ema100)
            .set(QUOTES.EMA_200, quote.ema200)
            .set(QUOTES.DONCHIAN_UPPER_5, quote.donchianUpper5)
            .set(QUOTES.INDICATOR_SOURCE, quote.indicatorSource.name)
            .onConflict(QUOTES.SYMBOL, QUOTES.QUOTE_DATE)
            .doUpdate()
            .set(QUOTES.OPEN_PRICE, quote.open)
            .set(QUOTES.HIGH_PRICE, quote.high)
            .set(QUOTES.LOW_PRICE, quote.low)
            .set(QUOTES.CLOSE_PRICE, quote.close)
            .set(QUOTES.VOLUME, quote.volume)
            .set(QUOTES.ATR, quote.atr)
            .set(QUOTES.ADX, quote.adx)
            .set(QUOTES.EMA_5, quote.ema5)
            .set(QUOTES.EMA_10, quote.ema10)
            .set(QUOTES.EMA_20, quote.ema20)
            .set(QUOTES.EMA_50, quote.ema50)
            .set(QUOTES.EMA_100, quote.ema100)
            .set(QUOTES.EMA_200, quote.ema200)
            .set(QUOTES.DONCHIAN_UPPER_5, quote.donchianUpper5)
            .set(QUOTES.INDICATOR_SOURCE, quote.indicatorSource.name)
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
            indicatorSource =
                indicatorSource?.let { IndicatorSource.valueOf(it) }
                    ?: IndicatorSource.CALCULATED,
        )
}
