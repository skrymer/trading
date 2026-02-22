package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.SimpleStockInfo
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
open class StockService(
  val stockRepository: StockJooqRepository,
) {
  /**
   * Loads a stock from the database.
   * @param symbol the stock symbol to look up
   * @return the stock if found, null otherwise
   */
  @Transactional(readOnly = true)
  open fun getStock(symbol: String): Stock? {
    logger.info("Getting stock $symbol from database")

    val stock = stockRepository.findBySymbol(symbol)

    // Enrich order blocks with trading days age
    stock?.let { enrichOrderBlocksWithAge(it) }

    return stock
  }

  /**
   * Enriches order blocks with trading days age based on the stock's quote history
   */
  private fun enrichOrderBlocksWithAge(stock: Stock) {
    val lastQuote = stock.quotes.maxByOrNull { it.date }
    if (lastQuote == null) {
      logger.warn("No quotes available for ${stock.symbol}, cannot calculate order block age")
      return
    }

    stock.orderBlocks.forEach { orderBlock ->
      val endDate = orderBlock.endDate ?: lastQuote.date
      val ageInTradingDays = stock.countTradingDaysBetween(orderBlock.startDate, endDate)
      orderBlock.ageInTradingDays = ageInTradingDays
    }
  }

  /**
   * @param quotesAfter if set, only load quotes on or after this date
   * @return all stocks currently stored in DB
   */
  @Transactional(readOnly = true)
  open fun getAllStocks(quotesAfter: LocalDate? = null) = stockRepository.findAll(quotesAfter)

  /**
   * Get simple info for all stocks in the database.
   * Returns lightweight stock information without loading full quotes/order blocks.
   *
   * @return list of SimpleStockInfo with symbol, sector, quote count, and last quote date
   */
  @Transactional(readOnly = true)
  open fun getAllStocksSimple(): List<SimpleStockInfo> {
    logger.info("Getting simple info for all stocks")
    return stockRepository.findAllSimpleInfo()
  }

  /**
   * Get stocks by a list of symbols (efficient repository query).
   * @param symbols list of stock symbols to fetch
   * @param quotesAfter if set, only load quotes on or after this date
   * @return list of stocks matching the provided symbols (only those that exist in DB)
   */
  @Transactional(readOnly = true)
  open fun getStocksBySymbols(
    symbols: List<String>,
    quotesAfter: LocalDate? = null,
  ): List<Stock> {
    val sortedSymbols = symbols.sorted()
    return stockRepository.findBySymbols(sortedSymbols, quotesAfter)
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(StockService::class.java)
  }
}
