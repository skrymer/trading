package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.AssetType
import com.skrymer.udgaard.repository.jooq.SymbolJooqRepository
import com.skrymer.udgaard.repository.jooq.SymbolRecord
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class SymbolService(
  private val repository: SymbolJooqRepository,
) {
  @Cacheable(value = ["symbols"], key = "'all'")
  fun getAll(): List<SymbolRecord> = repository.findAll()

  fun search(query: String, limit: Int = 20): List<String> {
    if (query.length < 2) return emptyList()
    val upperQuery = query.uppercase()
    return getAll()
      .map { it.symbol }
      .filter { it.startsWith(upperQuery) }
      .take(limit)
  }

  fun isNonStock(symbol: String): Boolean {
    val record = getAll().firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
    return record != null && record.assetType != AssetType.STOCK
  }
}
