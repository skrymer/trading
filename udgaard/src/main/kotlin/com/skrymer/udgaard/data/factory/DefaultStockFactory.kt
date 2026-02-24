package com.skrymer.udgaard.data.factory

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.service.TechnicalIndicatorService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Default implementation of StockFactory.
 * Creates Stock entities from AlphaVantage data with calculated technical indicators.
 *
 * Pipeline:
 * - AlphaVantage is the PRIMARY data source (OHLCV + volume)
 * - We calculate all technical indicators (EMAs, ATR, ADX, Donchian, trend)
 */
@Component
class DefaultStockFactory(
  private val technicalIndicatorService: TechnicalIndicatorService,
) : StockFactory {
  private val logger = LoggerFactory.getLogger(DefaultStockFactory::class.java)

  override fun enrichQuotes(
    symbol: String,
    stockQuotes: List<StockQuote>,
  ): List<StockQuote>? {
    if (stockQuotes.isEmpty()) {
      logger.warn("No AlphaVantage quotes provided for $symbol")
      return null
    }

    return technicalIndicatorService.enrichWithIndicators(stockQuotes, symbol)
  }

  override fun createStock(
    symbol: String,
    enrichedQuotes: List<StockQuote>,
    orderBlocks: List<OrderBlock>,
    earnings: List<Earning>,
  ): Stock =
    Stock(
      symbol = symbol,
      quotes = enrichedQuotes.toMutableList(),
      orderBlocks = orderBlocks.toMutableList(),
      earnings = earnings.toMutableList(),
    )
}
