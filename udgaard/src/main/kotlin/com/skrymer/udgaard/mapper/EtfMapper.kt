package com.skrymer.udgaard.mapper

import com.skrymer.udgaard.domain.EtfDomain
import com.skrymer.udgaard.domain.EtfHoldingDomain
import com.skrymer.udgaard.domain.EtfMetadataDomain
import com.skrymer.udgaard.domain.EtfQuoteDomain
import com.skrymer.udgaard.jooq.tables.pojos.EtfHoldings
import com.skrymer.udgaard.jooq.tables.pojos.EtfQuotes
import com.skrymer.udgaard.jooq.tables.pojos.Etfs
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ ETF POJOs and domain models
 */
@Component
class EtfMapper {
  /**
   * Convert jOOQ ETF POJO to domain model
   */
  fun toDomain(
    etf: Etfs,
    quotes: List<EtfQuotes>,
    holdings: List<EtfHoldings>,
  ): EtfDomain =
    EtfDomain(
      symbol = etf.symbol ?: "",
      name = etf.name ?: "",
      description = etf.description ?: "",
      quotes = quotes.map { toDomain(it) },
      holdings = holdings.map { toDomain(it) },
      metadata =
        if (etf.expenseRatio != null || etf.aum != null) {
          EtfMetadataDomain(
            expenseRatio = etf.expenseRatio,
            aum = etf.aum,
            inceptionDate = etf.inceptionDate,
            issuer = etf.issuer,
            exchange = etf.exchange,
            currency = etf.currency ?: "USD",
            type = etf.type,
            benchmark = etf.benchmark,
            lastRebalanceDate = etf.lastRebalanceDate,
          )
        } else {
          null
        },
    )

  /**
   * Convert EtfQuote jOOQ POJO to domain model
   */
  fun toDomain(quote: EtfQuotes): EtfQuoteDomain =
    EtfQuoteDomain(
      date = quote.quoteDate ?: throw IllegalArgumentException("Quote date cannot be null"),
      openPrice = quote.openPrice ?: 0.0,
      closePrice = quote.closePrice ?: 0.0,
      high = quote.highPrice ?: 0.0,
      low = quote.lowPrice ?: 0.0,
      volume = quote.volume ?: 0,
      closePriceEMA5 = quote.closePriceEma5 ?: 0.0,
      closePriceEMA10 = quote.closePriceEma10 ?: 0.0,
      closePriceEMA20 = quote.closePriceEma20 ?: 0.0,
      closePriceEMA50 = quote.closePriceEma50 ?: 0.0,
      atr = quote.atr ?: 0.0,
      bullishPercentage = quote.bullishPercentage ?: 0.0,
      stocksInUptrend = quote.stocksInUptrend ?: 0,
      stocksInDowntrend = quote.stocksInDowntrend ?: 0,
      stocksInNeutral = quote.stocksInNeutral ?: 0,
      totalHoldings = quote.totalHoldings ?: 0,
      lastBuySignal = quote.lastBuySignal,
      lastSellSignal = quote.lastSellSignal,
    )

  /**
   * Convert EtfHolding jOOQ POJO to domain model
   */
  fun toDomain(holding: EtfHoldings): EtfHoldingDomain =
    EtfHoldingDomain(
      stockSymbol = holding.stockSymbol ?: "",
      weight = holding.weight ?: 0.0,
      shares = holding.shares,
      marketValue = holding.marketValue,
      asOfDate = holding.asOfDate ?: java.time.LocalDate.now(),
      inUptrend = holding.inUptrend ?: false,
      trend = holding.trend,
    )

  /**
   * Convert domain model to jOOQ ETF POJO
   */
  fun toPojo(etf: EtfDomain): Etfs =
    Etfs(
      symbol = etf.symbol,
      name = etf.name,
      description = etf.description,
      expenseRatio = etf.metadata?.expenseRatio,
      aum = etf.metadata?.aum,
      inceptionDate = etf.metadata?.inceptionDate,
      issuer = etf.metadata?.issuer,
      exchange = etf.metadata?.exchange,
      currency = etf.metadata?.currency,
      type = etf.metadata?.type,
      benchmark = etf.metadata?.benchmark,
      lastRebalanceDate = etf.metadata?.lastRebalanceDate,
    )

  /**
   * Convert domain model to jOOQ EtfQuote POJO
   */
  fun toPojo(quote: EtfQuoteDomain): EtfQuotes =
    EtfQuotes(
      id = null, // Let database generate ID
      etfSymbol = null, // Set by repository
      quoteDate = quote.date,
      openPrice = quote.openPrice,
      closePrice = quote.closePrice,
      highPrice = quote.high,
      lowPrice = quote.low,
      volume = quote.volume,
      closePriceEma5 = quote.closePriceEMA5,
      closePriceEma10 = quote.closePriceEMA10,
      closePriceEma20 = quote.closePriceEMA20,
      closePriceEma50 = quote.closePriceEMA50,
      atr = quote.atr,
      bullishPercentage = quote.bullishPercentage,
      stocksInUptrend = quote.stocksInUptrend,
      stocksInDowntrend = quote.stocksInDowntrend,
      stocksInNeutral = quote.stocksInNeutral,
      totalHoldings = quote.totalHoldings,
      lastBuySignal = quote.lastBuySignal,
      lastSellSignal = quote.lastSellSignal,
    )

  /**
   * Convert domain model to jOOQ EtfHolding POJO
   */
  fun toPojo(holding: EtfHoldingDomain): EtfHoldings =
    EtfHoldings(
      id = null, // Let database generate ID
      etfSymbol = null, // Set by repository
      stockSymbol = holding.stockSymbol,
      weight = holding.weight,
      shares = holding.shares,
      marketValue = holding.marketValue,
      asOfDate = holding.asOfDate,
      inUptrend = holding.inUptrend,
      trend = holding.trend,
    )
}
