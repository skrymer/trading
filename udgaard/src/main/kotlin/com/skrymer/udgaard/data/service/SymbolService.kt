package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.SectorSymbol
import com.skrymer.udgaard.data.repository.SymbolJooqRepository
import com.skrymer.udgaard.data.repository.SymbolRecord
import org.springframework.cache.annotation.CacheEvict
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

  fun getSectorSymbol(symbol: String): SectorSymbol? {
    val record = getAll().firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
    return record?.sectorSymbol?.let { SectorSymbol.fromString(it) }
  }

  @CacheEvict(value = ["symbols"], key = "'all'")
  fun updateSectorSymbol(symbol: String, sectorSymbol: String) {
    repository.updateSectorSymbol(symbol, sectorSymbol)
  }

  fun findStocksWithoutSector(): List<SymbolRecord> = repository.findStocksWithoutSector()
}
