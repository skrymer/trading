package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.SymbolRecord
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.springframework.stereotype.Service

/**
 * The trading universe — every symbol Udgaard holds data for. Sourced from the ingested
 * `stocks` table (ADR 0011): Udgaard keeps no separate symbol catalogue, so a symbol exists
 * here only once it has been ingested from Midgaard.
 */
@Service
class SymbolService(
  private val stockRepository: StockJooqRepository,
) {
  fun getAll(): List<SymbolRecord> = stockRepository.findAllSymbolRecords()

  fun search(query: String, limit: Int = 20): List<String> {
    if (query.length < 2) return emptyList()
    val upperQuery = query.uppercase()
    return getAll()
      .map { it.symbol }
      .filter { it.startsWith(upperQuery) }
      .take(limit)
  }
}
