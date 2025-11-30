package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Breadth
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * JPA repository for breadth data (market and sector breadth).
 */
interface BreadthRepository : JpaRepository<Breadth, Long> {
    @Query("SELECT b FROM Breadth b LEFT JOIN FETCH b.quotes WHERE b.symbolValue = :symbolValue")
    fun findBySymbol(symbolValue: String): Breadth?
}
