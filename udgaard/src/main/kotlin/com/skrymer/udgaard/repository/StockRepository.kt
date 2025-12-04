package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Stock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface StockRepository : JpaRepository<Stock, String> {

    @Query("SELECT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol = :symbol")
    fun findBySymbolWithQuotes(symbol: String): Stock?

    @Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol IN :symbols")
    fun findAllBySymbolIn(symbols: List<String>): List<Stock>

    // Statistics queries for performance
    @Query("SELECT COUNT(q) FROM Stock s JOIN s.quotes q")
    fun countAllQuotes(): Long

    @Query("SELECT COUNT(e) FROM Stock s JOIN s.earnings e")
    fun countAllEarnings(): Long

    @Query("SELECT COUNT(ob) FROM Stock s JOIN s.orderBlocks ob")
    fun countAllOrderBlocks(): Long

    @Query("SELECT MIN(q.date) FROM Stock s JOIN s.quotes q WHERE q.date IS NOT NULL")
    fun findEarliestQuoteDate(): LocalDate?

    @Query("SELECT MAX(q.date) FROM Stock s JOIN s.quotes q WHERE q.date IS NOT NULL")
    fun findLatestQuoteDate(): LocalDate?

    @Query("SELECT COUNT(DISTINCT s) FROM Stock s JOIN s.earnings e")
    fun countStocksWithEarnings(): Long

    @Query("SELECT COUNT(DISTINCT s) FROM Stock s JOIN s.orderBlocks ob")
    fun countStocksWithOrderBlocks(): Long

    @Query("""
        SELECT s.symbol, MAX(q.date), COUNT(q),
               CASE WHEN COUNT(e) > 0 THEN true ELSE false END,
               COUNT(ob)
        FROM Stock s
        LEFT JOIN s.quotes q
        LEFT JOIN s.earnings e
        LEFT JOIN s.orderBlocks ob
        WHERE s.symbol IS NOT NULL
        GROUP BY s.symbol
        HAVING MAX(q.date) IS NOT NULL
        ORDER BY MAX(q.date) DESC
    """)
    fun findRecentlyUpdatedStocks(): List<Array<Any>>
}
