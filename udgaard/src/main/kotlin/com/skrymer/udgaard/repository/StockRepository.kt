package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Stock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StockRepository : JpaRepository<Stock, String> {
  @Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol IN :symbols")
  fun findAllBySymbolIn(symbols: List<String>): List<Stock>

  /**
   * Find all stocks with all collections eagerly fetched using multiple queries.
   * This prevents connection leaks by ensuring all data is loaded within the transaction.
   *
   * Note: We use three separate queries to avoid Hibernate's MultipleBagFetchException
   * which occurs when trying to JOIN FETCH multiple collections in one query.
   */
  @Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.quotes")
  fun findAllWithQuotes(): List<Stock>

  @Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.orderBlocks WHERE s IN :stocks")
  fun fetchOrderBlocks(stocks: List<Stock>): List<Stock>

  @Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.earnings WHERE s IN :stocks")
  fun fetchEarnings(stocks: List<Stock>): List<Stock>
}
