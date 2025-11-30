package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Stock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StockRepository : JpaRepository<Stock, String> {

    @Query("SELECT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol = :symbol")
    fun findBySymbolWithQuotes(symbol: String): Stock?

    @Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol IN :symbols")
    fun findAllBySymbolIn(symbols: List<String>): List<Stock>
}
