package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Breadth
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

/**
 * JPA repository for breadth data (market and sector breadth).
 */
interface BreadthRepository : JpaRepository<Breadth, Long> {
  @Query("SELECT b FROM Breadth b LEFT JOIN FETCH b.quotes WHERE b.symbolValue = :symbolValue")
  fun findBySymbol(symbolValue: String): Breadth?

  // Statistics queries for performance
  @Query("SELECT COUNT(q) FROM Breadth b JOIN b.quotes q")
  fun countAllBreadthQuotes(): Long

  @Query("SELECT MIN(q.quoteDate) FROM Breadth b JOIN b.quotes q WHERE q.quoteDate IS NOT NULL")
  fun findEarliestBreadthQuoteDate(): LocalDate?

  @Query("SELECT MAX(q.quoteDate) FROM Breadth b JOIN b.quotes q WHERE q.quoteDate IS NOT NULL")
  fun findLatestBreadthQuoteDate(): LocalDate?

  @Query(
    """
        SELECT b.symbolValue, COUNT(q), MAX(q.quoteDate)
        FROM Breadth b
        LEFT JOIN b.quotes q
        WHERE b.symbolValue IS NOT NULL
        GROUP BY b.symbolValue
    """,
  )
  fun findBreadthSymbolStats(): List<Array<Any>>
}
