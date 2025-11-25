package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.EtfEntity
import org.springframework.data.mongodb.repository.MongoRepository

/**
 * Repository for ETF entities.
 * Provides CRUD operations and custom queries for ETF data.
 */
interface EtfRepository : MongoRepository<EtfEntity, String> {
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
}
