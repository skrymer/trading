package com.skrymer.udgaard.repository.jooq

import com.skrymer.udgaard.domain.EtfDomain
import com.skrymer.udgaard.jooq.tables.pojos.EtfHoldings
import com.skrymer.udgaard.jooq.tables.pojos.EtfQuotes
import com.skrymer.udgaard.jooq.tables.pojos.Etfs
import com.skrymer.udgaard.jooq.tables.references.ETFS
import com.skrymer.udgaard.jooq.tables.references.ETF_HOLDINGS
import com.skrymer.udgaard.jooq.tables.references.ETF_QUOTES
import com.skrymer.udgaard.mapper.EtfMapper
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/**
 * jOOQ-based repository for ETF operations
 * Replaces the Hibernate EtfRepository
 */
@Repository
class EtfJooqRepository(
  private val dsl: DSLContext,
  private val mapper: EtfMapper,
) {
  /**
   * Find ETF by symbol with all related data
   */
  fun findBySymbol(symbol: String): EtfDomain? {
    // Load ETF
    val etf =
      dsl
        .selectFrom(ETFS)
        .where(ETFS.SYMBOL.eq(symbol))
        .fetchOneInto(Etfs::class.java) ?: return null

    // Load quotes
    val quotes =
      dsl
        .selectFrom(ETF_QUOTES)
        .where(ETF_QUOTES.ETF_SYMBOL.eq(symbol))
        .orderBy(ETF_QUOTES.QUOTE_DATE.asc())
        .fetchInto(EtfQuotes::class.java)

    // Load holdings
    val holdings =
      dsl
        .selectFrom(ETF_HOLDINGS)
        .where(ETF_HOLDINGS.ETF_SYMBOL.eq(symbol))
        .fetchInto(EtfHoldings::class.java)

    return mapper.toDomain(etf, quotes, holdings)
  }

  /**
   * Find all ETF symbols
   */
  fun findAllSymbols(): List<String> =
    dsl
      .select(ETFS.SYMBOL)
      .from(ETFS)
      .fetch(ETFS.SYMBOL)
      .filterNotNull()

  /**
   * Find all ETFs
   */
  fun findAll(): List<EtfDomain> {
    val symbols = findAllSymbols()
    return symbols.mapNotNull { findBySymbol(it) }
  }

  /**
   * Save ETF with all related data
   * Performs an upsert (insert or update)
   */
  fun save(etf: EtfDomain): EtfDomain = dsl.transactionResult { configuration ->
    val ctx = configuration.dsl()

    // 1. Upsert ETF
    val etfPojo = mapper.toPojo(etf)
    ctx
      .insertInto(ETFS)
      .set(ETFS.SYMBOL, etfPojo.symbol)
      .set(ETFS.NAME, etfPojo.name)
      .set(ETFS.DESCRIPTION, etfPojo.description)
      .set(ETFS.EXPENSE_RATIO, etfPojo.expenseRatio)
      .set(ETFS.AUM, etfPojo.aum)
      .set(ETFS.INCEPTION_DATE, etfPojo.inceptionDate)
      .set(ETFS.ISSUER, etfPojo.issuer)
      .set(ETFS.EXCHANGE, etfPojo.exchange)
      .set(ETFS.CURRENCY, etfPojo.currency)
      .set(ETFS.TYPE, etfPojo.type)
      .set(ETFS.BENCHMARK, etfPojo.benchmark)
      .set(ETFS.LAST_REBALANCE_DATE, etfPojo.lastRebalanceDate)
      .onDuplicateKeyUpdate()
      .set(ETFS.NAME, etfPojo.name)
      .set(ETFS.DESCRIPTION, etfPojo.description)
      .set(ETFS.EXPENSE_RATIO, etfPojo.expenseRatio)
      .set(ETFS.AUM, etfPojo.aum)
      .set(ETFS.INCEPTION_DATE, etfPojo.inceptionDate)
      .set(ETFS.ISSUER, etfPojo.issuer)
      .set(ETFS.EXCHANGE, etfPojo.exchange)
      .set(ETFS.CURRENCY, etfPojo.currency)
      .set(ETFS.TYPE, etfPojo.type)
      .set(ETFS.BENCHMARK, etfPojo.benchmark)
      .set(ETFS.LAST_REBALANCE_DATE, etfPojo.lastRebalanceDate)
      .execute()

    // 2. Delete existing quotes and holdings
    ctx.deleteFrom(ETF_QUOTES).where(ETF_QUOTES.ETF_SYMBOL.eq(etf.symbol)).execute()
    ctx.deleteFrom(ETF_HOLDINGS).where(ETF_HOLDINGS.ETF_SYMBOL.eq(etf.symbol)).execute()

    // 3. Insert quotes
    if (etf.quotes.isNotEmpty()) {
      val quoteBatch = ctx.batch(
        etf.quotes.map { quote ->
          val pojo = mapper.toPojo(quote)
          ctx
            .insertInto(ETF_QUOTES)
            .set(ETF_QUOTES.ETF_SYMBOL, etf.symbol)
            .set(ETF_QUOTES.QUOTE_DATE, pojo.quoteDate)
            .set(ETF_QUOTES.OPEN_PRICE, pojo.openPrice)
            .set(ETF_QUOTES.CLOSE_PRICE, pojo.closePrice)
            .set(ETF_QUOTES.HIGH_PRICE, pojo.highPrice)
            .set(ETF_QUOTES.LOW_PRICE, pojo.lowPrice)
            .set(ETF_QUOTES.VOLUME, pojo.volume)
            .set(ETF_QUOTES.CLOSE_PRICE_EMA5, pojo.closePriceEma5)
            .set(ETF_QUOTES.CLOSE_PRICE_EMA10, pojo.closePriceEma10)
            .set(ETF_QUOTES.CLOSE_PRICE_EMA20, pojo.closePriceEma20)
            .set(ETF_QUOTES.CLOSE_PRICE_EMA50, pojo.closePriceEma50)
            .set(ETF_QUOTES.ATR, pojo.atr)
            .set(ETF_QUOTES.BULLISH_PERCENTAGE, pojo.bullishPercentage)
            .set(ETF_QUOTES.STOCKS_IN_UPTREND, pojo.stocksInUptrend)
            .set(ETF_QUOTES.STOCKS_IN_DOWNTREND, pojo.stocksInDowntrend)
            .set(ETF_QUOTES.STOCKS_IN_NEUTRAL, pojo.stocksInNeutral)
            .set(ETF_QUOTES.TOTAL_HOLDINGS, pojo.totalHoldings)
            .set(ETF_QUOTES.LAST_BUY_SIGNAL, pojo.lastBuySignal)
            .set(ETF_QUOTES.LAST_SELL_SIGNAL, pojo.lastSellSignal)
        },
      )
      quoteBatch.execute()
    }

    // 4. Insert holdings
    if (etf.holdings.isNotEmpty()) {
      val holdingBatch = ctx.batch(
        etf.holdings.map { holding ->
          val pojo = mapper.toPojo(holding)
          ctx
            .insertInto(ETF_HOLDINGS)
            .set(ETF_HOLDINGS.ETF_SYMBOL, etf.symbol)
            .set(ETF_HOLDINGS.STOCK_SYMBOL, pojo.stockSymbol)
            .set(ETF_HOLDINGS.WEIGHT, pojo.weight)
            .set(ETF_HOLDINGS.SHARES, pojo.shares)
            .set(ETF_HOLDINGS.MARKET_VALUE, pojo.marketValue)
            .set(ETF_HOLDINGS.AS_OF_DATE, pojo.asOfDate)
            .set(ETF_HOLDINGS.IN_UPTREND, pojo.inUptrend)
            .set(ETF_HOLDINGS.TREND, pojo.trend)
        },
      )
      holdingBatch.execute()
    }

    etf
  }

  /**
   * Delete ETF by symbol
   */
  fun delete(symbol: String) {
    dsl.transaction { configuration ->
      val ctx = configuration.dsl()
      ctx.deleteFrom(ETF_QUOTES).where(ETF_QUOTES.ETF_SYMBOL.eq(symbol)).execute()
      ctx.deleteFrom(ETF_HOLDINGS).where(ETF_HOLDINGS.ETF_SYMBOL.eq(symbol)).execute()
      ctx.deleteFrom(ETFS).where(ETFS.SYMBOL.eq(symbol)).execute()
    }
  }

  /**
   * Check if ETF exists
   */
  fun exists(symbol: String): Boolean =
    dsl
      .selectCount()
      .from(ETFS)
      .where(ETFS.SYMBOL.eq(symbol))
      .fetchOne(0, Int::class.java) ?: 0 > 0

  /**
   * Count total number of ETFs
   */
  fun count(): Long =
    dsl
      .selectCount()
      .from(ETFS)
      .fetchOne(0, Long::class.java) ?: 0L
}
