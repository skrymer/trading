package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Stock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface StockRepository : JpaRepository<Stock, String> {
    fun findBySymbol(symbol: String): Stock?
    fun findBySymbolIn(symbols: List<String>): List<Stock>

    @Query("SELECT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol = :symbol")
    fun findBySymbolWithQuotes(symbol: String): Stock?

    @Query("SELECT s FROM Stock s LEFT JOIN FETCH s.quotes LEFT JOIN FETCH s.orderBlocks WHERE s.symbol = :symbol")
    fun findBySymbolWithQuotesAndOrderBlocks(symbol: String): Stock?
}
