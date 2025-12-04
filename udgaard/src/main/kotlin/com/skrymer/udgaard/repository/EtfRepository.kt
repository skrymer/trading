package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.EtfEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

/**
 * Repository for ETF entities.
 * Provides CRUD operations and custom queries for ETF data.
 */
interface EtfRepository : JpaRepository<EtfEntity, String> {
    /**
     * Find an ETF by its symbol.
     * @param symbol The ETF symbol (e.g., "SPY", "QQQ")
     * @return The ETF entity or null if not found
     */
    fun findBySymbol(symbol: String): EtfEntity?

    /**
     * Find multiple ETFs by their symbols.
     * @param symbols List of ETF symbols
     * @return List of matching ETF entities
     */
    fun findBySymbolIn(symbols: List<String>): List<EtfEntity>

    /**
     * Check if an ETF exists by symbol.
     * @param symbol The ETF symbol
     * @return true if exists, false otherwise
     */
    fun existsBySymbol(symbol: String): Boolean

    @Query("SELECT e FROM EtfEntity e LEFT JOIN FETCH e.quotes WHERE e.symbol = :symbol")
    fun findBySymbolWithQuotes(symbol: String): EtfEntity?

    @Query("SELECT e FROM EtfEntity e LEFT JOIN FETCH e.quotes LEFT JOIN FETCH e.holdings WHERE e.symbol = :symbol")
    fun findBySymbolWithQuotesAndHoldings(symbol: String): EtfEntity?

    // Statistics queries for performance
    @Query("SELECT COUNT(q) FROM EtfEntity e JOIN e.quotes q")
    fun countAllEtfQuotes(): Long

    @Query("SELECT COUNT(h) FROM EtfEntity e JOIN e.holdings h")
    fun countAllHoldings(): Long

    @Query("SELECT MIN(q.date) FROM EtfEntity e JOIN e.quotes q WHERE q.date IS NOT NULL")
    fun findEarliestEtfQuoteDate(): LocalDate?

    @Query("SELECT MAX(q.date) FROM EtfEntity e JOIN e.quotes q WHERE q.date IS NOT NULL")
    fun findLatestEtfQuoteDate(): LocalDate?

    @Query("SELECT COUNT(DISTINCT e) FROM EtfEntity e JOIN e.holdings h")
    fun countEtfsWithHoldings(): Long
}
